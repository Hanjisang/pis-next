package com.hanjisang.pis.integration.migration;

import static com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType.BLOCK;
import static com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType.CASE;
import static com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType.DIAGNOSIS;
import static com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType.REPORT_METADATA;
import static com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType.SLIDE;
import static com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType.SPECIMEN;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hanjisang.pis.integration.migration.MigrationIssue.Severity;
import com.hanjisang.pis.integration.migration.MigrationIssue.Status;
import com.hanjisang.pis.integration.migration.MigrationValidationReport.CountComparison;
import com.hanjisang.pis.integration.migration.MigrationValidationReport.RelationComparison;
import com.hanjisang.pis.integration.migration.legacy.LegacyFact;
import com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType;
import com.hanjisang.pis.integration.migration.legacy.MigrationSourceAdapter;
import com.hanjisang.pis.integration.migration.legacy.MigrationSourceAdapter.SourceManifest;

@Service
public class MigrationApplicationService {

    private static final Map<ObjectType, ObjectType> REQUIRED_PARENTS = Map.of(
            SPECIMEN, CASE,
            BLOCK, SPECIMEN,
            SLIDE, BLOCK,
            DIAGNOSIS, CASE,
            REPORT_METADATA, CASE);

    private final MigrationStagingStore stagingStore;

    public MigrationApplicationService(MigrationStagingStore stagingStore) {
        this.stagingStore = stagingStore;
    }

    public MigrationValidationReport execute(UUID requestedRunId, MigrationSourceAdapter source,
            String mappingRuleVersion, String startedByRef) {
        requireText(source.adapterCode(), "source adapter code");
        requireText(mappingRuleVersion, "mapping rule version");
        requireText(startedByRef, "started by reference");
        SourceManifest manifest = requireManifest(source.manifest());
        List<LegacyFact> facts = List.copyOf(source.readFacts());
        Instant now = Instant.now();
        UUID runId = stagingStore.beginOrResume(requestedRunId, source.adapterCode(), manifest,
                mappingRuleVersion.trim(), startedByRef.trim(), now);

        List<MigrationIssue> issues = new ArrayList<>();
        Set<FactKey> invalid = new HashSet<>();
        Map<FactKey, List<LegacyFact>> grouped = groupFacts(facts, issues, invalid, runId);
        Map<FactKey, LegacyFact> unique = uniqueFacts(grouped);

        if (manifest.recordCount() != facts.size()) {
            issues.add(issue(runId, "MANIFEST_COUNT_MISMATCH", Severity.P1, CASE, "__MANIFEST__",
                    "来源清单记录数与适配器实际读取数不一致",
                    "重新生成来源快照并核对清单后重跑", manifest.sourceReference()));
        }

        validateFacts(runId, unique, invalid, issues);
        cascadeInvalidParents(runId, unique, invalid, issues);

        List<LegacyFact> validFacts = unique.entrySet().stream()
                .filter(entry -> !invalid.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(LegacyFact::objectType).thenComparing(LegacyFact::legacyId))
                .toList();
        long stagedCount = 0;
        for (LegacyFact fact : validFacts) {
            UUID targetId = deterministicId(runId, "target", fact.objectType().name(), fact.legacyId());
            if (stagingStore.stage(runId, fact, targetType(fact.objectType()), targetId,
                    mappingDecision(fact.objectType()), evidenceSnapshot(fact), now)) {
                stagedCount++;
            }
        }

        for (MigrationIssue issue : issues) {
            stagingStore.addIssue(issue, now);
        }
        stagingStore.saveCheckpoint(runId, "M5-VALIDATION",
                facts.isEmpty() ? null : facts.getLast().objectType().name(),
                facts.isEmpty() ? null : facts.getLast().legacyId(), stagedCount, issues.size(), now);

        MigrationValidationReport report = report(runId, facts, validFacts, issues, now);
        stagingStore.complete(report, now);
        return report;
    }

    private static SourceManifest requireManifest(SourceManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("source manifest is required");
        requireText(manifest.sourceReference(), "source reference");
        requireText(manifest.datasetVersion(), "source dataset version");
        requireText(manifest.schemaHash(), "source schema hash");
        if (manifest.capturedAt() == null || manifest.recordCount() < 0) {
            throw new IllegalArgumentException("source manifest timestamp and non-negative count are required");
        }
        return manifest;
    }

    private static Map<FactKey, List<LegacyFact>> groupFacts(List<LegacyFact> facts, List<MigrationIssue> issues,
            Set<FactKey> invalid, UUID runId) {
        Map<FactKey, List<LegacyFact>> grouped = new LinkedHashMap<>();
        for (LegacyFact fact : facts) {
            if (fact == null || fact.objectType() == null || isBlank(fact.legacyId())) {
                throw new IllegalArgumentException("migration facts require object type and legacy id");
            }
            FactKey key = FactKey.of(fact);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fact);
        }
        grouped.forEach((key, values) -> {
            if (values.size() > 1) {
                invalid.add(key);
                issues.add(issue(runId, "DUPLICATE_SOURCE_FACT", Severity.P1, key.type(), key.id(),
                        "同一来源对象在快照中出现多次，无法可靠判定有效记录",
                        "清理来源重复记录或提供明确合并规则", sourceReference(key)));
            }
        });
        return grouped;
    }

    private static Map<FactKey, LegacyFact> uniqueFacts(Map<FactKey, List<LegacyFact>> grouped) {
        Map<FactKey, LegacyFact> unique = new HashMap<>();
        grouped.forEach((key, values) -> unique.put(key, values.getFirst()));
        return unique;
    }

    private static void validateFacts(UUID runId, Map<FactKey, LegacyFact> unique, Set<FactKey> invalid,
            List<MigrationIssue> issues) {
        unique.forEach((key, fact) -> {
            if (isBlank(fact.payloadDigest())) {
                invalid.add(key);
                issues.add(issue(runId, "MISSING_PAYLOAD_DIGEST", Severity.P1, key.type(), key.id(),
                        "来源事实缺少不可变摘要，无法证明迁移输入未被改变",
                        "补充来源事实摘要后重跑", sourceReference(key)));
            }
            if (fact.objectType() == REPORT_METADATA && isBlank(fact.payloadReference())) {
                invalid.add(key);
                issues.add(issue(runId, "INCOMPLETE_REPORT_REFERENCE", Severity.P1, key.type(), key.id(),
                        "历史报告缺少原始 PDF 或文件引用，禁止重新生成替代",
                        "定位历史文件并补录只读引用", sourceReference(key)));
            }
            ObjectType expectedParent = REQUIRED_PARENTS.get(fact.objectType());
            if (expectedParent != null && (fact.parentType() != expectedParent || isBlank(fact.parentLegacyId()))) {
                invalid.add(key);
                issues.add(issue(runId, "INVALID_PARENT_TYPE", Severity.P1, key.type(), key.id(),
                        "来源事实缺少必需的 " + expectedParent + " 父关系",
                        "核对来源关系并补充明确父对象", sourceReference(key)));
                return;
            }
            if (fact.parentType() != null) {
                FactKey parentKey = new FactKey(fact.parentType(), fact.parentLegacyId());
                if (isBlank(fact.parentLegacyId()) || !unique.containsKey(parentKey)) {
                    invalid.add(key);
                    issues.add(issue(runId, "ORPHAN_SOURCE", Severity.P1, key.type(), key.id(),
                            "来源父对象不存在，不能静默迁移孤立记录",
                            "恢复父对象或登记人工归属决定", sourceReference(key)));
                }
            }
        });
    }

    private static void cascadeInvalidParents(UUID runId, Map<FactKey, LegacyFact> unique, Set<FactKey> invalid,
            List<MigrationIssue> issues) {
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<FactKey, LegacyFact> entry : unique.entrySet()) {
                LegacyFact fact = entry.getValue();
                if (invalid.contains(entry.getKey()) || fact.parentType() == null || isBlank(fact.parentLegacyId())) {
                    continue;
                }
                FactKey parent = new FactKey(fact.parentType(), fact.parentLegacyId());
                if (invalid.contains(parent)) {
                    invalid.add(entry.getKey());
                    issues.add(issue(runId, "PARENT_NOT_STAGED", Severity.P1, fact.objectType(), fact.legacyId(),
                            "父对象存在迁移异常，当前记录不能先于父对象进入 V2",
                            "先处理父对象异常，再重跑当前记录", sourceReference(entry.getKey())));
                    changed = true;
                }
            }
        } while (changed);
    }

    private static MigrationValidationReport report(UUID runId, List<LegacyFact> sourceFacts,
            List<LegacyFact> stagedFacts, List<MigrationIssue> issues, Instant generatedAt) {
        Map<ObjectType, Long> sourceCounts = counts(sourceFacts);
        Map<ObjectType, Long> stagedCounts = counts(stagedFacts);
        CountComparison cases = count(sourceCounts, stagedCounts, CASE);
        CountComparison specimens = count(sourceCounts, stagedCounts, SPECIMEN);
        CountComparison blocks = count(sourceCounts, stagedCounts, BLOCK);
        CountComparison slides = count(sourceCounts, stagedCounts, SLIDE);
        CountComparison diagnoses = count(sourceCounts, stagedCounts, DIAGNOSIS);
        CountComparison reports = count(sourceCounts, stagedCounts, REPORT_METADATA);
        RelationComparison caseSpecimen = relation("CASE_SPECIMEN", specimens);
        RelationComparison specimenBlock = relation("SPECIMEN_BLOCK", blocks);
        RelationComparison blockSlide = relation("BLOCK_SLIDE", slides);
        RelationComparison caseReport = relation("CASE_REPORT", reports);
        boolean blockingIssue = issues.stream().anyMatch(issue -> issue.severity() != Severity.P2);
        boolean countMismatch = List.of(cases, specimens, blocks, slides, diagnoses, reports).stream()
                .anyMatch(comparison -> comparison.difference() != 0);
        boolean relationMismatch = List.of(caseSpecimen, specimenBlock, blockSlide, caseReport).stream()
                .anyMatch(comparison -> comparison.difference() != 0);
        MigrationValidationReport.Status status = blockingIssue || countMismatch || relationMismatch
                ? MigrationValidationReport.Status.BLOCKED
                : MigrationValidationReport.Status.VALIDATED;
        return new MigrationValidationReport(runId, status, cases, specimens, blocks, slides, diagnoses, reports,
                caseSpecimen, specimenBlock, blockSlide, caseReport, issues, generatedAt);
    }

    private static Map<ObjectType, Long> counts(List<LegacyFact> facts) {
        Map<ObjectType, Long> counts = new EnumMap<>(ObjectType.class);
        facts.forEach(fact -> counts.merge(fact.objectType(), 1L, Long::sum));
        return counts;
    }

    private static CountComparison count(Map<ObjectType, Long> source, Map<ObjectType, Long> staged,
            ObjectType type) {
        return new CountComparison(source.getOrDefault(type, 0L), staged.getOrDefault(type, 0L));
    }

    private static RelationComparison relation(String code, CountComparison count) {
        return new RelationComparison(code, count.sourceCount(), count.stagedCount());
    }

    private static MigrationIssue issue(UUID runId, String code, Severity severity, ObjectType type, String id,
            String reason, String manualAction, String evidenceReference) {
        return new MigrationIssue(deterministicId(runId, "issue", code, type.name(), id), runId, code, severity,
                type, id, reason, manualAction, Status.OPEN, evidenceReference);
    }

    private static UUID deterministicId(UUID runId, String... parts) {
        return UUID.nameUUIDFromBytes((runId + "|" + String.join("|", parts)).getBytes(StandardCharsets.UTF_8));
    }

    private static String targetType(ObjectType type) {
        return switch (type) {
            case PATIENT -> "EXTERNAL_PATIENT_REFERENCE";
            case REPORT_METADATA -> "HISTORICAL_REPORT_METADATA";
            default -> "V2_" + type.name();
        };
    }

    private static String mappingDecision(ObjectType type) {
        return type == ObjectType.PATIENT || type == REPORT_METADATA ? "KEEP_REFERENCE" : "MAP";
    }

    private static String evidenceSnapshot(LegacyFact fact) {
        return "sourceType=" + fact.objectType() + ";sourceId=" + fact.legacyId()
                + ";payloadDigest=" + fact.payloadDigest() + ";payloadReference="
                + (fact.payloadReference() == null ? "" : fact.payloadReference());
    }

    private static String sourceReference(FactKey key) {
        return "legacy://" + key.type().name().toLowerCase() + "/" + key.id();
    }

    private static void requireText(String value, String field) {
        if (isBlank(value)) throw new IllegalArgumentException(field + " is required");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FactKey(ObjectType type, String id) {
        private static FactKey of(LegacyFact fact) {
            return new FactKey(fact.objectType(), fact.legacyId());
        }
    }
}

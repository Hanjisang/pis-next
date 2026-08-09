package com.hanjisang.pis.integration.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.integration.migration.legacy.LegacyFact;
import com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType;
import com.hanjisang.pis.integration.migration.legacy.MigrationSourceAdapter;
import com.hanjisang.pis.integration.migration.legacy.MigrationSourceAdapter.SourceManifest;

class MigrationFrameworkTest {

    @Test
    void syntheticLegacyFactsStageWithCountsRelationsAndHistoricalPdfReferenceIntact() {
        List<LegacyFact> fixture = List.of(
                fact(ObjectType.PATIENT, "PAT-1", null, null, "PATIENT-REF", null),
                fact(ObjectType.CASE, "CASE-1", ObjectType.PATIENT, "PAT-1", "P-0001", null),
                fact(ObjectType.SPECIMEN, "SPEC-1", ObjectType.CASE, "CASE-1", "P-0001-A", null),
                fact(ObjectType.BLOCK, "BLOCK-1", ObjectType.SPECIMEN, "SPEC-1", "P-0001-A1", null),
                fact(ObjectType.SLIDE, "SLIDE-1", ObjectType.BLOCK, "BLOCK-1", "P-0001-A1-HE", null),
                fact(ObjectType.DIAGNOSIS, "DX-1", ObjectType.CASE, "CASE-1", "DX-R001", null),
                fact(ObjectType.REPORT_METADATA, "REPORT-1", ObjectType.CASE, "CASE-1", "R001",
                        "archive://synthetic/reports/R001.pdf"));
        InMemoryStagingStore store = new InMemoryStagingStore();
        MigrationApplicationService service = new MigrationApplicationService(store);

        MigrationValidationReport report = service.execute(UUID.randomUUID(), adapter(fixture),
                "RULES-1", "MIGRATION-TEST");

        assertThat(report.status()).isEqualTo(MigrationValidationReport.Status.VALIDATED);
        assertThat(report.caseCount()).isEqualTo(new MigrationValidationReport.CountComparison(1, 1));
        assertThat(report.specimenCount()).isEqualTo(new MigrationValidationReport.CountComparison(1, 1));
        assertThat(report.blockCount()).isEqualTo(new MigrationValidationReport.CountComparison(1, 1));
        assertThat(report.slideCount()).isEqualTo(new MigrationValidationReport.CountComparison(1, 1));
        assertThat(report.reportCount()).isEqualTo(new MigrationValidationReport.CountComparison(1, 1));
        assertThat(List.of(report.caseSpecimen(), report.specimenBlock(), report.blockSlide(), report.caseReport()))
                .allMatch(relation -> relation.difference() == 0);
        assertThat(report.exceptions()).isEmpty();
        assertThat(store.staged).hasSize(7);
        assertThat(store.staged.get("REPORT_METADATA|REPORT-1").fact().payloadReference())
                .isEqualTo("archive://synthetic/reports/R001.pdf");
        assertThat(store.staged.get("REPORT_METADATA|REPORT-1").mappingDecision()).isEqualTo("KEEP_REFERENCE");
        assertThat(store.completedReport).isSameAs(report);
    }

    @Test
    void duplicateOrOrphanFactsAndMissingPdfReferencesEnterExceptionListWithoutSilentStaging() {
        List<LegacyFact> fixture = List.of(
                fact(ObjectType.CASE, "CASE-1", null, null, "P-0001", null),
                fact(ObjectType.CASE, "CASE-1", null, null, "P-0001-DUP", null),
                fact(ObjectType.SPECIMEN, "SPEC-1", ObjectType.CASE, "CASE-1", "P-0001-A", null),
                fact(ObjectType.SLIDE, "SLIDE-ORPHAN", ObjectType.BLOCK, "BLOCK-MISSING", "ORPHAN-SLIDE", null),
                fact(ObjectType.REPORT_METADATA, "REPORT-1", ObjectType.CASE, "CASE-1", "R001", null));
        InMemoryStagingStore store = new InMemoryStagingStore();
        MigrationApplicationService service = new MigrationApplicationService(store);

        MigrationValidationReport report = service.execute(UUID.randomUUID(), adapter(fixture),
                "RULES-1", "MIGRATION-TEST");

        assertThat(report.status()).isEqualTo(MigrationValidationReport.Status.BLOCKED);
        assertThat(report.exceptions()).extracting(MigrationIssue::exceptionCode)
                .contains("DUPLICATE_SOURCE_FACT", "ORPHAN_SOURCE", "INCOMPLETE_REPORT_REFERENCE",
                        "PARENT_NOT_STAGED");
        assertThat(store.staged).doesNotContainKeys("CASE|CASE-1", "SPECIMEN|SPEC-1", "SLIDE|SLIDE-ORPHAN",
                "REPORT_METADATA|REPORT-1");
        assertThat(store.issues).hasSameSizeAs(report.exceptions());
        assertThat(report.caseCount().difference()).isEqualTo(2);
    }

    private static LegacyFact fact(ObjectType type, String id, ObjectType parentType, String parentId,
            String businessReference, String payloadReference) {
        return new LegacyFact(type, id, parentType, parentId, businessReference, payloadReference,
                "sha256:" + type + ":" + id + ":synthetic");
    }

    private static MigrationSourceAdapter adapter(List<LegacyFact> facts) {
        return new MigrationSourceAdapter() {
            @Override
            public String adapterCode() {
                return "SYNTHETIC_LEGACY_FIXTURE";
            }

            @Override
            public SourceManifest manifest() {
                return new SourceManifest("fixture://synthetic/site-migration", "SYNTH-1", "schema-sha256:test",
                        Instant.parse("2026-08-09T00:00:00Z"), facts.size());
            }

            @Override
            public List<LegacyFact> readFacts() {
                return facts;
            }
        };
    }

    private static final class InMemoryStagingStore implements MigrationStagingStore {
        private final Map<String, StagedRecord> staged = new LinkedHashMap<>();
        private final List<MigrationIssue> issues = new ArrayList<>();
        private MigrationValidationReport completedReport;

        @Override
        public UUID beginOrResume(UUID requestedRunId, String sourceAdapterCode, SourceManifest manifest,
                String mappingRuleVersion, String startedByRef, Instant now) {
            return requestedRunId == null ? UUID.randomUUID() : requestedRunId;
        }

        @Override
        public boolean stage(UUID runId, LegacyFact fact, String targetObjectType, UUID targetObjectId,
                String mappingDecisionCode, String evidenceSnapshot, Instant now) {
            staged.put(fact.objectType() + "|" + fact.legacyId(),
                    new StagedRecord(fact, targetObjectType, targetObjectId, mappingDecisionCode, evidenceSnapshot));
            return true;
        }

        @Override
        public void addIssue(MigrationIssue issue, Instant now) {
            if (issues.stream().noneMatch(existing -> existing.id().equals(issue.id()))) issues.add(issue);
        }

        @Override
        public void saveCheckpoint(UUID runId, String checkpointCode, String lastObjectType, String lastObjectId,
                long stagedCount, long exceptionCount, Instant now) { }

        @Override
        public void complete(MigrationValidationReport report, Instant now) {
            completedReport = report;
        }
    }

    private record StagedRecord(LegacyFact fact, String targetObjectType, UUID targetObjectId,
            String mappingDecision, String evidenceSnapshot) { }
}

package com.hanjisang.pis.v2.production.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2HistologyRepository;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2HistologyRepository.EquipmentScope;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2HistologyRepository.ProcessRow;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2HistologyRepository.TargetScope;

/** Optional physical production trace. These facts never gate Slide completion. */
@Service
public class V2HistologyApplicationService {

    private static final String MATERIAL_PERMISSION = "P14-PERM-014";
    private final JdbcV2HistologyRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2HistologyApplicationService(JdbcV2HistologyRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public HistologyWorkbenchResult workbench(UUID caseId) { return workbench(caseId, null); }

    @Transactional(readOnly = true)
    public HistologyWorkbenchResult workbench(UUID caseId, UUID frozenRoundId) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        var grouped = new LinkedHashMap<UUID, SlideBuilder>();
        for (ProcessRow row : repository.findWorkbench(actor.hospitalScope(), caseId, frozenRoundId)) {
            SlideBuilder builder = grouped.computeIfAbsent(row.slideId(), ignored -> new SlideBuilder(row));
            builder.phases.add(phase(row));
        }
        List<SlideWorkItem> slides = grouped.values().stream().map(SlideBuilder::build).toList();
        return new HistologyWorkbenchResult(slides, traceSummary(slides), Instant.now());
    }

    /** Backward-compatible slide endpoint; block stages are persisted against the source Block. */
    @Transactional
    public PhaseFact start(UUID slideId, String stageCode, String equipmentReference, String operationGroup) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        String stage = validStage(stageCode);
        TargetScope target = targetForSlide(slideId, stage, actor.hospitalScope());
        EquipmentScope equipment = equipment(equipmentReference, actor.hospitalScope());
        ProcessRow row = repository.saveStart(target, actor.hospitalScope(), stage, actor.actorId(), equipment,
                operationGroup, null, Instant.now());
        appendAudit("START", target, stage, actor, null);
        return phase(row);
    }

    @Transactional
    public PhaseFact complete(UUID slideId, String stageCode) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        String stage = validStage(stageCode);
        TargetScope target = targetForSlide(slideId, stage, actor.hospitalScope());
        ProcessRow row = repository.saveComplete(target, actor.hospitalScope(), stage, actor.actorId(), null,
                "STAINING".equals(stage) ? "HE" : null, null, Instant.now());
        appendAudit("COMPLETE", target, stage, actor, null);
        return phase(row);
    }

    @Transactional
    public PhaseFact completeTarget(String targetKind, UUID targetId, CompleteTraceCommand command) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        String stage = validStage(command.stageCode());
        String normalizedKind = normalizeKind(targetKind);
        if (!JdbcV2HistologyRepository.requiredTarget(stage).equals(normalizedKind)) {
            throw badRequest(stageLabel(stage) + "不能记录到" + ("BLOCK".equals(normalizedKind) ? "材块" : "玻片"));
        }
        TargetScope target = repository.findTarget(normalizedKind, targetId, actor.hospitalScope())
                .orElseThrow(() -> notFound(normalizedKind));
        EquipmentScope equipment = equipment(command.equipmentReference(), actor.hospitalScope());
        ProcessRow row = repository.saveComplete(target, actor.hospitalScope(), stage, actor.actorId(), equipment,
                command.stainCode(), command.note(), Instant.now());
        appendAudit("COMPLETE", target, stage, actor, command.note());
        return phase(row);
    }

    @Transactional
    public List<PhaseFact> completeTargets(String targetKind, CompleteTraceBatchCommand command) {
        if (command.targetIds() == null || command.targetIds().isEmpty()) {
            throw badRequest("请选择至少一个材料");
        }
        var actor = authorization.require(MATERIAL_PERMISSION);
        String stage = validStage(command.stageCode());
        String normalizedKind = normalizeKind(targetKind);
        if (!JdbcV2HistologyRepository.requiredTarget(stage).equals(normalizedKind)) {
            throw badRequest("所选材料类型不适用于" + stageLabel(stage));
        }
        EquipmentScope equipment = equipment(command.equipmentReference(), actor.hospitalScope());
        List<TargetScope> targets = command.targetIds().stream().distinct().sorted()
                .map(id -> repository.findTarget(normalizedKind, id, actor.hospitalScope())
                        .orElseThrow(() -> notFound(normalizedKind)))
                .toList();
        Instant now = Instant.now();
        List<PhaseFact> results = new ArrayList<>();
        for (TargetScope target : targets) {
            results.add(phase(repository.saveComplete(target, actor.hospitalScope(), stage, actor.actorId(),
                    equipment, command.stainCode(), command.note(), now)));
        }
        audit.append("PIS-V2-FC03A-TRACE-BATCH-" + stage, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                targets.getFirst().targetId(), "V2-" + normalizedKind, UUID.randomUUID().toString(),
                "批量记录" + stageLabel(stage) + " " + targets.size() + " 项");
        return results;
    }

    @Transactional
    public List<PhaseFact> startBatch(List<UUID> slideIds, String stageCode) {
        if (slideIds == null || slideIds.isEmpty()) throw badRequest("请选择至少一张玻片");
        return slideIds.stream().distinct().map(slideId -> start(slideId, stageCode, null, null)).toList();
    }

    @Transactional
    public List<PhaseFact> completeBatch(List<UUID> slideIds, String stageCode) {
        if (slideIds == null || slideIds.isEmpty()) throw badRequest("请选择至少一张玻片");
        return slideIds.stream().distinct().map(slideId -> complete(slideId, stageCode)).toList();
    }

    @Transactional
    public PhaseFact recordException(UUID slideId, String stageCode, String exceptionCode, String note) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        String stage = validStage(stageCode);
        required(exceptionCode, "请选择异常类型");
        required(note, "请填写异常说明");
        TargetScope target = targetForSlide(slideId, stage, actor.hospitalScope());
        ProcessRow row = repository.saveException(target, actor.hospitalScope(), stage, actor.actorId(),
                exceptionCode.trim(), note.trim(), Instant.now());
        appendAudit("EXCEPTION", target, stage, actor, exceptionCode.trim());
        return phase(row);
    }

    @Transactional
    public PhaseFact resolveException(UUID factId, String note) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        required(note, "请填写异常处理结果");
        ProcessRow row = repository.resolveException(factId, actor.hospitalScope(), actor.actorId(), note.trim(),
                Instant.now()).orElseThrow(() -> new P15BusinessException("V2-PRODUCTION-EXCEPTION-CONFLICT",
                        "异常不存在、已处理或不在当前数据范围", 409));
        audit.append("PIS-V2-FC03A-PRODUCTION-EXCEPTION-RESOLVE", MATERIAL_PERMISSION, actor, "ALLOWED",
                "COMPLETED", factId, "V2-MATERIAL-PROCESS-FACT", UUID.randomUUID().toString(), note.trim());
        return phase(row);
    }

    @Transactional
    public PhaseFact correctTrace(UUID factId, CorrectTraceCommand command) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        required(command.reason(), "技术记录修正原因不能为空");
        ProcessRow prior = repository.findById(factId, actor.hospitalScope())
                .orElseThrow(() -> new P15BusinessException("V2-TRACE-NOT-FOUND", "技术记录不存在或不在当前数据范围", 404));
        EquipmentScope equipment = equipment(command.equipmentReference(), actor.hospitalScope());
        ProcessRow row = repository.correct(factId, actor.hospitalScope(), actor.actorId(), command.completedAt(),
                equipment == null ? null : equipment.id(), command.note(), command.reason().trim(), Instant.now());
        audit.append("PIS-V2-FC03A-TRACE-CORRECT", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", factId,
                "V2-MATERIAL-PROCESS-FACT", UUID.randomUUID().toString(), command.reason().trim());
        return phase(row);
    }

    private TargetScope targetForSlide(UUID slideId, String stage, String organizationReference) {
        return ("BLOCK".equals(JdbcV2HistologyRepository.requiredTarget(stage))
                ? repository.findBlockForSlide(slideId, organizationReference)
                : repository.findSlide(slideId, organizationReference))
                .orElseThrow(() -> new P15BusinessException("V2-MATERIAL-NOT-FOUND",
                        "材料不存在、不属于常规制片或不在当前数据范围", 404));
    }

    private EquipmentScope equipment(String reference, String organizationReference) {
        if (reference == null || reference.isBlank()) return null;
        EquipmentScope equipment = repository.findEquipment(reference, organizationReference)
                .orElseThrow(() -> new P15BusinessException("V2-EQUIPMENT-NOT-FOUND",
                        "设备不存在或不在当前数据范围", 404));
        if (!List.of("ACTIVE", "AVAILABLE", "IN_SERVICE", "NORMAL").contains(equipment.statusCode())) {
            throw new P15BusinessException("V2-EQUIPMENT-UNAVAILABLE", "所选设备当前不可用", 409);
        }
        return equipment;
    }

    private void appendAudit(String action, TargetScope target, String stage,
            com.hanjisang.pis.security.ActorContext actor, String note) {
        audit.append("PIS-V2-FC03A-TRACE-" + action + "-" + stage, MATERIAL_PERMISSION, actor, "ALLOWED",
                "COMPLETED", target.targetId(), "V2-" + target.targetKind(), UUID.randomUUID().toString(),
                note == null ? "stage=" + stage : "stage=" + stage + ";" + note);
    }

    private static String validStage(String stageCode) {
        String normalized = JdbcV2HistologyRepository.normalize(stageCode);
        if (!JdbcV2HistologyRepository.supported(normalized)) throw badRequest("技术环节不受支持");
        return normalized;
    }

    private static String normalizeKind(String targetKind) {
        String normalized = targetKind == null ? "" : targetKind.trim().toUpperCase();
        if (!List.of("BLOCK", "SLIDE").contains(normalized)) throw badRequest("技术记录材料类型不受支持");
        return normalized;
    }

    private static P15BusinessException notFound(String kind) {
        return new P15BusinessException("V2-MATERIAL-NOT-FOUND",
                "BLOCK".equals(kind) ? "材块不存在或不在当前数据范围" : "玻片不存在或不在当前数据范围", 404);
    }

    private static void required(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
    }

    private static P15BusinessException badRequest(String message) {
        return new P15BusinessException("V2-INVALID-REQUEST", message, 400);
    }

    private static String stageLabel(String stage) {
        return switch (stage) {
            case "DEHYDRATION" -> "脱水";
            case "EMBEDDING" -> "包埋";
            case "SECTIONING" -> "切片";
            case "STAINING" -> "染色";
            case "COVERSLIPPING" -> "封片";
            default -> stage;
        };
    }

    private static PhaseFact phase(ProcessRow row) {
        return new PhaseFact(row.factId(), row.targetKind(), row.targetId(), row.phaseCode(), row.startedAt(),
                row.completedAt(), row.operatorRef(), row.deviceReference(), row.equipmentId(),
                row.batchReference(), row.stainCode(), row.exceptionCode(), row.exceptionNote(),
                row.exceptionResolvedAt());
    }

    private static HistologyQueueSummary traceSummary(List<SlideWorkItem> slides) {
        return new HistologyQueueSummary(completed(slides, "DEHYDRATION"), completed(slides, "EMBEDDING"),
                completed(slides, "SECTIONING"), completed(slides, "STAINING"),
                completed(slides, "COVERSLIPPING"),
                (int) slides.stream().filter(item -> item.slideCompletedAt() != null).count(),
                (int) slides.stream().filter(V2HistologyApplicationService::hasOpenException).count());
    }

    private static int completed(List<SlideWorkItem> slides, String stage) {
        return (int) slides.stream().flatMap(item -> item.phases().stream())
                .filter(fact -> stage.equals(fact.phaseCode()) && fact.completedAt() != null)
                .map(PhaseFact::targetId).distinct().count();
    }

    private static boolean hasOpenException(SlideWorkItem item) {
        return item.phases().stream().anyMatch(fact -> fact.exceptionCode() != null
                && fact.exceptionResolvedAt() == null);
    }

    private static final class SlideBuilder {
        private final ProcessRow row;
        private final List<PhaseFact> phases = new ArrayList<>();
        private SlideBuilder(ProcessRow row) { this.row = row; }
        private SlideWorkItem build() {
            String attention = hasOpenException(phases) ? "EXCEPTIONS"
                    : row.slideCompletedAt() != null ? "COMPLETED" : "TRACE_OPTIONAL";
            return new SlideWorkItem(row.slideId(), row.caseId(), row.caseNo(), row.patientReference(),
                    row.slideCode(), row.slideType(), row.businessTypeCode(), row.specimenCode(), row.blockCode(),
                    row.sourceContextType(), row.slideCompletedAt(), row.concurrencyVersion(), row.printCount(),
                    attention, attention, phases);
        }
    }

    private static boolean hasOpenException(List<PhaseFact> phases) {
        return phases.stream().anyMatch(fact -> fact.exceptionCode() != null && fact.exceptionResolvedAt() == null);
    }

    public record CompleteTraceCommand(String stageCode, String equipmentReference, String stainCode, String note) { }
    public record CompleteTraceBatchCommand(List<UUID> targetIds, String stageCode, String equipmentReference,
            String stainCode, String note) { }
    public record CorrectTraceCommand(Instant completedAt, String equipmentReference, String note, String reason) { }
    public record HistologyWorkbenchResult(List<SlideWorkItem> slides, HistologyQueueSummary queues,
            Instant refreshedAt) { }
    /** Counts are recorded trace counts, not mandatory workflow queues. */
    public record HistologyQueueSummary(int dehydration, int embedding, int cutting, int staining,
            int coverslipping, int completed, int exceptions) { }
    public record SlideWorkItem(UUID slideId, UUID caseId, String caseNo, String patientReference, String slideCode,
            String slideType, String businessTypeCode, String specimenCode, String blockCode,
            String sourceContextType, Instant slideCompletedAt, long concurrencyVersion, int printCount,
            String currentPhase, String derivedQueue, List<PhaseFact> phases) { }
    public record PhaseFact(UUID factId, String targetKind, UUID targetId, String phaseCode, Instant startedAt,
            Instant completedAt, String operatorRef, String deviceReference, UUID equipmentId,
            String batchReference, String stainCode, String exceptionCode, String exceptionNote,
            Instant exceptionResolvedAt) { }
}

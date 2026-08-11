package com.hanjisang.pis.v2.production.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2HistologyRepository;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2HistologyRepository.ProcessRow;

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
    public HistologyWorkbenchResult workbench(UUID caseId) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        var grouped = new LinkedHashMap<UUID, SlideBuilder>();
        for (ProcessRow row : repository.findWorkbench(actor.hospitalScope(), caseId)) {
            SlideBuilder builder = grouped.computeIfAbsent(row.slideId(), ignored ->
                    new SlideBuilder(row.slideId(), row.caseId(), row.caseNo(), row.patientReference(),
                            row.businessTypeCode(), row.specimenCode(), row.blockCode(), row.sourceContextType(),
                            row.slideCode(), row.slideType(), row.slideCompletedAt(), row.concurrencyVersion(),
                            row.printCount()));
            builder.phases.add(new PhaseFact(row.phaseCode(), row.startedAt(), row.completedAt(), row.operatorRef(),
                    row.deviceReference(), row.batchReference(), row.exceptionCode(), row.exceptionNote()));
        }
        List<SlideWorkItem> slides = grouped.values().stream().map(SlideBuilder::build).toList();
        return new HistologyWorkbenchResult(slides, queues(slides), Instant.now());
    }

    @Transactional
    public PhaseFact start(UUID slideId, String phaseCode, String deviceReference, String batchReference) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        validPhase(phaseCode);
        ensureSlide(slideId, actor.hospitalScope());
        PhaseFact fact = phase(repository.saveStart(slideId, actor.hospitalScope(), phaseCode, actor.actorId(),
                deviceReference, batchReference, Instant.now()));
        audit.append("PIS-V2-PX01-HISTOLOGY-START-" + phaseCode, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slideId,
                "V2-SLIDE", UUID.randomUUID().toString(), "phase=" + phaseCode);
        return fact;
    }

    @Transactional
    public PhaseFact complete(UUID slideId, String phaseCode) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        validPhase(phaseCode);
        ensureSlide(slideId, actor.hospitalScope());
        PhaseFact fact = phase(repository.saveComplete(slideId, actor.hospitalScope(), phaseCode, actor.actorId(), Instant.now()));
        audit.append("PIS-V2-PX01-HISTOLOGY-COMPLETE-" + phaseCode, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slideId,
                "V2-SLIDE", UUID.randomUUID().toString(), "phase=" + phaseCode);
        return fact;
    }

    @Transactional
    public List<PhaseFact> startBatch(List<UUID> slideIds, String phaseCode) {
        if (slideIds == null || slideIds.isEmpty()) {
            throw new P15BusinessException("V2-HISTOLOGY-BATCH-EMPTY", "请选择至少一张玻片", 400);
        }
        return slideIds.stream().distinct().map(slideId -> start(slideId, phaseCode, null, null)).toList();
    }

    @Transactional
    public List<PhaseFact> completeBatch(List<UUID> slideIds, String phaseCode) {
        if (slideIds == null || slideIds.isEmpty()) {
            throw new P15BusinessException("V2-HISTOLOGY-BATCH-EMPTY", "请选择至少一张玻片", 400);
        }
        return slideIds.stream().distinct().map(slideId -> complete(slideId, phaseCode)).toList();
    }

    @Transactional
    public PhaseFact recordException(UUID slideId, String phaseCode, String exceptionCode, String note) {
        var actor = authorization.require(MATERIAL_PERMISSION);
        validPhase(phaseCode);
        ensureSlide(slideId, actor.hospitalScope());
        if (exceptionCode == null || exceptionCode.isBlank() || note == null || note.isBlank()) {
            throw new P15BusinessException("V2-HISTOLOGY-EXCEPTION-REQUIRED", "请填写异常类型和说明", 400);
        }
        PhaseFact fact = phase(repository.saveException(slideId, actor.hospitalScope(), phaseCode, actor.actorId(),
                exceptionCode.trim(), note.trim(), Instant.now()));
        audit.append("PIS-V2-PX01-HISTOLOGY-EXCEPTION-" + phaseCode, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slideId,
                "V2-SLIDE", UUID.randomUUID().toString(), "phase=" + phaseCode + ";exception=" + exceptionCode.trim());
        return fact;
    }

    private static void validPhase(String phaseCode) {
        if (phaseCode == null || !JdbcV2HistologyRepository.supported(phaseCode)) {
            throw new P15BusinessException("V2-HISTOLOGY-PHASE-INVALID", "技术环节不受支持", 400);
        }
    }

    private void ensureSlide(UUID slideId, String organizationReference) {
        if (repository.findSlide(slideId, organizationReference).isEmpty()) {
            throw new P15BusinessException("V2-SLIDE-NOT-FOUND", "玻片不存在或不在当前数据范围", 404);
        }
    }

    private static PhaseFact phase(ProcessRow row) {
        return new PhaseFact(row.phaseCode(), row.startedAt(), row.completedAt(), row.operatorRef(),
                row.deviceReference(), row.batchReference(), row.exceptionCode(), row.exceptionNote());
    }

    private static HistologyQueueSummary queues(List<SlideWorkItem> slides) {
        return new HistologyQueueSummary(
                waiting(slides, "DEHYDRATION", null), waiting(slides, "EMBEDDING", "DEHYDRATION"),
                waiting(slides, "SECTIONING", "EMBEDDING"), waiting(slides, "STAINING", "SECTIONING"),
                waiting(slides, "MOUNTING", "STAINING"),
                (int) slides.stream().filter(item -> item.slideCompletedAt() != null).count(),
                (int) slides.stream().filter(V2HistologyApplicationService::hasException).count());
    }

    private static int waiting(List<SlideWorkItem> slides, String phaseCode, String previousPhase) {
        return (int) slides.stream().filter(item -> {
            Map<String, PhaseFact> facts = item.phases().stream()
                    .collect(java.util.stream.Collectors.toMap(PhaseFact::phaseCode, fact -> fact, (left, right) -> left));
            PhaseFact current = facts.get(phaseCode);
            PhaseFact previous = previousPhase == null ? null : facts.get(previousPhase);
            return (current == null || current.startedAt() == null || current.completedAt() == null)
                    && (previous == null || previous.completedAt() != null);
        }).count();
    }

    private static boolean hasException(SlideWorkItem item) {
        return item.phases().stream().anyMatch(fact -> fact.exceptionCode() != null && !fact.exceptionCode().isBlank());
    }

    private static final class SlideBuilder {
        private final UUID slideId;
        private final UUID caseId;
        private final String caseNo;
        private final String patientReference;
        private final String businessTypeCode;
        private final String specimenCode;
        private final String blockCode;
        private final String sourceContextType;
        private final String slideCode;
        private final String slideType;
        private final Instant slideCompletedAt;
        private final long concurrencyVersion;
        private final int printCount;
        private final List<PhaseFact> phases = new ArrayList<>();
        private SlideBuilder(UUID slideId, UUID caseId, String caseNo, String patientReference, String slideCode,
                String slideType, Instant slideCompletedAt) {
            this(slideId, caseId, caseNo, patientReference, null, null, null, null, slideCode, slideType,
                    slideCompletedAt, 0, 0);
        }
        private SlideBuilder(UUID slideId, UUID caseId, String caseNo, String patientReference,
                String businessTypeCode, String specimenCode, String blockCode, String sourceContextType,
                String slideCode, String slideType, Instant slideCompletedAt, long concurrencyVersion,
                int printCount) {
            this.slideId = slideId; this.caseId = caseId; this.caseNo = caseNo; this.patientReference = patientReference;
            this.businessTypeCode = businessTypeCode; this.specimenCode = specimenCode; this.blockCode = blockCode;
            this.sourceContextType = sourceContextType; this.slideCode = slideCode; this.slideType = slideType;
            this.slideCompletedAt = slideCompletedAt; this.concurrencyVersion = concurrencyVersion;
            this.printCount = printCount;
        }
        private SlideWorkItem build() {
            String queue = queueCode(derivedQueue(phases, slideCompletedAt));
            return new SlideWorkItem(slideId, caseId, caseNo, patientReference, slideCode, slideType,
                    businessTypeCode, specimenCode, blockCode, sourceContextType, slideCompletedAt,
                    concurrencyVersion, printCount, queue, queue, phases);
        }
    }

    private static String derivedQueue(List<PhaseFact> phases, Instant slideCompletedAt) {
        if (hasException(phases)) return "EXCEPTIONS";
        if (slideCompletedAt != null) return "COMPLETED";
        for (int index = 0; index < PHASE_ORDER.length; index++) {
            String phaseCode = PHASE_ORDER[index];
            PhaseFact current = phases.stream().filter(fact -> phaseCode.equals(fact.phaseCode())).findFirst().orElse(null);
            String previousCode = index == 0 ? null : PHASE_ORDER[index - 1];
            PhaseFact previous = previousCode == null ? null : phases.stream()
                    .filter(fact -> previousCode.equals(fact.phaseCode())).findFirst().orElse(null);
            if (current == null || current.completedAt() == null) {
                if (previous == null || previous.completedAt() != null) return phaseCode;
            }
        }
        return "COMPLETED";
    }

    private static String queueCode(String phaseCode) {
        return switch (phaseCode) {
            case "SECTIONING" -> "CUTTING";
            case "MOUNTING" -> "COVERSLIPPING";
            default -> phaseCode;
        };
    }

    private static boolean hasException(List<PhaseFact> phases) {
        return phases.stream().anyMatch(fact -> fact.exceptionCode() != null && !fact.exceptionCode().isBlank());
    }

    private static final String[] PHASE_ORDER = { "DEHYDRATION", "EMBEDDING", "SECTIONING", "STAINING", "MOUNTING" };

    public record HistologyWorkbenchResult(List<SlideWorkItem> slides, HistologyQueueSummary queues,
            Instant refreshedAt) { }
    public record HistologyQueueSummary(int dehydration, int embedding, int cutting, int staining,
            int coverslipping, int completed, int exceptions) { }
    public record SlideWorkItem(UUID slideId, UUID caseId, String caseNo, String patientReference, String slideCode,
            String slideType, String businessTypeCode, String specimenCode, String blockCode,
            String sourceContextType, Instant slideCompletedAt, long concurrencyVersion, int printCount,
            String currentPhase, String derivedQueue, List<PhaseFact> phases) { }
    public record PhaseFact(String phaseCode, Instant startedAt, Instant completedAt, String operatorRef,
            String deviceReference, String batchReference, String exceptionCode, String exceptionNote) { }
}

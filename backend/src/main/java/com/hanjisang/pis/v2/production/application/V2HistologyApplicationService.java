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
                    new SlideBuilder(row.slideId(), row.caseId(), row.caseNo(), row.patientReference(), row.slideCode(),
                            row.slideType(), row.slideCompletedAt()));
            builder.phases.add(new PhaseFact(row.phaseCode(), row.startedAt(), row.completedAt(), row.operatorRef(),
                    row.deviceReference(), row.batchReference(), row.exceptionCode(), row.exceptionNote()));
        }
        return new HistologyWorkbenchResult(grouped.values().stream().map(SlideBuilder::build).toList(), Instant.now());
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

    private static final class SlideBuilder {
        private final UUID slideId;
        private final UUID caseId;
        private final String caseNo;
        private final String patientReference;
        private final String slideCode;
        private final String slideType;
        private final Instant slideCompletedAt;
        private final List<PhaseFact> phases = new ArrayList<>();
        private SlideBuilder(UUID slideId, UUID caseId, String caseNo, String patientReference, String slideCode,
                String slideType, Instant slideCompletedAt) {
            this.slideId = slideId; this.caseId = caseId; this.caseNo = caseNo; this.patientReference = patientReference;
            this.slideCode = slideCode; this.slideType = slideType; this.slideCompletedAt = slideCompletedAt;
        }
        private SlideWorkItem build() { return new SlideWorkItem(slideId, caseId, caseNo, patientReference, slideCode,
                slideType, slideCompletedAt, phases); }
    }

    public record HistologyWorkbenchResult(List<SlideWorkItem> slides, Instant refreshedAt) { }
    public record SlideWorkItem(UUID slideId, UUID caseId, String caseNo, String patientReference, String slideCode,
            String slideType, Instant slideCompletedAt, List<PhaseFact> phases) { }
    public record PhaseFact(String phaseCode, Instant startedAt, Instant completedAt, String operatorRef,
            String deviceReference, String batchReference, String exceptionCode, String exceptionNote) { }
}

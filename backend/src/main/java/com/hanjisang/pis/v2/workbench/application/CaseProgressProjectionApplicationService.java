package com.hanjisang.pis.v2.workbench.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.v2.capability.BusinessTypeCapability;
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2CaseProgressRepository;
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2CaseProgressRepository.ProgressRow;

/** Builds role-neutral progress from existing case, material, responsibility and report facts. */
@Service
public class CaseProgressProjectionApplicationService {

    private static final String QUERY_PERMISSION = "P14-PERM-048";

    private final JdbcV2CaseProgressRepository repository;
    private final P15AuthorizationService authorization;

    public CaseProgressProjectionApplicationService(JdbcV2CaseProgressRepository repository,
            P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public CaseProgress progress(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        ProgressRow row = repository.find(caseId, actor.hospitalScope());
        if (row == null) {
            throw new IllegalArgumentException("病例不存在或不在当前数据范围");
        }
        return project(row);
    }

    @Transactional(readOnly = true)
    public List<CaseProgress> registeredCases() {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return repository.findRegistered(actor.hospitalScope(), actor.actorId()).stream()
                .map(this::project).toList();
    }

    public CaseProgress project(ProgressRow row) {
        BusinessTypeCapability capability = BusinessTypeCapability.from(row.businessTypeCode(), row.modalityCode());
        int materialRequired = capability.supportsDirectSlides() ? row.specimenCount() : row.requiredSlideCount();
        int materialCompleted = capability.supportsDirectSlides()
                ? row.completedDirectSpecimenCount() : row.completedSlideCount();
        Instant enteredAt = row.responsibilityEnteredAt() != null ? row.responsibilityEnteredAt()
                : row.currentStageCode().equals("SIGNED") && row.signedAt() != null ? row.signedAt()
                : row.materialAt() == null ? row.createdAt() : row.materialAt();
        long waitingMinutes = Math.max(0, Duration.between(enteredAt, Instant.now()).toMinutes());
        String currentResponsible = row.responsibilityName();
        if (currentResponsible == null && "CYTOLOGY_PREPARATION".equals(row.currentStageCode())) {
            currentResponsible = "制片人员";
        } else if (currentResponsible == null && "HISTOLOGY_PREPARATION".equals(row.currentStageCode())) {
            currentResponsible = "技术人员";
        }
        return new CaseProgress(row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(),
                row.businessTypeName(), row.lifecycle(), row.currentStageCode(), row.currentStageLabel(),
                currentResponsible, new MaterialProgress(materialCompleted, materialRequired,
                        materialRequired == 0 ? "尚未建立" : materialCompleted + "/" + materialRequired),
                row.reportStatus(), enteredAt, waitingMinutes, capability,
                steps(row, capability, materialRequired, materialCompleted));
    }

    private static List<ProgressStep> steps(ProgressRow row, BusinessTypeCapability capability,
            int materialRequired, int materialCompleted) {
        boolean cancelled = "CANCELLED".equals(row.lifecycle());
        boolean materialComplete = materialRequired > 0 && materialCompleted >= materialRequired;
        String productionCode = capability.supportsDirectSlides() ? "CYTOLOGY_PREPARATION"
                : "FROZEN".equals(row.businessTypeCode()) ? "FROZEN_PRODUCTION" : "HISTOLOGY_PREPARATION";
        String productionLabel = capability.supportsDirectSlides() ? "细胞制片"
                : "FROZEN".equals(row.businessTypeCode()) ? "冰冻制片" : "常规制片";
        return List.of(
                new ProgressStep("REGISTRATION", "已登记", cancelled ? "COMPLETED" : "COMPLETED"),
                new ProgressStep("SPECIMEN", "标本", row.specimenCount() > 0 ? "COMPLETED" : "CURRENT"),
                new ProgressStep(productionCode, productionLabel,
                        materialComplete ? "COMPLETED"
                                : row.currentStageCode().equals(productionCode) ? "CURRENT" : "PENDING"),
                new ProgressStep("DIAGNOSIS", "诊断",
                        row.currentStageCode().contains("DIAGNOSIS") || "AUDIT".equals(row.currentStageCode())
                                ? "CURRENT" : "SIGNED".equals(row.currentStageCode()) ? "COMPLETED" : "PENDING"),
                new ProgressStep("REPORT", "报告",
                        "SIGNED".equals(row.currentStageCode()) ? "COMPLETED" : "PENDING"));
    }

    public record CaseProgress(UUID caseId, String pathologyNo, String patientReference,
            String businessTypeCode, String businessTypeName, String lifecycle, String currentStageCode,
            String currentStageLabel, String currentResponsible, MaterialProgress material, String reportStatus,
            Instant enteredAt, long waitingMinutes, BusinessTypeCapability capability,
            List<ProgressStep> steps) { }

    public record MaterialProgress(int completed, int required, String status) { }

    public record ProgressStep(String code, String label, String status) { }
}

package com.hanjisang.pis.v2.workspace.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.JdbcAuditEventRepository.AuditChange;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository.MaterialTreeRow;
import com.hanjisang.pis.v2.workspace.infrastructure.JdbcV2CaseWorkspaceRepository;
import com.hanjisang.pis.v2.workspace.infrastructure.JdbcV2CaseWorkspaceRepository.AuditRow;
import com.hanjisang.pis.v2.workspace.infrastructure.JdbcV2CaseWorkspaceRepository.CaseHeaderRow;

@Service
public class V2CaseWorkspaceApplicationService {

    private static final String CASE_CONTEXT_PERMISSION = "P14-PERM-048";

    private final JdbcV2CaseWorkspaceRepository repository;
    private final P15AuthorizationService authorization;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public V2CaseWorkspaceApplicationService(JdbcV2CaseWorkspaceRepository repository,
            P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public CaseWorkspaceResult workspace(UUID caseId) {
        var actor = authorization.require(CASE_CONTEXT_PERMISSION);
        CaseHeaderRow header = repository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> new P15BusinessException("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围", 404));
        List<MaterialTreeRow> materialRows = repository.findMaterials(caseId, actor.hospitalScope());
        return new CaseWorkspaceResult(
                header(header),
                materialTree(caseId, header, materialRows),
                repository.findGrossings(caseId, actor.hospitalScope()).stream().map(this::grossing).toList(),
                repository.findResponsibilities(caseId, actor.hospitalScope()).stream().map(this::responsibility).toList(),
                repository.findTechnicalOrders(caseId, actor.hospitalScope()).stream().map(this::technicalOrder).toList(),
                repository.findDigitalSlides(caseId).stream().map(this::digitalSlide).toList(),
                repository.findReports(caseId, actor.hospitalScope()).stream().map(this::report).toList(),
                repository.findTimeline(caseId, actor.hospitalScope()).stream().map(this::timeline).toList(),
                repository.findFrozenRounds(caseId, actor.hospitalScope()).stream().map(this::frozenRound).toList(),
                Instant.now());
    }

    private CaseHeader header(CaseHeaderRow row) {
        return new CaseHeader(row.caseId(), row.pathologyNo(), row.businessTypeCode(), row.businessTypeName(),
                row.lifecycle(), row.applicationItemCode(), row.sourceSystemCode(), row.applicationNo(),
                row.patientReference(), row.visitReference(), row.createdAt(), row.frozenSourceCaseId(),
                row.frozenSourcePathologyNo(), row.routineTargetCaseId(), row.routineTargetPathologyNo());
    }

    private MaterialTree materialTree(UUID caseId, CaseHeaderRow header, List<MaterialTreeRow> rows) {
        var specimens = new java.util.LinkedHashMap<UUID, SpecimenBuilder>();
        for (MaterialTreeRow row : rows) {
            SpecimenBuilder specimen = specimens.computeIfAbsent(row.specimenId(), ignored ->
                    new SpecimenBuilder(row.specimenId(), row.specimenNo(), row.specimenCode(), row.specimenKindCode()));
            if (row.blockId() != null) {
                BlockBuilder block = specimen.blocks.computeIfAbsent(row.blockId(), ignored ->
                        new BlockBuilder(row.blockId(), row.blockCode(), row.blockType()));
                if (row.slideId() != null) {
                    block.slides.add(slide(row));
                }
            } else if (row.slideId() != null) {
                specimen.directSlides.add(slide(row));
            }
        }
        return new MaterialTree(caseId, header.pathologyNo(), header.businessTypeCode(), specimens.values().stream()
                .map(SpecimenBuilder::build).toList());
    }

    private static Slide slide(MaterialTreeRow row) {
        return new Slide(row.slideId(), row.slideCode(), row.slideType(), row.sourceContextType(), row.completedAt(),
                row.completedAt() != null, Boolean.TRUE.equals(row.required()), row.completedByRef());
    }

    private Grossing grossing(JdbcV2CaseWorkspaceRepository.GrossingRow row) {
        return new Grossing(row.grossingId(), row.grossingNo(), row.sourceType(), row.grossDescription(),
                row.grossingDoctor(), row.recorder(), row.startedAt(), row.completedAt(), row.completedBy());
    }

    private Responsibility responsibility(JdbcV2CaseWorkspaceRepository.ResponsibilityRow row) {
        return new Responsibility(row.responsibilityId(), row.diagnosisId(), row.roleCode(), row.doctorId(),
                row.doctorName(), row.sequenceNo(), row.acceptedAt(), row.completedAt(), row.endedAt(),
                row.assignmentSource(), row.assignmentReason());
    }

    private TechnicalOrder technicalOrder(JdbcV2CaseWorkspaceRepository.TechnicalOrderRow row) {
        return new TechnicalOrder(row.orderId(), row.orderNo(), row.statusCode(), row.requiredBeforeSignOut(),
                row.createdAt(), row.createdBy(), row.itemCount(), row.resultCount());
    }

    private DigitalSlide digitalSlide(JdbcV2CaseWorkspaceRepository.DigitalSlideRow row) {
        return new DigitalSlide(row.digitalSlideId(), row.blockId(), row.slideId(), row.bindingMode(),
                row.statusCode(), row.viewerReference(), row.sourcePlatform(), row.updatedAt());
    }

    private Report report(JdbcV2CaseWorkspaceRepository.ReportRow row) {
        return new Report(row.reportId(), row.reportNo(), row.natureCode(), row.priorReportId(), row.statusCode(),
                row.signedBy(), row.signedAt(), row.withdrawnBy(), row.withdrawnAt(), row.withdrawalReason(),
                row.pdfFileReference());
    }

    private FrozenRoundSummary frozenRound(JdbcV2CaseWorkspaceRepository.FrozenRoundRow row) {
        return new FrozenRoundSummary(row.roundId(), row.roundNo(), row.statusCode(), row.arrivalTime(),
                row.diagnosisSignedTime(), row.specimenCount(), row.slideCount(), row.completedSlideCount(),
                row.reportCount());
    }

    private TimelineEntry timeline(AuditRow row) {
        return new TimelineEntry(row.eventId(), row.occurredAt(), displayActor(row.actorName(), row.actorRef()), row.actorRef(),
                eventTitle(row.operationCode()),
                eventDetail(row.operationCode(), row.reason()), row.operationCode(),
                row.categoryCode() == null ? category(row.operationCode()) : row.categoryCode(), row.targetKind(),
                row.targetId(), row.targetDisplayCode(), targetDisplayName(row.targetKind()), changes(row.changesJson()));
    }

    private static String displayActor(String actorName, String actorRef) {
        String value = actorName == null || actorName.isBlank() ? actorRef : actorName;
        if (value == null || value.isBlank()) return "系统用户";
        if (value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return "系统用户";
        }
        return value;
    }

    private List<TimelineChange> changes(String changesJson) {
        if (changesJson == null || changesJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(changesJson, new TypeReference<List<AuditChange>>() { }).stream()
                    .map(change -> new TimelineChange(change.fieldCode(), change.fieldLabel(), change.beforeValue(),
                            change.afterValue()))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String targetDisplayName(String targetKind) {
        if (targetKind == null) return null;
        return switch (targetKind) {
            case "V2-CASE" -> "病例";
            case "V2-APPLICATION" -> "申请";
            case "V2-SPECIMEN" -> "标本";
            case "V2-GROSSING" -> "取材记录";
            case "V2-BLOCK" -> "蜡块";
            case "V2-SLIDE" -> "玻片";
            case "V2-DIGITAL-SLIDE" -> "数字切片";
            case "V2-DIAGNOSIS" -> "诊断";
            case "V2-RESPONSIBILITY" -> "责任链";
            case "V2-TECHNICAL-ORDER", "V2-TECHNICAL-ORDER-ITEM" -> "技术医嘱";
            case "V2-REPORT" -> "报告";
            case "V2-FROZEN-ROUND" -> "冰冻轮次";
            case "V2-ARCHIVE-BATCH", "V2-ARCHIVE-ITEM" -> "归档材料";
            case "V2-LOAN" -> "借阅记录";
            default -> "业务对象";
        };
    }

    private static String category(String operationCode) {
        if (operationCode == null) return "SYSTEM";
        String code = operationCode.toUpperCase();
        if (code.contains("CASE") || code.contains("SPECIMEN") || code.contains("REGISTRATION")
                || code.contains("APPLICATION") || code.contains("INBOUND")) return "REGISTRATION";
        if (code.contains("ARCHIVE") || code.contains("LOAN") || code.contains("CUSTODY")
                || code.contains("DESTRUCTION")) return "ARCHIVE";
        if (code.contains("GROSS") || code.contains("BLOCK") || code.contains("SLIDE") || code.contains("PRINT")
                || code.contains("MATERIAL")) return "MATERIAL";
        if (code.contains("TECHNICAL") || code.contains("HISTOLOGY")) return "TECHNICAL";
        if (code.contains("DIAGNOSIS") || code.contains("RESPONSIBILITY")) return "DIAGNOSIS";
        if (code.contains("REPORT") || code.contains("SIGN") || code.contains("WITHDRAW")
                || code.contains("SUPPLEMENT")) return "REPORT";
        if (code.contains("HIS") || code.contains("LIS") || code.contains("EMR")
                || code.contains("INTEGRATION")) return "INTEGRATION";
        return "SYSTEM";
    }

    private static String eventTitle(String operation) {
        if (operation == null) return "完成业务操作";
        if (operation.contains("CASE-CREATE")) return "完成登记";
        if (operation.contains("SPECIMEN-REGISTER")) return "登记标本";
        if (operation.contains("SPECIMEN-UPDATE")) return "修改标本信息";
        if (operation.contains("GROSSING-CREATE")) return "开始取材";
        if (operation.contains("GROSSING-COMPLETE")) return "完成取材";
        if (operation.contains("BLOCK-CREATE")) return "新增蜡块";
        if (operation.contains("BLOCK-UPDATE")) return "修改蜡块";
        if (operation.contains("BLOCK-SOFT-DELETE")) return "作废蜡块";
        if (operation.contains("SLIDE-CREATE")) return "生成玻片";
        if (operation.contains("SLIDE-COMPLETE")) return "完成制片";
        if (operation.contains("PRINT")) return "完成标签打印";
        if (operation.contains("DIAGNOSIS-CLAIM")) return "接诊病例";
        if (operation.contains("DIAGNOSIS-ASSIGN")) return "分配诊断责任";
        if (operation.contains("DIAGNOSIS-EDIT")) return "修改诊断";
        if (operation.contains("RESPONSIBILITY-COMPLETE")) return "完成诊断责任";
        if (operation.contains("TECHNICAL-ORDER-CREATE")) return "开立技术医嘱";
        if (operation.contains("TECHNICAL-ORDER-EXECUTE")) return "开始执行技术医嘱";
        if (operation.contains("TECHNICAL-RESULT")) return "技术结果返回";
        if (operation.contains("DIGITAL-SLIDE")) return "更新数字切片";
        if (operation.contains("HISTOLOGY-START")) return phaseLabel(operation) + "开始";
        if (operation.contains("HISTOLOGY-COMPLETE")) return phaseLabel(operation) + "完成";
        if (operation.contains("HISTOLOGY-EXCEPTION")) return phaseLabel(operation) + "记录异常";
        if (operation.contains("REPORT-SIGN-OUT")) return "签发报告";
        if (operation.contains("REPORT-WITHDRAW")) return "撤回报告";
        if (operation.contains("REPORT-SUPPLEMENT")) return "签发补充报告";
        if (operation.contains("FROZEN-ROUND")) return "更新冰冻轮次";
        if (operation.contains("ARCHIVE")) return "更新归档位置";
        if (operation.contains("BORROW")) return "借出材料";
        if (operation.contains("RETURN")) return "归还材料";
        return "完成业务操作";
    }

    private static String eventDetail(String operation, String reason) {
        if (operation == null) return "";
        if (operation.contains("CASE-CREATE")) return "病例已建立，病理号已生成";
        if (operation.contains("GROSSING")) return "病例取材事实已记录";
        if (operation.contains("SLIDE-COMPLETE")) return "玻片已标记为完成";
        if (operation.contains("HISTOLOGY-EXCEPTION")) return appendReason("技术人员已记录异常，原始事实仍保留", reason);
        if (operation.contains("DIAGNOSIS-EDIT")) return "诊断内容已修改，历史记录已保留";
        if (operation.contains("RESPONSIBILITY-COMPLETE")) return "责任节点已完成，诊断快照已保留";
        if (operation.contains("REPORT-SIGN-OUT")) return "正式报告已生成，可查看 PDF";
        if (operation.contains("REPORT-WITHDRAW")) return appendReason("报告已撤回，原始记录保留", reason);
        if (operation.contains("REPORT-SUPPLEMENT")) return appendReason("补充报告已关联原报告", reason);
        if (reason != null && (reason.contains("beforeDigest=") || reason.contains("afterDigest=")
                || reason.contains("role="))) return "业务记录已更新，历史快照已保留";
        return safeReason(reason);
    }

    private static String appendReason(String message, String reason) {
        String safe = safeReason(reason);
        return safe.isBlank() ? message : message + "；原因：" + safe;
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "";
        String normalized = reason.trim();
        if (normalized.matches("(?is).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*")
                || normalized.contains("beforeDigest=") || normalized.contains("afterDigest=")
                || normalized.contains("old=") || normalized.contains("new=") || normalized.contains("role=")) {
            return "业务记录已更新，历史快照已保留";
        }
        return normalized;
    }

    private static String phaseLabel(String operation) {
        if (operation.contains("DEHYDRATION")) return "脱水";
        if (operation.contains("EMBEDDING")) return "包埋";
        if (operation.contains("SECTIONING")) return "切片";
        if (operation.contains("STAINING")) return "染色";
        if (operation.contains("MOUNTING")) return "封片";
        return "技术环节";
    }

    private record SpecimenBuilder(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            java.util.Map<UUID, BlockBuilder> blocks, List<Slide> directSlides) {
        private SpecimenBuilder(UUID id, String no, String code, String kind) {
            this(id, no, code, kind, new java.util.LinkedHashMap<>(), new java.util.ArrayList<>());
        }
        private Specimen build() {
            return new Specimen(specimenId, specimenNo, specimenCode, specimenKindCode,
                    blocks.values().stream().map(BlockBuilder::build).toList(), directSlides);
        }
    }

    private record BlockBuilder(UUID blockId, String blockCode, String blockType, List<Slide> slides) {
        private BlockBuilder(UUID id, String code, String type) { this(id, code, type, new java.util.ArrayList<>()); }
        private Block build() { return new Block(blockId, blockCode, blockType, slides); }
    }

    public record CaseWorkspaceResult(CaseHeader caseHeader, MaterialTree materialTree, List<Grossing> grossings,
            List<Responsibility> responsibilities, List<TechnicalOrder> technicalOrders,
            List<DigitalSlide> digitalSlides, List<Report> reports, List<TimelineEntry> timeline,
            List<FrozenRoundSummary> frozenRounds, Instant refreshedAt) { }

    public record FrozenRoundSummary(UUID roundId, int roundNo, String statusCode, Instant arrivalTime,
            Instant diagnosisSignedTime, int specimenCount, int slideCount, int completedSlideCount,
            int reportCount) { }

    public record CaseHeader(UUID caseId, String pathologyNo, String businessTypeCode, String businessTypeName,
            String lifecycle, String applicationItemCode, String sourceSystemCode, String applicationNo,
            String patientReference, String visitReference, Instant createdAt, UUID frozenSourceCaseId,
            String frozenSourcePathologyNo, UUID routineTargetCaseId, String routineTargetPathologyNo) { }

    public record MaterialTree(UUID caseId, String pathologyNo, String businessTypeCode, List<Specimen> specimens) { }
    public record Specimen(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            List<Block> blocks, List<Slide> directSlides) { }
    public record Block(UUID blockId, String blockCode, String blockType, List<Slide> slides) { }
    public record Slide(UUID slideId, String slideCode, String slideType, String sourceContextType,
            Instant completedAt, boolean completed, boolean required, String completedBy) { }
    public record Grossing(UUID grossingId, String grossingNo, String sourceType, String grossDescription,
            String grossingDoctor, String recorder, Instant startedAt, Instant completedAt, String completedBy) { }
    public record Responsibility(UUID responsibilityId, UUID diagnosisId, String roleCode, String doctorId,
            String doctorName, int sequenceNo, Instant acceptedAt, Instant completedAt, Instant endedAt,
            String assignmentSource, String assignmentReason) { }
    public record TechnicalOrder(UUID orderId, String orderNo, String statusCode, boolean requiredBeforeSignOut,
            Instant createdAt, String createdBy, int itemCount, int resultCount) { }
    public record DigitalSlide(UUID digitalSlideId, UUID blockId, UUID slideId, String bindingMode, String statusCode,
            String viewerReference, String sourcePlatform, Instant updatedAt) { }
    public record Report(UUID reportId, String reportNo, String natureCode, UUID priorReportId, String statusCode,
            String signedBy, Instant signedAt, String withdrawnBy, Instant withdrawnAt, String withdrawalReason,
            String pdfFileReference) { }
    public record TimelineEntry(UUID eventId, Instant occurredAt, String actorName, String actorRef, String title,
            String detail, String operationCode, String categoryCode, String targetKind, UUID targetId,
            String targetDisplayCode, String targetDisplayName, List<TimelineChange> changes) { }
    public record TimelineChange(String fieldCode, String fieldLabel, String beforeValue, String afterValue) { }
}

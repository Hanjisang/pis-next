package com.hanjisang.pis.v2.production.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository.CytologyRow;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository.ExceptionRow;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository.FrozenRow;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository.RoutineRow;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository.SlideRow;
import com.hanjisang.pis.v2.production.infrastructure.JdbcV2ProductionWorkbenchRepository.TechnicalRow;

/** Projects production work from business sources, never from physical phases. */
@Service
public class V2ProductionWorkbenchApplicationService {

    public static final String MATERIAL_PERMISSION = "P14-PERM-014";
    public static final String FROZEN_PERMISSION = "P14-PERM-008";
    public static final String TECHNICAL_PERMISSION = "P14-PERM-017";

    private final JdbcV2ProductionWorkbenchRepository repository;
    private final P15AuthorizationService authorization;

    public V2ProductionWorkbenchApplicationService(JdbcV2ProductionWorkbenchRepository repository,
            P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public ProductionWorkbenchResult workbench() {
        var materialAccess = authorization.decide(MATERIAL_PERMISSION);
        var frozenAccess = authorization.decide(FROZEN_PERMISSION);
        var technicalAccess = authorization.decide(TECHNICAL_PERMISSION);
        if (!materialAccess.allowed() && !frozenAccess.allowed() && !technicalAccess.allowed()) {
            throw new P15BusinessException("P14-PERMISSION-DENIED", "当前主体没有生产工作台权限", 403);
        }
        ActorContext actor = materialAccess.allowed() ? materialAccess.actor()
                : frozenAccess.allowed() ? frozenAccess.actor() : technicalAccess.actor();
        Instant now = Instant.now();

        List<ProductionItem> routine = materialAccess.allowed() ? repository.findRoutine(actor.hospitalScope()).stream()
                .map(row -> routineItem(row, now)).toList() : List.of();
        List<ProductionItem> cytology = materialAccess.allowed()
                ? repository.findCytology(actor.hospitalScope()).stream().map(row -> cytologyItem(row, now)).toList()
                : List.of();
        List<ProductionItem> frozen = frozenAccess.allowed()
                ? repository.findFrozen(actor.hospitalScope()).stream().map(row -> frozenItem(row, now)).toList()
                : List.of();
        List<ProductionItem> technical = technicalAccess.allowed()
                ? technicalItems(repository.findTechnical(actor.hospitalScope()), now)
                : List.of();
        List<ProductionItem> incompleteSlides = materialAccess.allowed()
                ? repository.findIncompleteSlides(actor.hospitalScope()).stream()
                        .map(row -> incompleteSlideItem(row, now)).toList()
                : List.of();
        List<ProductionItem> exceptions = (materialAccess.allowed() || frozenAccess.allowed()
                || technicalAccess.allowed())
                        ? repository.findExceptions(actor.hospitalScope()).stream()
                                .map(row -> exceptionItem(row, now)).toList()
                        : List.of();

        return new ProductionWorkbenchResult(now,
                new Queues(queue("ROUTINE_PRODUCTION", "常规制片", routine),
                        queue("CYTOLOGY_PRODUCTION", "细胞制片", cytology),
                        queue("FROZEN_PRODUCTION", "冰冻制片", frozen),
                        queue("TECHNICAL_ORDER", "技术医嘱", technical),
                        queue("INCOMPLETE_SLIDES", "待完成玻片", incompleteSlides),
                        queue("EXCEPTIONS", "异常 / 返工", exceptions)));
    }

    private static QueueView queue(String code, String label, List<ProductionItem> items) {
        return new QueueView(code, label, items.size(), items);
    }

    private static ProductionItem routineItem(RoutineRow row, Instant now) {
        return item("INITIAL", row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(),
                row.businessTypeName(), row.specimenCount() + " 个标本 · " + row.blockCount() + " 个蜡块",
                row.requiredCount() == 0 ? "待生成初始玻片" : "初始制片 " + row.completedCount() + "/"
                        + row.requiredCount() + " 张玻片",
                row.requiredCount(), row.completedCount(), row.enteredAt(), row.currentOperator(),
                caseCenterLink(row.caseId(), "production", null, null), Set.of("OPEN", "CREATE_SLIDE", "PRINT", "COMPLETE", "SCAN",
                        "REPRINT", "RECORD_EXCEPTION"), null, null, null, null, null, now);
    }

    private static ProductionItem cytologyItem(CytologyRow row, Instant now) {
        return item("CYTOLOGY", row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(),
                row.businessTypeName(), row.specimenCount() + " 个标本", "直接玻片 " + row.completedCount() + "/"
                        + row.specimenCount() + " 个标本已完成", row.specimenCount(), row.completedCount(),
                row.enteredAt(), "制片人员", caseCenterLink(row.caseId(), "production", null, null),
                Set.of("OPEN", "CREATE_SLIDE", "PRINT", "COMPLETE", "SCAN", "RECORD_EXCEPTION"), null, null,
                null, null, null, now);
    }

    private static ProductionItem frozenItem(FrozenRow row, Instant now) {
        return item("FROZEN_ROUND", row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(),
                row.businessTypeName(), "第 " + row.roundNo() + " 轮 · " + row.specimenCount() + " 个标本",
                row.requiredCount() == 0 ? "待建立冰冻玻片" : "冰冻玻片 " + row.completedCount() + "/"
                        + row.requiredCount() + " 张完成", row.requiredCount(), row.completedCount(), row.enteredAt(),
                "冰冻制片", caseCenterLink(row.caseId(), "frozen", row.roundId(), row.roundId()),
                Set.of("OPEN", "CREATE_SLIDE", "PRINT", "COMPLETE", "SCAN"), null, null, row.roundId(), null,
                null, now);
    }

    private static List<ProductionItem> technicalItems(List<TechnicalRow> rows, Instant now) {
        Map<UUID, List<TechnicalRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(TechnicalRow::orderId, LinkedHashMap::new, Collectors.toList()));
        List<ProductionItem> result = new ArrayList<>();
        for (List<TechnicalRow> orderRows : grouped.values()) {
            TechnicalRow first = orderRows.get(0);
            int required = orderRows.stream().mapToInt(TechnicalRow::requiredCount).sum();
            int completed = orderRows.stream().mapToInt(row -> Math.min(row.completedCount(), row.requiredCount())).sum();
            if (required > 0 && completed >= required) continue;
            String projects = orderRows.stream().map(TechnicalRow::projectName).distinct()
                    .collect(Collectors.joining("、"));
            String task = "医嘱 " + first.orderNo() + " · " + projects + " · " + completed + "/" + required
                    + " 项输出";
            result.add(item("TECHNICAL_ORDER", first.caseId(), first.pathologyNo(), first.patientReference(),
                    first.businessTypeCode(), first.businessTypeName(), "医嘱 " + first.orderNo(), task, required,
                    completed, first.enteredAt(), "技术人员",
                    caseCenterLink(first.caseId(), "technical-order", first.orderId(), null),
                    Set.of("OPEN", "EXECUTE", "COMPLETE", "ENTER_RESULT", "RECORD_EXCEPTION"), first.orderId(),
                    first.orderNo(), null, null, null, now));
        }
        return result;
    }

    private static ProductionItem incompleteSlideItem(SlideRow row, Instant now) {
        String deepLink = caseCenterLink(row.caseId(),
                "FROZEN_ROUND".equals(row.productionContext()) ? "frozen" : "production",
                row.slideCode(), "FROZEN_ROUND".equals(row.productionContext()) ? row.productionContextId() : null);
        return item(row.productionContext(), row.caseId(), row.pathologyNo(), row.patientReference(),
                row.businessTypeCode(), row.businessTypeName(), row.materialCode() + " · " + row.slideCode(),
                "待完成 " + row.slideType() + " 玻片", 1, 0, row.enteredAt(), "制片人员", deepLink,
                Set.of("OPEN", "PRINT", "COMPLETE", "SCAN", "REPRINT", "RECORD_EXCEPTION"), null, null,
                row.productionContextId(), row.slideCode(), row.slideType(), now);
    }

    private static ProductionItem exceptionItem(ExceptionRow row, Instant now) {
        return item(row.productionContext(), row.caseId(), row.pathologyNo(), row.patientReference(),
                row.businessTypeCode(), row.businessTypeName(), row.materialCode() + " · "
                        + (row.slideCode() == null ? "" : row.slideCode()),
                row.exceptionType() + " · " + row.exceptionNote(), 0, 0, row.occurredAt(),
                row.operatorReference() == null ? "制片人员" : row.operatorReference(),
                caseCenterLink(row.caseId(), "production", row.slideCode(), null), Set.of("OPEN", "RECORD_EXCEPTION"), null, null, null,
                row.slideCode(), null, now);
    }

    private static String caseCenterLink(UUID caseId, String focus, Object focusId, Object roundId) {
        StringBuilder link = new StringBuilder("/v2/cases/").append(caseId).append("?focus=").append(focus);
        if (focusId != null) link.append("&focusId=").append(focusId);
        if (roundId != null) link.append("&roundId=").append(roundId);
        return link.toString();
    }

    private static ProductionItem item(String context, UUID caseId, String pathologyNo, String patientReference,
            String businessTypeCode, String businessTypeName, String materialSummary, String taskSummary,
            int requiredCount, int completedCount, Instant enteredAt, String currentOperator, String deepLink,
            Set<String> actions, UUID orderId, String orderNo, UUID contextId, String slideCode, String slideType,
            Instant now) {
        return new ProductionItem(context, caseId, pathologyNo, patientReference, businessTypeCode, businessTypeName,
                materialSummary, taskSummary, requiredCount, completedCount, enteredAt,
                Math.max(0, Duration.between(enteredAt == null ? now : enteredAt, now).toMinutes()),
                currentOperator, deepLink, Set.copyOf(new LinkedHashSet<>(actions)), orderId, orderNo, contextId,
                slideCode, slideType);
    }

    public record ProductionWorkbenchResult(Instant refreshedAt, Queues queues) { }

    public record Queues(QueueView routineProduction, QueueView cytologyProduction, QueueView frozenProduction,
            QueueView technicalOrders, QueueView incompleteSlides, QueueView exceptions) { }

    public record QueueView(String code, String label, int count, List<ProductionItem> items) { }

    public record ProductionItem(String productionContext, UUID caseId, String pathologyNo,
            String patientReference, String businessTypeCode, String businessTypeName, String materialSummary,
            String taskSummary, int requiredCount, int completedCount, Instant enteredAt, long waitingMinutes,
            String currentOperator, String deepLink, Set<String> availableActions, UUID orderId, String orderNo,
            UUID productionContextId, String slideCode, String slideType) { }
}

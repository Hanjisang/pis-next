package com.hanjisang.pis.v2.technical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.CancelOrderCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.CreateItemCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.CreateProjectCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.CreateTechnicalOrderCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.EnterResultCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.TargetCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.TechnicalConsumptionCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.TechnicalFeeStatusCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.TechnicalLabelPrintCommand;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService.TechnicalQualityCommand;
import com.hanjisang.pis.v2.technical.domain.TechnicalTargetType;

@RestController
@RequestMapping("/api/v2")
public class V2TechnicalOrderController {

    private final V2TechnicalOrderApplicationService service;

    public V2TechnicalOrderController(V2TechnicalOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping("/technical-projects")
    public List<V2TechnicalOrderApplicationService.ProjectResult> projects(
            @RequestParam(required = false) UUID caseId) {
        return service.listProjects(caseId);
    }

    @PostMapping("/technical-projects")
    public V2TechnicalOrderApplicationService.ProjectResult createProject(@RequestBody CreateProjectRequest request) {
        return service.createProject(new CreateProjectCommand(request.businessTypeId(), request.projectCode(),
                request.projectName(), request.capabilityCode(), request.outputTypeCode(), request.enabled(),
                request.allowedTargetTypes(), request.producesSlide(), request.producesBlock(),
                request.producesStructuredResult(), request.requiresResult(), request.deviceTypeCode(),
                request.consumableRequired(), request.defaultSlideType(), request.parametersSchema(),
                request.resultSchema(), request.feeMapping(), request.displayConfiguration(),
                request.requiredBeforeSignOutDefault(), request.configurationVersion()));
    }

    @PostMapping("/technical-orders")
    public V2TechnicalOrderApplicationService.TechnicalOrderResult createOrder(@RequestBody CreateOrderRequest request) {
        List<CreateItemCommand> items = request.items() == null ? List.of() : request.items().stream()
                .map(item -> new CreateItemCommand(item.projectId(), item.quantity(), item.parameters(), item.note(),
                        item.targets() == null ? List.of() : item.targets().stream()
                                .map(target -> new TargetCommand(target.targetType(), target.targetId())).toList()))
                .toList();
        return service.createOrder(new CreateTechnicalOrderCommand(request.diagnosisId(), request.requiredBeforeSignOut(),
                items, request.idempotencyKey()));
    }

    @GetMapping("/technical-orders/{orderId}")
    public V2TechnicalOrderApplicationService.TechnicalOrderResult getOrder(@PathVariable UUID orderId) {
        return service.getOrder(orderId);
    }

    @GetMapping("/diagnoses/{diagnosisId}/technical-orders")
    public List<V2TechnicalOrderApplicationService.TechnicalOrderResult> diagnosisOrders(
            @PathVariable UUID diagnosisId) {
        return service.diagnosisOrders(diagnosisId);
    }

    @PostMapping("/technical-orders/{orderId}/execute")
    public V2TechnicalOrderApplicationService.TechnicalOrderResult execute(@PathVariable UUID orderId,
            @RequestBody IdempotencyRequest request) {
        return service.executeOrder(orderId, request.idempotencyKey());
    }

    @PostMapping("/technical-orders/{orderId}/cancel")
    public V2TechnicalOrderApplicationService.TechnicalOrderResult cancel(@PathVariable UUID orderId,
            @RequestBody CancelRequest request) {
        return service.cancelOrder(orderId, new CancelOrderCommand(request.expectedVersion(), request.reason(),
                request.idempotencyKey()));
    }

    @PostMapping("/technical-order-items/{itemId}/result")
    public V2TechnicalOrderApplicationService.TechnicalOrderResult result(@PathVariable UUID itemId,
            @RequestBody ResultRequest request) {
        return service.enterResult(itemId, new EnterResultCommand(request.resultData(), request.expectedVersion(),
                request.idempotencyKey()));
    }

    @PostMapping("/technical-order-items/{itemId}/acknowledge")
    public V2TechnicalOrderApplicationService.TechnicalAcknowledgement acknowledge(@PathVariable UUID itemId) {
        return service.acknowledgeResult(itemId);
    }

    @PostMapping("/technical-order-items/{itemId}/quality")
    public V2TechnicalOrderApplicationService.TechnicalQualityResult quality(@PathVariable UUID itemId,
            @RequestBody QualityRequest request) {
        return service.evaluateQuality(itemId, new TechnicalQualityCommand(request.outputId(), request.resultCode(),
                request.score(), request.note()));
    }

    @PostMapping("/technical-order-items/{itemId}/fee-status")
    public V2TechnicalOrderApplicationService.TechnicalFeeStatusResult feeStatus(@PathVariable UUID itemId,
            @RequestBody FeeStatusRequest request) {
        return service.updateFeeStatus(itemId, new TechnicalFeeStatusCommand(request.statusCode(),
                request.externalReference(), request.failureReason()));
    }

    @PostMapping("/technical-order-items/{itemId}/consumption")
    public V2TechnicalOrderApplicationService.TechnicalConsumptionResult consumption(@PathVariable UUID itemId,
            @RequestBody ConsumptionRequest request) {
        return service.recordConsumption(itemId, new TechnicalConsumptionCommand(request.consumableBatchId(),
                request.quantity(), request.unitCode(), request.reason()));
    }

    @PostMapping("/technical-order-items/{itemId}/label")
    public V2TechnicalOrderApplicationService.TechnicalLabelPrintResult label(@PathVariable UUID itemId,
            @RequestBody LabelRequest request) {
        return service.printLabel(itemId, new TechnicalLabelPrintCommand(request.outputId(), request.reason(),
                request.idempotencyKey()));
    }

    @GetMapping("/technical-workbench")
    public V2TechnicalOrderApplicationService.WorkbenchResult workbench() {
        return service.workbench();
    }

    public record CreateProjectRequest(UUID businessTypeId, String projectCode, String projectName,
            String capabilityCode, String outputTypeCode, boolean enabled, String allowedTargetTypes,
            boolean producesSlide, boolean producesBlock, boolean producesStructuredResult, boolean requiresResult,
            String deviceTypeCode, boolean consumableRequired, String defaultSlideType, String parametersSchema,
            String resultSchema, String feeMapping, String displayConfiguration,
            boolean requiredBeforeSignOutDefault, int configurationVersion) { }

    public record CreateOrderRequest(UUID diagnosisId, Boolean requiredBeforeSignOut, List<ItemRequest> items,
            String idempotencyKey) { }
    public record ItemRequest(UUID projectId, Integer quantity, String parameters, String note,
            List<TargetRequest> targets) { }
    public record TargetRequest(TechnicalTargetType targetType, UUID targetId) { }
    public record IdempotencyRequest(String idempotencyKey) { }
    public record CancelRequest(long expectedVersion, String reason, String idempotencyKey) { }
    public record ResultRequest(String resultData, long expectedVersion, String idempotencyKey) { }
    public record QualityRequest(UUID outputId, String resultCode, java.math.BigDecimal score, String note) { }
    public record FeeStatusRequest(String statusCode, String externalReference, String failureReason) { }
    public record ConsumptionRequest(UUID consumableBatchId, java.math.BigDecimal quantity, String unitCode,
            String reason) { }
    public record LabelRequest(UUID outputId, String reason, String idempotencyKey) { }
}

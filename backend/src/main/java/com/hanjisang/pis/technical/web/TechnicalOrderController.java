package com.hanjisang.pis.technical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.AddProjectCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.AssignCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.BindTargetCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.CancelCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.CreateOrderCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.PlannedOutputCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ProjectCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ResultCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ReviewCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.VersionCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.OrderResult;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ProjectResult;

@RestController
@RequestMapping("/api/p18")
public class TechnicalOrderController {

    private final TechnicalOrderApplicationService service;

    public TechnicalOrderController(TechnicalOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    public List<?> orders() { return service.orders(); }

    @GetMapping("/orders/{orderId}")
    public OrderResult order(@PathVariable UUID orderId) { return service.order(orderId); }

    @PostMapping("/orders")
    public OrderResult create(@RequestBody CreateOrderRequest request) {
        return service.createOrder(new CreateOrderCommand(request.caseId(), request.orderKindCode(), request.priorityCode(),
                request.reasonText(), request.representedActorRef(), request.projects(), request.idempotencyKey()));
    }

    @PostMapping("/orders/{orderId}/projects")
    public ProjectResult addProject(@PathVariable UUID orderId, @RequestBody AddProjectRequest request) {
        return service.addProject(orderId, new AddProjectCommand(request.project(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/target")
    public ProjectResult target(@PathVariable UUID projectId, @RequestBody TargetRequest request) {
        return service.bindTarget(projectId, new BindTargetCommand(request.actualBlockFormationId(), request.reasonText(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/planned-outputs")
    public ProjectResult plannedOutput(@PathVariable UUID projectId, @RequestBody PlannedOutputRequest request) {
        return service.addPlannedOutput(projectId, new PlannedOutputCommand(request.sequenceNo(), request.outputKindCode(),
                request.slidePurposeCode(), request.plannedLayerReference(), request.plannedQuantity(),
                request.plannedStainProjectCode(), request.plannedUsageCode(), request.plannedLabelQuantity(), request.executionNote(),
                request.idempotencyKey()));
    }

    @PostMapping("/orders/{orderId}/submit")
    public OrderResult submit(@PathVariable UUID orderId, @RequestBody VersionRequest request) {
        return service.submit(orderId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/review")
    public ProjectResult review(@PathVariable UUID projectId, @RequestBody ReviewRequest request) {
        return service.review(projectId, new ReviewCommand(request.decisionCode(), request.reasonText(), request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/receive")
    public ProjectResult receive(@PathVariable UUID projectId, @RequestBody VersionRequest request) {
        return service.receive(projectId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/assign")
    public ProjectResult assign(@PathVariable UUID projectId, @RequestBody AssignRequest request) {
        return service.assign(projectId, new AssignCommand(request.assignedActorRef(), request.expectedVersion(), request.idempotencyKey(), request.reasonText()));
    }

    @PostMapping("/projects/{projectId}/takeover")
    public ProjectResult takeover(@PathVariable UUID projectId, @RequestBody VersionRequest request) {
        return service.takeover(projectId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/execution-handoff")
    public ProjectResult handoff(@PathVariable UUID projectId, @RequestBody VersionRequest request) {
        return service.handoff(projectId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/result-reference")
    public ProjectResult result(@PathVariable UUID projectId, @RequestBody ResultRequest request) {
        return service.referenceResult(projectId, new ResultCommand(request.resultReferenceKindCode(), request.resultIdentity(),
                request.resultDigest(), request.resultEnvironmentCode(), request.note(), request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/close")
    public ProjectResult close(@PathVariable UUID projectId, @RequestBody VersionRequest request) {
        return service.closeProject(projectId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/projects/{projectId}/cancel")
    public ProjectResult cancel(@PathVariable UUID projectId, @RequestBody CancelRequest request) {
        return service.cancel(projectId, new CancelCommand(request.cancellationKindCode(), request.reasonText(), request.impactSummary(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    public record CreateOrderRequest(UUID caseId, String orderKindCode, String priorityCode, String reasonText,
            String representedActorRef, List<ProjectCommand> projects, String idempotencyKey) { }
    public record AddProjectRequest(ProjectCommand project, String idempotencyKey) { }
    public record TargetRequest(UUID actualBlockFormationId, String reasonText, String idempotencyKey) { }
    public record PlannedOutputRequest(int sequenceNo, String outputKindCode, String slidePurposeCode,
            String plannedLayerReference, int plannedQuantity, String plannedStainProjectCode, String plannedUsageCode,
            int plannedLabelQuantity, String executionNote, String idempotencyKey) { }
    public record VersionRequest(long expectedVersion, String idempotencyKey) { }
    public record ReviewRequest(String decisionCode, String reasonText, long expectedVersion, String idempotencyKey) { }
    public record AssignRequest(String assignedActorRef, long expectedVersion, String idempotencyKey, String reasonText) { }
    public record ResultRequest(String resultReferenceKindCode, String resultIdentity, String resultDigest,
            String resultEnvironmentCode, String note, long expectedVersion, String idempotencyKey) { }
    public record CancelRequest(String cancellationKindCode, String reasonText, String impactSummary, long expectedVersion,
            String idempotencyKey) { }
}

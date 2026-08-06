package com.hanjisang.pis.technical.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.technical.application.GrossingApplicationService;
import com.hanjisang.pis.technical.application.GrossingApplicationService.AssignSampleCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.CreateBatchCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.CreateBlockCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.GenerateLabelCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.GrossingRecordCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.PrintCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.PrintResultCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.SampleCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.TakeoverCommand;
import com.hanjisang.pis.technical.application.GrossingApplicationService.TransitionCommand;

@RestController
@RequestMapping("/api/p16")
public class GrossingController {

    private final GrossingApplicationService service;

    public GrossingController(GrossingApplicationService service) {
        this.service = service;
    }

    @GetMapping("/grossing-queue")
    public List<Map<String, Object>> queue() { return service.queue(); }

    @GetMapping("/grossing-batches/{batchId}")
    public GrossingApplicationService.BatchResult batch(@PathVariable UUID batchId) { return service.batch(batchId); }

    @PostMapping("/grossing-batches")
    public GrossingApplicationService.BatchResult createBatch(@RequestBody CreateBatchRequest request) {
        return service.createBatch(new CreateBatchCommand(request.specimenId(), request.specimenNo(), request.caseNo(),
                request.patientIdentityReference(), request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/takeover")
    public GrossingApplicationService.BatchResult takeover(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.takeover(batchId, new TakeoverCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/specimens")
    public GrossingApplicationService.BatchResult addSpecimen(@PathVariable UUID batchId,
            @RequestBody AddSpecimenRequest request) {
        return service.addSpecimen(batchId, new GrossingApplicationService.AddSpecimenCommand(request.specimenId(),
                request.specimenNo(), request.caseNo(), request.patientIdentityReference(), request.expectedVersion(),
                request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/start")
    public GrossingApplicationService.BatchResult start(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.transition(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()),
                GrossingApplicationServiceTarget.IN_PROGRESS, "P14-PERM-013");
    }

    @PostMapping("/grossing-batches/{batchId}/pause")
    public GrossingApplicationService.BatchResult pause(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.transition(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()),
                GrossingApplicationServiceTarget.PAUSED, "P14-PERM-013");
    }

    @PostMapping("/grossing-batches/{batchId}/resume")
    public GrossingApplicationService.BatchResult resume(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.transition(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()),
                GrossingApplicationServiceTarget.IN_PROGRESS, "P14-PERM-013");
    }

    @PostMapping("/grossing-batches/{batchId}/records")
    public GrossingApplicationService.RecordResult record(@PathVariable UUID batchId,
            @RequestBody GrossingRecordRequest request) {
        return service.recordGrossing(batchId, new GrossingRecordCommand(request.specimenId(), request.specimenNo(),
                request.caseNo(), request.patientIdentityReference(), request.identityVerified(),
                request.patientIdentityVerified(), request.grossAppearance(), request.grossDescription(),
                request.quantity(), request.quantityUnitCode(), request.correctionReason(), request.reviewActorReference(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/samples")
    public GrossingApplicationService.SampleResult sample(@PathVariable UUID batchId,
            @RequestBody SampleRequest request) {
        return service.addSample(batchId, new SampleCommand(request.specimenId(), request.sourceSite(),
                request.description(), request.quantity(), request.unit(), request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/blocks")
    public GrossingApplicationService.BlockResult block(@PathVariable UUID batchId,
            @RequestBody BlockRequest request) {
        return service.createBlock(batchId, new CreateBlockCommand(request.specimenId(), request.blockKindCode(),
                request.sourceMaterialKindCode(), request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/blocks/{blockId}/samples")
    public GrossingApplicationService.AssignmentResult assign(@PathVariable UUID blockId,
            @RequestBody AssignRequest request) {
        return service.assignSample(blockId, new AssignSampleCommand(request.sampleId(), request.expectedVersion(),
                request.idempotencyKey()));
    }

    @PostMapping("/blocks/{blockId}/labels")
    public GrossingApplicationService.LabelResult label(@PathVariable UUID blockId,
            @RequestBody LabelRequest request) {
        return service.generateLabel(blockId, new GenerateLabelCommand(request.idempotencyKey()));
    }

    @PostMapping("/labels/{labelId}/print")
    public GrossingApplicationService.PrintResult print(@PathVariable UUID labelId,
            @RequestBody PrintRequest request) {
        return service.submitPrint(labelId, new PrintCommand(request.idempotencyKey(), request.reason()), false);
    }

    @PostMapping("/labels/{labelId}/reprint")
    public GrossingApplicationService.PrintResult reprint(@PathVariable UUID labelId,
            @RequestBody PrintRequest request) {
        return service.submitPrint(labelId, new PrintCommand(request.idempotencyKey(), request.reason()), true);
    }

    @PostMapping("/labels/{labelId}/void")
    public Map<String, Object> voidLabel(@PathVariable UUID labelId, @RequestBody ReasonRequest request) {
        return service.voidLabel(labelId, request.reason(), request.idempotencyKey());
    }

    @PostMapping("/label-print-results")
    public GrossingApplicationService.PrintResult printResult(@RequestBody PrintResultRequest request) {
        return service.recordPrintResult(new PrintResultCommand(request.requestId(), request.labelId(),
                request.outcomeCode(), request.note(), request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/complete")
    public GrossingApplicationService.BatchResult complete(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.complete(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/grossing-batches/{batchId}/handoff")
    public GrossingApplicationService.BatchResult handoff(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.transition(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()),
                GrossingApplicationServiceTarget.HANDED_OFF, "P14-PERM-013");
    }

    @PostMapping("/grossing-batches/{batchId}/cancel")
    public GrossingApplicationService.BatchResult cancel(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.transition(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()),
                GrossingApplicationServiceTarget.CANCELLED, "P14-PERM-013");
    }

    @PostMapping("/grossing-batches/{batchId}/terminate")
    public GrossingApplicationService.BatchResult terminate(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.transition(batchId, new TransitionCommand(request.expectedVersion(), request.idempotencyKey()),
                GrossingApplicationServiceTarget.TERMINATED, "P14-PERM-013");
    }

    private static final class GrossingApplicationServiceTarget {
        private static final String IN_PROGRESS = "P16-GROSSING-IN-PROGRESS";
        private static final String PAUSED = "P16-GROSSING-PAUSED";
        private static final String HANDED_OFF = "P16-GROSSING-HANDED-OFF";
        private static final String CANCELLED = "P16-GROSSING-CANCELLED";
        private static final String TERMINATED = "P16-GROSSING-TERMINATED";
    }

    public record VersionRequest(long expectedVersion, String idempotencyKey) { }
    public record CreateBatchRequest(UUID specimenId, String specimenNo, String caseNo,
            String patientIdentityReference, String idempotencyKey) { }
    public record AddSpecimenRequest(UUID specimenId, String specimenNo, String caseNo,
            String patientIdentityReference, long expectedVersion, String idempotencyKey) { }
    public record GrossingRecordRequest(UUID specimenId, String specimenNo, String caseNo,
            String patientIdentityReference, boolean identityVerified, boolean patientIdentityVerified,
            String grossAppearance, String grossDescription, double quantity, String quantityUnitCode,
            String correctionReason, String reviewActorReference, long expectedVersion, String idempotencyKey) { }
    public record SampleRequest(UUID specimenId, String sourceSite, String description, double quantity, String unit,
            long expectedVersion, String idempotencyKey) { }
    public record BlockRequest(UUID specimenId, String blockKindCode, String sourceMaterialKindCode,
            long expectedVersion, String idempotencyKey) { }
    public record AssignRequest(UUID sampleId, long expectedVersion, String idempotencyKey) { }
    public record LabelRequest(String idempotencyKey) { }
    public record PrintRequest(String idempotencyKey, String reason) { }
    public record ReasonRequest(String reason, String idempotencyKey) { }
    public record PrintResultRequest(UUID requestId, UUID labelId, String outcomeCode, String note,
            String idempotencyKey) { }
}

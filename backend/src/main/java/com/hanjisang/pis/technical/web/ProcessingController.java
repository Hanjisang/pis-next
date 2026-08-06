package com.hanjisang.pis.technical.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.technical.application.ProcessingApplicationService;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.AddMemberCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CompleteEmbeddingCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ConfirmResultCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CreateBatchCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CreateEmbeddingCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CreateTaskCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.DeviceCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.FailureCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ImpactCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.InterruptCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ProgramCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.RawResultCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.RecoveryCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ReprocessCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.RequirementCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ReworkCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.StepCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.VersionCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.VoidFormationCommand;

@RestController
@RequestMapping("/api/p17")
public class ProcessingController {

    private final ProcessingApplicationService service;

    public ProcessingController(ProcessingApplicationService service) {
        this.service = service;
    }

    @GetMapping("/processing-queue")
    public List<Map<String, Object>> processingQueue() { return service.processingQueue(); }

    @GetMapping("/embedding-queue")
    public List<Map<String, Object>> embeddingQueue() { return service.embeddingQueue(); }

    @GetMapping("/processing-tasks/{taskId}")
    public ProcessingApplicationService.TaskResult task(@PathVariable UUID taskId) { return service.task(taskId); }

    @PostMapping("/processing-tasks")
    public ProcessingApplicationService.TaskResult createTask(@RequestBody CreateTaskRequest request) {
        return service.createTask(new CreateTaskCommand(request.tissueBlockId(), request.idempotencyKey()));
    }

    @PostMapping("/processing-tasks/{taskId}/takeover")
    public ProcessingApplicationService.TaskResult takeoverTask(@PathVariable UUID taskId,
            @RequestBody VersionRequest request) {
        return service.takeoverTask(taskId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/processing-tasks/{taskId}/batches")
    public ProcessingApplicationService.BatchResult createBatch(@PathVariable UUID taskId,
            @RequestBody CreateBatchRequest request) {
        return service.createBatch(new CreateBatchCommand(taskId, request.programCode(), request.versionLabel(),
                request.executionMode(), request.deviceIdentity(), request.idempotencyKey()));
    }

    @GetMapping("/processing-batches/{batchId}")
    public ProcessingApplicationService.BatchResult batch(@PathVariable UUID batchId) { return service.batch(batchId); }

    @PostMapping("/processing-batches/{batchId}/program")
    public ProcessingApplicationService.BatchResult program(@PathVariable UUID batchId,
            @RequestBody ProgramRequest request) {
        return service.selectProgram(batchId, new ProgramCommand(request.programCode(), request.versionLabel(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/execution")
    public ProcessingApplicationService.BatchResult execution(@PathVariable UUID batchId,
            @RequestBody DeviceRequest request) {
        return service.assignDevice(batchId, new DeviceCommand(request.executionMode(), request.deviceIdentity(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/members")
    public ProcessingApplicationService.MemberResult member(@PathVariable UUID batchId,
            @RequestBody MemberRequest request) {
        return service.addMember(batchId, new AddMemberCommand(request.tissueBlockId(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/start")
    public ProcessingApplicationService.RunResult start(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.startBatch(batchId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/steps")
    public ProcessingApplicationService.StepResult step(@PathVariable UUID batchId,
            @RequestBody StepRequest request) {
        return service.recordStep(batchId, new StepCommand(request.runId(), request.sequence(), request.stepCode(),
                request.stateCode(), request.observedReference(), request.idempotencyKey()));
    }

    @PostMapping("/processing-runs/raw-results")
    public ProcessingApplicationService.RawResultResult rawResult(@RequestBody RawResultRequest request) {
        return service.receiveRawResult(new RawResultCommand(request.runId(), request.externalMessageId(),
                request.payloadDigest(), request.rawStateCode(), request.payloadReference(), request.deviceOccurredAt(),
                request.idempotencyKey()));
    }

    @PostMapping("/processing-runs/results")
    public ProcessingApplicationService.ResultResult result(@RequestBody ConfirmResultRequest request) {
        return service.confirmResult(new ConfirmResultCommand(request.runId(), request.memberId(), request.resultStateCode(),
                request.canEnterEmbedding(), request.summary(), request.expectedMemberVersion(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/interrupt")
    public ProcessingApplicationService.BatchResult interrupt(@PathVariable UUID batchId,
            @RequestBody FailureRequest request) {
        return service.interruptBatch(batchId, new InterruptCommand(request.expectedVersion(), request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/fail")
    public ProcessingApplicationService.BatchResult fail(@PathVariable UUID batchId,
            @RequestBody FailureRequest request) {
        return service.failBatch(batchId, new FailureCommand(request.expectedVersion(), request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/processing-members/impact")
    public ProcessingApplicationService.ImpactResult impact(@RequestBody ImpactRequest request) {
        return service.decideImpact(new ImpactCommand(request.memberId(), request.impactStateCode(), request.canContinue(),
                request.requiresReprocess(), request.isolationRequired(), request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/processing-exceptions/recovery")
    public ProcessingApplicationService.RecoveryResult recovery(@RequestBody RecoveryRequest request) {
        return service.recover(new RecoveryCommand(request.exceptionId(), request.recoveryKindCode(), request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/processing-members/reprocess")
    public ProcessingApplicationService.TaskResult reprocess(@RequestBody ReprocessRequest request) {
        return service.requestReprocess(new ReprocessCommand(request.memberId(), request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/processing-batches/{batchId}/complete")
    public ProcessingApplicationService.BatchResult completeBatch(@PathVariable UUID batchId,
            @RequestBody VersionRequest request) {
        return service.completeBatch(batchId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/embedding-tasks")
    public ProcessingApplicationService.EmbeddingResult createEmbedding(@RequestBody CreateEmbeddingRequest request) {
        return service.createEmbeddingTask(new CreateEmbeddingCommand(request.tissueBlockId(), request.processingResultId(),
                request.reworkOfFormationId(), request.idempotencyKey()));
    }

    @GetMapping("/embedding-tasks/{taskId}")
    public ProcessingApplicationService.EmbeddingResult embeddingTask(@PathVariable UUID taskId) {
        return service.embeddingTask(taskId);
    }

    @PostMapping("/embedding-tasks/{taskId}/takeover")
    public ProcessingApplicationService.EmbeddingResult takeoverEmbedding(@PathVariable UUID taskId,
            @RequestBody VersionRequest request) {
        return service.takeoverEmbeddingTask(taskId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/embedding-tasks/{taskId}/start")
    public ProcessingApplicationService.EmbeddingResult startEmbedding(@PathVariable UUID taskId,
            @RequestBody VersionRequest request) {
        return service.startEmbedding(taskId, new VersionCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/embedding-tasks/{taskId}/requirements")
    public ProcessingApplicationService.EmbeddingResult requirements(@PathVariable UUID taskId,
            @RequestBody RequirementRequest request) {
        return service.recordEmbeddingRequirements(taskId, new RequirementCommand(request.requirementSnapshot(),
                request.orientationReference(), request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/embedding-tasks/{taskId}/complete")
    public ProcessingApplicationService.FormationResult completeEmbedding(@PathVariable UUID taskId,
            @RequestBody CompleteEmbeddingRequest request) {
        return service.completeEmbedding(new CompleteEmbeddingCommand(taskId, request.expectedTaskVersion(),
                request.expectedBlockVersion(), request.replacementReason(), request.idempotencyKey()));
    }

    @PostMapping("/actual-blocks/{formationId}/rework")
    public ProcessingApplicationService.EmbeddingResult rework(@PathVariable UUID formationId,
            @RequestBody ReasonRequest request) {
        return service.requestEmbeddingRework(new ReworkCommand(formationId, request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/actual-blocks/{formationId}/void")
    public ProcessingApplicationService.FormationResult voidActualBlock(@PathVariable UUID formationId,
            @RequestBody VoidRequest request) {
        return service.voidActualBlock(new VoidFormationCommand(formationId, request.expectedBlockVersion(), request.reason(), request.idempotencyKey()));
    }

    @GetMapping("/actual-blocks/{formationId}")
    public ProcessingApplicationService.FormationResult actualBlock(@PathVariable UUID formationId) {
        return service.actualBlock(formationId);
    }

    public record CreateTaskRequest(UUID tissueBlockId, String idempotencyKey) { }
    public record VersionRequest(long expectedVersion, String idempotencyKey) { }
    public record CreateBatchRequest(String programCode, String versionLabel, String executionMode,
            String deviceIdentity, String idempotencyKey) { }
    public record ProgramRequest(String programCode, String versionLabel, long expectedVersion, String idempotencyKey) { }
    public record DeviceRequest(String executionMode, String deviceIdentity, long expectedVersion, String idempotencyKey) { }
    public record MemberRequest(UUID tissueBlockId, String idempotencyKey) { }
    public record StepRequest(UUID runId, int sequence, String stepCode, String stateCode, String observedReference,
            String idempotencyKey) { }
    public record RawResultRequest(UUID runId, String externalMessageId, String payloadDigest, String rawStateCode,
            String payloadReference, Instant deviceOccurredAt, String idempotencyKey) { }
    public record ConfirmResultRequest(UUID runId, UUID memberId, String resultStateCode, boolean canEnterEmbedding,
            String summary, long expectedMemberVersion, String idempotencyKey) { }
    public record FailureRequest(long expectedVersion, String reason, String idempotencyKey) { }
    public record ImpactRequest(UUID memberId, String impactStateCode, boolean canContinue, boolean requiresReprocess,
            boolean isolationRequired, String reason, String idempotencyKey) { }
    public record RecoveryRequest(UUID exceptionId, String recoveryKindCode, String reason, String idempotencyKey) { }
    public record ReprocessRequest(UUID memberId, String reason, String idempotencyKey) { }
    public record CreateEmbeddingRequest(UUID tissueBlockId, UUID processingResultId, UUID reworkOfFormationId,
            String idempotencyKey) { }
    public record RequirementRequest(String requirementSnapshot, String orientationReference, long expectedVersion,
            String idempotencyKey) { }
    public record CompleteEmbeddingRequest(long expectedTaskVersion, long expectedBlockVersion, String replacementReason,
            String idempotencyKey) { }
    public record ReasonRequest(String reason, String idempotencyKey) { }
    public record VoidRequest(long expectedBlockVersion, String reason, String idempotencyKey) { }
}

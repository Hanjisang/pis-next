package com.hanjisang.pis.technical.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.technical.domain.ActualBlockFormation;
import com.hanjisang.pis.technical.domain.EmbeddingTask;
import com.hanjisang.pis.technical.domain.ProcessingBatch;
import com.hanjisang.pis.technical.domain.ProcessingProgramVersion;
import com.hanjisang.pis.technical.domain.ProcessingTask;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.EmbeddingTaskSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.FormationSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.ProcessingBatchSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.ProcessingMemberSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.ProcessingResultSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.ProcessingRunSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.ProcessingTaskSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.ProgramVersionSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.RawResultSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcProcessingRepository.SourceBlock;

@Service
public class ProcessingApplicationService {

    public static final String TASK = "P17-TECHNICAL-PROCESSING-EMBEDDING";
    private static final String QUEUE_PERMISSION = "P14-PERM-050";
    private static final String BLOCK_PERMISSION = "P14-PERM-014";
    private static final String DEVICE_PERMISSION = "P14-PERM-029";

    private final JdbcProcessingRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final String runtimeEnvironment;

    public ProcessingApplicationService(JdbcProcessingRepository repository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox,
            @Value("${pis.runtime-environment:local}") String runtimeEnvironment) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.runtimeEnvironment = runtimeEnvironment;
    }

    public List<Map<String, Object>> processingQueue() {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        return repository.processingQueue(actor.hospitalScope());
    }

    public List<Map<String, Object>> embeddingQueue() {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        return repository.embeddingQueue(actor.hospitalScope());
    }

    public TaskResult task(UUID taskId) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        return taskResult(requireTask(taskId, actor), false);
    }

    public BatchResult batch(UUID batchId) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        return batchResult(requireBatch(batchId, actor), false);
    }

    public EmbeddingResult embeddingTask(UUID taskId) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        return embeddingResult(requireEmbeddingTask(taskId, actor), false);
    }

    public FormationResult actualBlock(UUID formationId) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        FormationSnapshot formation = repository.formation(formationId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-032", "实际蜡块形成事实不存在或不在当前范围"));
        return formationResult(formation, false);
    }

    @Transactional
    public TaskResult createTask(CreateTaskCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        SourceBlock block = requireAdmission(command.tissueBlockId(), actor);
        String digest = command.tissueBlockId().toString();
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-CREATE-TASK",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return taskResult(requireTask(replay.get().resultObjectId(), actor), true);
        Optional<ProcessingTaskSnapshot> existing = repository.taskByBlock(block.blockId(), actor.hospitalScope());
        if (existing.isPresent()) return taskResult(existing.get(), true);
        ProcessingTaskSnapshot task = repository.createTask(block, "P17-TASK-" + token(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-CREATE-TASK", command.idempotencyKey(), digest, task.id(), actor.actorId(), Instant.now());
        audit(task.id(), "P17-CMD-CREATE-PROCESSING-TASK", QUEUE_PERMISSION, actor, "processing task created");
        outbox.append("P17-EVT-PROCESSING-TASK-CREATED", task.id(), "P17-PROCESSING-TASK", task.concurrencyVersion(),
                UUID.randomUUID().toString(), task.taskNo(), actor.actorId());
        return taskResult(task, false);
    }

    @Transactional
    public TaskResult takeoverTask(UUID taskId, VersionCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingTaskSnapshot current = requireTask(taskId, actor);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-TAKEOVER-TASK",
                command.idempotencyKey(), taskId + ":" + command.expectedVersion());
        if (replay.isPresent()) return taskResult(requireTask(taskId, actor), true);
        try {
            ProcessingTask.persisted(taskId, current.stateCode(), current.concurrencyVersion()).transition(ProcessingTask.ASSIGNED);
        } catch (IllegalStateException exception) {
            throw business("P12-ERR-034", "当前组织处理任务不允许接管");
        }
        if (!repository.takeoverTask(taskId, actor.hospitalScope(), actor.actorId(), command.expectedVersion(), Instant.now())) {
            throw versionConflict();
        }
        repository.recordIdempotent("P17-TAKEOVER-TASK", command.idempotencyKey(), taskId + ":" + command.expectedVersion(),
                taskId, actor.actorId(), Instant.now());
        audit(taskId, "P17-CMD-TAKEOVER-PROCESSING-TASK", QUEUE_PERMISSION, actor, "processing task takeover");
        outbox.append("P17-EVT-PROCESSING-TASK-TAKEN-OVER", taskId, "P17-PROCESSING-TASK",
                command.expectedVersion() + 1, UUID.randomUUID().toString(), actor.actorId(), actor.actorId());
        return taskResult(requireTask(taskId, actor), false);
    }

    @Transactional
    public BatchResult createBatch(CreateBatchCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingTaskSnapshot task = requireTask(command.taskId(), actor);
        requireTaskOwner(task, actor);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-CREATE-BATCH",
                command.idempotencyKey(), command.taskId() + ":" + command.programCode() + ":" + command.versionLabel());
        if (replay.isPresent()) return batchResult(requireBatch(replay.get().resultObjectId(), actor), true);
        Optional<ProcessingBatchSnapshot> existing = repository.batchByTask(task.id(), actor.hospitalScope());
        if (existing.isPresent()) return batchResult(existing.get(), true);
        ProgramVersionSnapshot program = requireProgram(command.programCode(), command.versionLabel());
        ensureProgramStartAllowed(program);
        ProcessingBatchSnapshot batch = repository.createBatch(task, "P17-BATCH-" + token(), program,
                defaultText(command.executionMode(), "HUMAN"), command.deviceIdentity(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-CREATE-BATCH", command.idempotencyKey(),
                command.taskId() + ":" + command.programCode() + ":" + command.versionLabel(), batch.id(), actor.actorId(), Instant.now());
        audit(batch.id(), "P17-CMD-CREATE-PROCESSING-BATCH", QUEUE_PERMISSION, actor, "processing batch created");
        outbox.append("P17-EVT-PROCESSING-BATCH-CREATED", batch.id(), "P17-PROCESSING-BATCH", batch.concurrencyVersion(),
                UUID.randomUUID().toString(), batch.batchNo(), actor.actorId());
        return batchResult(batch, false);
    }

    @Transactional
    public BatchResult selectProgram(UUID batchId, ProgramCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        ProgramVersionSnapshot program = requireProgram(command.programCode(), command.versionLabel());
        ensureProgramStartAllowed(program);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-SELECT-PROGRAM",
                command.idempotencyKey(), batchId + ":" + program.id());
        if (replay.isPresent()) return batchResult(requireBatch(batchId, actor), true);
        if (!repository.updateBatchProgram(batchId, actor.hospitalScope(), program.id(), program.versionDigest(),
                command.expectedVersion(), actor.actorId(), Instant.now())) throw versionConflict();
        repository.recordIdempotent("P17-SELECT-PROGRAM", command.idempotencyKey(), batchId + ":" + program.id(),
                batchId, actor.actorId(), Instant.now());
        audit(batchId, "P17-CMD-SELECT-PROCESSING-PROGRAM", QUEUE_PERMISSION, actor, "processing program selected");
        return batchResult(requireBatch(batchId, actor), false);
    }

    @Transactional
    public BatchResult assignDevice(UUID batchId, DeviceCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        if (!"DEVICE".equals(command.executionMode()) && !"HUMAN".equals(command.executionMode())) {
            throw business("P12-ERR-035", "执行方式只能是人工或产品中立设备模式");
        }
        if ("DEVICE".equals(command.executionMode()) && blank(command.deviceIdentity())) {
            throw business("P12-ERR-035", "设备执行必须提供设备身份");
        }
        if (!repository.updateBatchDevice(batchId, actor.hospitalScope(), command.executionMode(), command.deviceIdentity(),
                command.expectedVersion(), Instant.now())) throw versionConflict();
        audit(batchId, "P17-CMD-ASSIGN-EXECUTION-MODE", QUEUE_PERMISSION, actor, "execution mode assigned");
        return batchResult(requireBatch(batchId, actor), false);
    }

    @Transactional
    public MemberResult addMember(UUID batchId, AddMemberCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        if (!ProcessingBatch.PLANNED.equals(batch.stateCode()) && !ProcessingBatch.ASSIGNED.equals(batch.stateCode())) {
            throw business("P12-ERR-034", "处理批次开始后不能普通增加成员");
        }
        SourceBlock block = requireAdmission(command.tissueBlockId(), actor);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-ADD-MEMBER",
                command.idempotencyKey(), batchId + ":" + command.tissueBlockId());
        if (replay.isPresent()) return memberResult(repository.member(replay.get().resultObjectId(), actor.hospitalScope()).orElseThrow(), true);
        Optional<ProcessingMemberSnapshot> same = repository.memberInBatch(batchId, block.blockId(), actor.hospitalScope());
        if (same.isPresent()) return memberResult(same.get(), true);
        if (repository.activeMemberExists(block.blockId(), batchId)) {
            throw business("P12-ERR-035", "同一计划蜡块已进入冲突的组织处理批次");
        }
        ProcessingMemberSnapshot member = repository.addMember(batch, block, actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-ADD-MEMBER", command.idempotencyKey(), batchId + ":" + command.tissueBlockId(),
                member.id(), actor.actorId(), Instant.now());
        audit(member.id(), "P17-CMD-ADD-BATCH-MEMBER", QUEUE_PERMISSION, actor, "processing batch member added");
        outbox.append("P17-EVT-PROCESSING-MEMBER-ADDED", member.id(), "P17-PROCESSING-BATCH-MEMBER",
                member.version(), UUID.randomUUID().toString(), member.plannedBlockNo(), actor.actorId());
        return memberResult(member, false);
    }

    @Transactional
    public RunResult startBatch(UUID batchId, VersionCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        List<ProcessingMemberSnapshot> members = repository.members(batchId, actor.hospitalScope());
        if (members.isEmpty()) throw business("P12-ERR-035", "没有批次成员不能开始组织处理");
        ProgramVersionSnapshot program = batch.programVersionId() == null ? null : repository.programVersion(batch.programVersionId()).orElse(null);
        if (program == null || !"ACTIVE".equals(program.versionStateCode())) {
            throw business("P12-ERR-035", "缺少有效组织处理程序版本");
        }
        ensureProgramStartAllowed(program);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-START-BATCH",
                command.idempotencyKey(), batchId + ":" + command.expectedVersion());
        if (replay.isPresent()) return runResult(repository.runForBatch(batchId, actor.hospitalScope()).orElseThrow(), true);
        try {
            ProcessingBatch.persisted(batchId, batch.stateCode(), batch.concurrencyVersion()).transition(ProcessingBatch.IN_PROGRESS);
        } catch (IllegalStateException exception) {
            throw business("P12-ERR-034", "当前处理批次不允许开始");
        }
        ProcessingRunSnapshot run;
        try {
            run = repository.startBatch(batch, actor.actorId(), Instant.now());
        } catch (IllegalStateException exception) {
            throw versionConflict();
        }
        ProcessingTaskSnapshot task = requireTask(batch.taskId(), actor);
        if (!repository.transitionTask(task.id(), actor.hospitalScope(), task.stateCode(), ProcessingTask.IN_PROGRESS,
                actor.actorId(), task.concurrencyVersion(), Instant.now())) throw versionConflict();
        repository.recordIdempotent("P17-START-BATCH", command.idempotencyKey(), batchId + ":" + command.expectedVersion(),
                run.id(), actor.actorId(), Instant.now());
        audit(batchId, "P17-CMD-START-PROCESSING", QUEUE_PERMISSION, actor, "processing batch started");
        outbox.append("P17-EVT-PROCESSING-BATCH-STARTED", batchId, "P17-PROCESSING-BATCH",
                batch.concurrencyVersion() + 1, UUID.randomUUID().toString(), run.externalRunId(), actor.actorId());
        return runResult(run, false);
    }

    @Transactional
    public StepResult recordStep(UUID batchId, StepCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        ProcessingRunSnapshot run = requireRun(command.runId(), actor);
        if (!run.batchId().equals(batchId)) throw business("P12-ERR-035", "处理运行不属于当前批次");
        if (command.sequence() <= 0 || blank(command.stepCode()) || blank(command.stateCode())) {
            throw business("P12-ERR-035", "处理步骤事实不完整");
        }
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-RECORD-STEP",
                command.idempotencyKey(), command.runId() + ":" + command.sequence() + ":" + command.stateCode());
        if (replay.isPresent()) return new StepResult(replay.get().resultObjectId(), command.sequence(), command.stepCode(), command.stateCode(), true);
        var step = repository.insertStep(command.runId(), command.sequence(), command.stepCode(), command.stateCode(),
                command.observedReference(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-RECORD-STEP", command.idempotencyKey(), command.runId() + ":" + command.sequence() + ":" + command.stateCode(),
                step.id(), actor.actorId(), Instant.now());
        audit(step.id(), "P17-CMD-RECORD-PROCESSING-STEP", QUEUE_PERMISSION, actor, "processing step recorded");
        return new StepResult(step.id(), step.sequence(), step.stepCode(), step.stateCode(), false);
    }

    @Transactional
    public RawResultResult receiveRawResult(RawResultCommand command) {
        ActorContext actor = authorization.requireTask(DEVICE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingRunSnapshot run = requireRun(command.runId(), actor);
        if (blank(command.externalMessageId()) || blank(command.payloadDigest()) || blank(command.rawStateCode())) {
            throw business("P12-ERR-035", "设备原始结果缺少消息标识、摘要或原始状态");
        }
        Optional<RawResultSnapshot> existingMessage = repository.rawResult(command.externalMessageId());
        if (existingMessage.isPresent()) {
            if (!existingMessage.get().payloadDigest().equals(command.payloadDigest())) {
                throw business("P12-ERR-003", "相同设备消息标识对应不同载荷");
            }
            return new RawResultResult(existingMessage.get().id(), run.id(), existingMessage.get().stateCode(), true);
        }
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-RAW-RESULT",
                command.idempotencyKey(), command.runId() + ":" + command.externalMessageId() + ":" + command.payloadDigest());
        if (replay.isPresent()) return new RawResultResult(replay.get().resultObjectId(), run.id(), command.rawStateCode(), true);
        RawResultSnapshot raw = repository.insertRawResult(run.id(), command.externalMessageId(), command.payloadDigest(),
                command.rawStateCode(), command.payloadReference(), command.deviceOccurredAt(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-RAW-RESULT", command.idempotencyKey(), command.runId() + ":" + command.externalMessageId() + ":" + command.payloadDigest(),
                raw.id(), actor.actorId(), Instant.now());
        audit(raw.id(), "P17-CMD-RECEIVE-DEVICE-RAW-RESULT", DEVICE_PERMISSION, actor, "raw result received");
        outbox.append("P17-EVT-PROCESSING-RAW-RESULT-RECEIVED", raw.id(), "P17-PROCESSING-RAW-RESULT", 1,
                UUID.randomUUID().toString(), command.payloadDigest(), actor.actorId());
        return new RawResultResult(raw.id(), run.id(), raw.stateCode(), false);
    }

    @Transactional
    public ResultResult confirmResult(ConfirmResultCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireHuman(actor);
        requireText(command.idempotencyKey());
        ProcessingRunSnapshot run = requireRun(command.runId(), actor);
        ProcessingMemberSnapshot member = repository.member(command.memberId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "处理批次成员不存在或不在范围内"));
        if (!member.batchId().equals(run.batchId())) throw business("P12-ERR-035", "处理结果成员与运行不匹配");
        String digest = command.runId() + ":" + command.memberId() + ":" + command.resultStateCode() + ":" + command.canEnterEmbedding();
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-CONFIRM-RESULT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return resultResult(repository.result(replay.get().resultObjectId(), actor.hospitalScope()).orElseThrow(), true);
        if (!"P17-RESULT-VALIDATED".equals(command.resultStateCode()) && !"P17-RESULT-FAILED".equals(command.resultStateCode())) {
            throw business("P12-ERR-036", "有效处理结果状态不受支持");
        }
        boolean canEnter = command.canEnterEmbedding() && "P17-RESULT-VALIDATED".equals(command.resultStateCode());
        ProcessingResultSnapshot result = repository.confirmResult(run.id(), member.id(), command.resultStateCode(), canEnter,
                requireTextValue(command.summary()), actor.actorId(), Instant.now());
        String memberTarget = canEnter ? "P17-MEMBER-READY-FOR-EMBEDDING" : "P17-MEMBER-FAILED";
        if (!repository.updateMember(member.id(), member.stateCode(), memberTarget, canEnter, command.expectedMemberVersion(), Instant.now())) {
            throw versionConflict();
        }
        repository.recordIdempotent("P17-CONFIRM-RESULT", command.idempotencyKey(), digest, result.id(), actor.actorId(), Instant.now());
        audit(result.id(), "P17-CMD-CONFIRM-PROCESSING-RESULT", QUEUE_PERMISSION, actor, "processing result confirmed");
        outbox.append("P17-EVT-PROCESSING-RESULT-CONFIRMED", result.id(), "P17-PROCESSING-RESULT", result.recordVersion(),
                UUID.randomUUID().toString(), result.stateCode(), actor.actorId());
        return resultResult(result, false);
    }

    @Transactional
    public BatchResult interruptBatch(UUID batchId, InterruptCommand command) {
        return recordBatchFailure(batchId, command.expectedVersion(), command.idempotencyKey(), ProcessingBatch.INTERRUPTED,
                "P07-EXC-020", "SEV-3", command.reason(), "处理中断已定位到当前批次，成员需逐项裁决");
    }

    @Transactional
    public BatchResult failBatch(UUID batchId, FailureCommand command) {
        return recordBatchFailure(batchId, command.expectedVersion(), command.idempotencyKey(), ProcessingBatch.FAILED,
                "P07-EXC-020", "SEV-3", command.reason(), "处理失败批次已隔离，成员结果保留");
    }

    private BatchResult recordBatchFailure(UUID batchId, long expectedVersion, String key, String targetState,
            String exceptionCode, String severity, String reason, String affectedScope) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(key);
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-BATCH-FAILURE",
                key, batchId + ":" + targetState + ":" + expectedVersion);
        if (replay.isPresent()) return batchResult(requireBatch(batchId, actor), true);
        UUID exceptionId = repository.createException(batchId, null, null, exceptionCode, severity, reason, affectedScope,
                actor.actorId(), Instant.now());
        if (!repository.transitionBatch(batchId, actor.hospitalScope(), batch.stateCode(), targetState, actor.actorId(),
                expectedVersion, Instant.now(), exceptionCode)) throw versionConflict();
        ProcessingTaskSnapshot task = requireTask(batch.taskId(), actor);
        String taskTarget = ProcessingBatch.INTERRUPTED.equals(targetState) ? ProcessingTask.INTERRUPTED : ProcessingTask.FAILED;
        repository.transitionTask(task.id(), actor.hospitalScope(), task.stateCode(), taskTarget, actor.actorId(),
                task.concurrencyVersion(), Instant.now());
        repository.recordIdempotent("P17-BATCH-FAILURE", key, batchId + ":" + targetState + ":" + expectedVersion,
                exceptionId, actor.actorId(), Instant.now());
        audit(exceptionId, "P17-CMD-RECORD-PROCESSING-FAILURE", QUEUE_PERMISSION, actor, reason);
        outbox.append("P17-EVT-PROCESSING-INTERRUPTED", exceptionId, "P17-PROCESSING-EXCEPTION",
                expectedVersion + 1, UUID.randomUUID().toString(), exceptionCode, actor.actorId());
        return batchResult(requireBatch(batchId, actor), false);
    }

    @Transactional
    public ImpactResult decideImpact(ImpactCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingMemberSnapshot member = repository.member(command.memberId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "处理成员不存在或不在范围内"));
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-DECIDE-IMPACT",
                command.idempotencyKey(), command.memberId() + ":" + command.impactStateCode());
        if (replay.isPresent()) return new ImpactResult(replay.get().resultObjectId(), command.memberId(), true);
        UUID exceptionId = repository.currentExceptionForBatch(member.batchId())
                .orElseThrow(() -> business("P12-ERR-077", "不存在可裁决的处理中断或失败异常"));
        UUID impactId = repository.recordImpact(exceptionId, member.id(), command.canContinue(), command.requiresReprocess(),
                command.isolationRequired(), command.impactStateCode(), requireTextValue(command.reason()), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-DECIDE-IMPACT", command.idempotencyKey(), command.memberId() + ":" + command.impactStateCode(),
                impactId, actor.actorId(), Instant.now());
        audit(impactId, "P17-CMD-DECIDE-MEMBER-IMPACT", QUEUE_PERMISSION, actor, command.reason());
        return new ImpactResult(impactId, member.id(), false);
    }

    @Transactional
    public RecoveryResult recover(RecoveryCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-RECOVER-EXCEPTION",
                command.idempotencyKey(), command.exceptionId() + ":" + command.recoveryKindCode());
        if (replay.isPresent()) return new RecoveryResult(replay.get().resultObjectId(), command.exceptionId(), true);
        UUID recoveryId = repository.recordRecovery(command.exceptionId(), command.recoveryKindCode(),
                "P17-RECOVERY-APPROVED", requireTextValue(command.reason()), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-RECOVER-EXCEPTION", command.idempotencyKey(), command.exceptionId() + ":" + command.recoveryKindCode(),
                recoveryId, actor.actorId(), Instant.now());
        audit(recoveryId, "P17-CMD-RECOVER-PROCESSING-EXCEPTION", QUEUE_PERMISSION, actor, command.reason());
        outbox.append("P17-EVT-PROCESSING-RECOVERED", recoveryId, "P17-PROCESSING-RECOVERY", 1,
                UUID.randomUUID().toString(), command.recoveryKindCode(), actor.actorId());
        return new RecoveryResult(recoveryId, command.exceptionId(), false);
    }

    @Transactional
    public TaskResult requestReprocess(ReprocessCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingMemberSnapshot member = repository.member(command.memberId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "原处理成员不存在"));
        SourceBlock block = requireAdmissionForReprocess(member.tissueBlockId(), actor);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-REQUEST-REPROCESS",
                command.idempotencyKey(), command.memberId() + ":" + command.reason());
        if (replay.isPresent()) return taskResult(requireTask(replay.get().resultObjectId(), actor), true);
        ProcessingTaskSnapshot replacement = repository.createReprocessTask(block, "P17-REPROCESS-TASK-" + token(),
                actor.actorId(), Instant.now());
        ProcessingRunSnapshot originalRun = repository.runForBatch(member.batchId(), actor.hospitalScope()).orElse(null);
        repository.recordReprocess(nullSafeBatch(member.batchId()), originalRun == null ? null : originalRun.id(), member.id(),
                replacement.id(), requireTextValue(command.reason()), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-REQUEST-REPROCESS", command.idempotencyKey(), command.memberId() + ":" + command.reason(),
                replacement.id(), actor.actorId(), Instant.now());
        audit(replacement.id(), "P17-CMD-REQUEST-REPROCESS", QUEUE_PERMISSION, actor, command.reason());
        return taskResult(replacement, false);
    }

    private UUID nullSafeBatch(UUID batchId) { return batchId; }

    @Transactional
    public BatchResult completeBatch(UUID batchId, VersionCommand command) {
        ActorContext actor = authorization.requireTask(QUEUE_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        ProcessingBatchSnapshot batch = requireBatch(batchId, actor);
        requireBatchOwner(batch, actor);
        List<ProcessingMemberSnapshot> members = repository.members(batchId, actor.hospitalScope());
        if (members.isEmpty() || members.stream().anyMatch(member -> !resolvedMember(member))) {
            throw business("P12-ERR-036", "批次仍有未完成或未裁决成员，不能完成");
        }
        String target = members.stream().allMatch(ProcessingMemberSnapshot::canEnterEmbedding)
                ? ProcessingBatch.COMPLETED : ProcessingBatch.PARTIAL;
        try {
            ProcessingBatch.persisted(batch.id(), batch.stateCode(), batch.concurrencyVersion()).transition(target);
        } catch (IllegalStateException exception) {
            throw business("P12-ERR-034", "当前批次不允许完成");
        }
        if (!repository.transitionBatch(batchId, actor.hospitalScope(), batch.stateCode(), target, actor.actorId(),
                command.expectedVersion(), Instant.now(), null)) throw versionConflict();
        ProcessingTaskSnapshot task = requireTask(batch.taskId(), actor);
        String taskTarget = ProcessingBatch.COMPLETED.equals(target) ? ProcessingTask.COMPLETED : ProcessingTask.PARTIAL;
        if (!repository.transitionTask(task.id(), actor.hospitalScope(), task.stateCode(), taskTarget, actor.actorId(),
                task.concurrencyVersion(), Instant.now())) throw versionConflict();
        repository.recordIdempotent("P17-COMPLETE-BATCH", command.idempotencyKey(), batchId + ":" + command.expectedVersion(),
                batchId, actor.actorId(), Instant.now());
        audit(batchId, "P17-CMD-COMPLETE-PROCESSING-BATCH", QUEUE_PERMISSION, actor, target);
        outbox.append("P17-EVT-PROCESSING-BATCH-COMPLETED", batchId, "P17-PROCESSING-BATCH",
                command.expectedVersion() + 1, UUID.randomUUID().toString(), target, actor.actorId());
        return batchResult(requireBatch(batchId, actor), false);
    }

    @Transactional
    public EmbeddingResult createEmbeddingTask(CreateEmbeddingCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        SourceBlock block = requireSourceForEmbedding(command.tissueBlockId(), actor);
        ProcessingResultSnapshot result = repository.result(command.processingResultId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "组织处理结果不存在或不在范围内"));
        ProcessingMemberSnapshot member = repository.member(result.memberId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "组织处理结果成员不存在"));
        if (!member.tissueBlockId().equals(block.blockId()) || !result.canEnterEmbedding()) {
            throw business("P12-ERR-036", "只有有效组织处理结果可以进入包埋");
        }
        Optional<FormationSnapshot> current = repository.currentFormation(block.blockId(), actor.hospitalScope());
        if (current.isPresent() && command.reworkOfFormationId() == null) {
            throw business("P12-ERR-032", "该计划蜡块已经形成当前有效实际蜡块");
        }
        int attempt = repository.nextEmbeddingAttempt(block.blockId(), result.id());
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-CREATE-EMBEDDING-TASK",
                command.idempotencyKey(), block.blockId() + ":" + result.id() + ":" + attempt);
        if (replay.isPresent()) return embeddingResult(requireEmbeddingTask(replay.get().resultObjectId(), actor), true);
        EmbeddingTaskSnapshot task = repository.createEmbeddingTask(block, result, "P17-EMBEDDING-TASK-" + token(),
                attempt, command.reworkOfFormationId(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-CREATE-EMBEDDING-TASK", command.idempotencyKey(), block.blockId() + ":" + result.id() + ":" + attempt,
                task.id(), actor.actorId(), Instant.now());
        audit(task.id(), "P17-CMD-CREATE-EMBEDDING-TASK", BLOCK_PERMISSION, actor, "embedding task created");
        outbox.append("P17-EVT-EMBEDDING-TASK-CREATED", task.id(), "P17-EMBEDDING-TASK", task.concurrencyVersion(),
                UUID.randomUUID().toString(), task.taskNo(), actor.actorId());
        return embeddingResult(task, false);
    }

    @Transactional
    public EmbeddingResult takeoverEmbeddingTask(UUID taskId, VersionCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        EmbeddingTaskSnapshot task = requireEmbeddingTask(taskId, actor);
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-TAKEOVER-EMBEDDING",
                command.idempotencyKey(), taskId + ":" + command.expectedVersion());
        if (replay.isPresent()) return embeddingResult(requireEmbeddingTask(taskId, actor), true);
        if (!repository.takeoverEmbedding(taskId, actor.hospitalScope(), actor.actorId(), command.expectedVersion(), Instant.now())) {
            throw versionConflict();
        }
        repository.recordIdempotent("P17-TAKEOVER-EMBEDDING", command.idempotencyKey(), taskId + ":" + command.expectedVersion(),
                taskId, actor.actorId(), Instant.now());
        audit(taskId, "P17-CMD-TAKEOVER-EMBEDDING-TASK", BLOCK_PERMISSION, actor, "embedding task takeover");
        return embeddingResult(requireEmbeddingTask(taskId, actor), false);
    }

    @Transactional
    public EmbeddingResult startEmbedding(UUID taskId, VersionCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        EmbeddingTaskSnapshot task = requireEmbeddingTask(taskId, actor);
        requireEmbeddingOwner(task, actor);
        if (!repository.transitionEmbedding(taskId, actor.hospitalScope(), task.stateCode(), EmbeddingTask.IN_PROGRESS,
                actor.actorId(), command.expectedVersion(), Instant.now())) throw versionConflict();
        audit(taskId, "P17-CMD-START-EMBEDDING", BLOCK_PERMISSION, actor, "embedding started");
        return embeddingResult(requireEmbeddingTask(taskId, actor), false);
    }

    @Transactional
    public EmbeddingResult recordEmbeddingRequirements(UUID taskId, RequirementCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        EmbeddingTaskSnapshot task = requireEmbeddingTask(taskId, actor);
        requireEmbeddingOwner(task, actor);
        if (blank(command.requirementSnapshot())) throw business("P12-ERR-035", "包埋要求不能为空");
        if (!repository.updateEmbeddingRequirement(taskId, actor.hospitalScope(), actor.actorId(), command.requirementSnapshot(),
                command.orientationReference(), command.expectedVersion())) throw versionConflict();
        audit(taskId, "P17-CMD-RECORD-EMBEDDING-REQUIREMENTS", BLOCK_PERMISSION, actor, "embedding requirements recorded");
        return embeddingResult(requireEmbeddingTask(taskId, actor), false);
    }

    @Transactional
    public FormationResult completeEmbedding(CompleteEmbeddingCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        EmbeddingTaskSnapshot task = requireEmbeddingTask(command.embeddingTaskId(), actor);
        requireEmbeddingOwner(task, actor);
        String digest = task.id() + ":" + command.expectedTaskVersion() + ":" + command.expectedBlockVersion();
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-COMPLETE-EMBEDDING", command.idempotencyKey(), digest);
        if (replay.isPresent()) return formationResult(repository.formation(replay.get().resultObjectId(), actor.hospitalScope()).orElseThrow(), true);
        if (blank(task.requirementSnapshot())) throw business("P12-ERR-035", "未记录包埋要求不能形成实际蜡块");
        SourceBlock block = repository.sourceBlock(task.tissueBlockId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-032", "计划蜡块不存在或不在范围内"));
        Optional<FormationSnapshot> previous = repository.currentFormation(block.blockId(), actor.hospitalScope());
        if (task.reworkOfFormationId() == null) {
            ActualBlockFormation.requireFirstFormation(previous.isPresent());
            if (!"P08-SM-004-ST-02".equals(block.blockStateCode()) || block.physicalFormedAt() != null) {
                throw business("P12-ERR-032", "计划蜡块当前状态不允许首次形成实际蜡块");
            }
        } else if (previous.isEmpty() || !previous.get().id().equals(task.reworkOfFormationId())) {
            throw business("P12-ERR-036", "重制实际蜡块未指向当前有效原形成事实");
        }
        ProcessingResultSnapshot result = repository.result(task.processingResultId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-036", "处理结果不存在"));
        if (!result.canEnterEmbedding()) throw business("P12-ERR-036", "处理结果未达到包埋准入条件");
        repository.lockTissueBlock(block.blockId(), actor.hospitalScope());
        if (task.reworkOfFormationId() == null && !repository.markBlockFormed(block.blockId(), actor.hospitalScope(),
                command.expectedBlockVersion(), Instant.now())) throw versionConflict();
        if (task.reworkOfFormationId() != null) repository.markPreviousFormationSuperseded(task.reworkOfFormationId(), Instant.now(), actor.actorId());
        var fact = repository.insertEmbeddingFact(task, actor.actorId(), Instant.now());
        FormationSnapshot formation = repository.insertFormation(fact, block, repository.nextFormationVersion(block.blockId()),
                previous.map(FormationSnapshot::id).orElse(null), actor.actorId(), Instant.now());
        if (task.reworkOfFormationId() != null) repository.linkReplacement(task.reworkOfFormationId(), formation.id(),
                requireTextValue(command.replacementReason()), actor.actorId(), Instant.now());
        if (!repository.markEmbeddingCompleted(task.id(), actor.hospitalScope(), actor.actorId(), command.expectedTaskVersion(), Instant.now())) {
            throw versionConflict();
        }
        repository.appendStateHistory(block.blockId(), "OBJ-004", "P08-SM-004", "P08-SM-004-ST-02", "P08-SM-004-ST-03",
                "E03", command.expectedBlockVersion(), command.expectedBlockVersion() + 1, Instant.now(), actor.actorId(), "包埋完成形成实际蜡块");
        repository.recordIdempotent("P17-COMPLETE-EMBEDDING", command.idempotencyKey(), digest, formation.id(), actor.actorId(), Instant.now());
        audit(formation.id(), "P17-CMD-COMPLETE-EMBEDDING-FORM-ACTUAL-BLOCK", BLOCK_PERMISSION, actor, "actual block formed");
        outbox.append("P17-EVT-ACTUAL-BLOCK-FORMED", formation.id(), "P17-ACTUAL-BLOCK-FORMATION",
                formation.formationVersion(), UUID.randomUUID().toString(), formation.inheritedBlockNo(), actor.actorId());
        return formationResult(formation, false);
    }

    @Transactional
    public EmbeddingResult requestEmbeddingRework(ReworkCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        FormationSnapshot formation = repository.formation(command.formationId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-032", "实际蜡块形成事实不存在"));
        SourceBlock block = repository.sourceBlock(formation.tissueBlockId(), actor.hospitalScope()).orElseThrow();
        ProcessingResultSnapshot result = repository.resultForEmbeddingFact(formation.embeddingFactId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-036", "实际蜡块缺少有效处理结果"));
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-REQUEST-EMBEDDING-REWORK",
                command.idempotencyKey(), command.formationId() + ":" + command.reason());
        if (replay.isPresent()) return embeddingResult(requireEmbeddingTask(replay.get().resultObjectId(), actor), true);
        EmbeddingTaskSnapshot task = repository.createEmbeddingTask(block, result, "P17-EMBEDDING-REWORK-" + token(),
                repository.nextEmbeddingAttempt(block.blockId(), result.id()), formation.id(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P17-REQUEST-EMBEDDING-REWORK", command.idempotencyKey(), command.formationId() + ":" + command.reason(),
                task.id(), actor.actorId(), Instant.now());
        audit(task.id(), "P17-CMD-REQUEST-EMBEDDING-REWORK", BLOCK_PERMISSION, actor, command.reason());
        return embeddingResult(task, false);
    }

    @Transactional
    public FormationResult voidActualBlock(VoidFormationCommand command) {
        ActorContext actor = authorization.requireTask(BLOCK_PERMISSION, TASK);
        requireText(command.idempotencyKey());
        FormationSnapshot formation = repository.formation(command.formationId(), actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-032", "实际蜡块形成事实不存在"));
        SourceBlock block = repository.sourceBlock(formation.tissueBlockId(), actor.hospitalScope()).orElseThrow();
        if (blank(command.reason())) throw business("P12-ERR-077", "作废实际蜡块必须提供理由");
        Optional<JdbcProcessingRepository.IdempotentReference> replay = idempotent("P17-VOID-ACTUAL-BLOCK",
                command.idempotencyKey(), command.formationId() + ":" + command.reason());
        if (replay.isPresent()) return formationResult(repository.formation(command.formationId(), actor.hospitalScope()).orElseThrow(), true);
        repository.lockTissueBlock(block.blockId(), actor.hospitalScope());
        if (!repository.voidFormation(formation.id(), actor.hospitalScope())
                || !repository.voidBlock(block.blockId(), actor.hospitalScope(), command.expectedBlockVersion(), Instant.now())) {
            throw versionConflict();
        }
        repository.recordIdempotent("P17-VOID-ACTUAL-BLOCK", command.idempotencyKey(), command.formationId() + ":" + command.reason(),
                formation.id(), actor.actorId(), Instant.now());
        audit(formation.id(), "P17-CMD-VOID-ACTUAL-BLOCK", BLOCK_PERMISSION, actor, command.reason());
        outbox.append("P17-EVT-ACTUAL-BLOCK-VOIDED", formation.id(), "P17-ACTUAL-BLOCK-FORMATION",
                formation.formationVersion(), UUID.randomUUID().toString(), command.reason(), actor.actorId());
        return formationResult(repository.formation(formation.id(), actor.hospitalScope()).orElseThrow(), false);
    }

    private SourceBlock requireAdmission(UUID blockId, ActorContext actor) {
        SourceBlock block = repository.sourceBlock(blockId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-031", "计划蜡块不存在或不在当前组织范围"));
        if (!"P08-SM-004-ST-02".equals(block.blockStateCode()) || block.physicalFormedAt() != null
                || !"P16-GROSSING-HANDED-OFF".equals(block.grossingBatchState())) {
            throw business("P12-ERR-031", "仅允许已完成取材并正式交接的计划蜡块进入组织处理");
        }
        if (repository.currentExceptionForBatch(block.grossingBatchId()).isPresent()) {
            throw business("P12-ERR-077", "存在未裁决阻断异常，不能进入组织处理");
        }
        return block;
    }

    private SourceBlock requireAdmissionForReprocess(UUID blockId, ActorContext actor) {
        SourceBlock block = repository.sourceBlock(blockId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-031", "原计划蜡块不存在"));
        if ("P08-SM-004-ST-01".equals(block.blockStateCode()) || block.physicalFormedAt() != null) {
            throw business("P12-ERR-036", "重新处理对象状态不允许");
        }
        return block;
    }

    private SourceBlock requireSourceForEmbedding(UUID blockId, ActorContext actor) {
        SourceBlock block = repository.sourceBlock(blockId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-032", "计划蜡块不存在"));
        if (!"P08-SM-004-ST-02".equals(block.blockStateCode()) && !"P08-SM-004-ST-03".equals(block.blockStateCode())) {
            throw business("P12-ERR-032", "计划蜡块生命周期不允许包埋");
        }
        return block;
    }

    private ProcessingTaskSnapshot requireTask(UUID taskId, ActorContext actor) {
        return repository.task(taskId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "组织处理任务不存在或不在当前范围"));
    }

    private ProcessingBatchSnapshot requireBatch(UUID batchId, ActorContext actor) {
        return repository.batch(batchId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "组织处理批次不存在或不在当前范围"));
    }

    private ProcessingRunSnapshot requireRun(UUID runId, ActorContext actor) {
        return repository.run(runId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "处理运行不存在或不在当前范围"));
    }

    private EmbeddingTaskSnapshot requireEmbeddingTask(UUID taskId, ActorContext actor) {
        return repository.embeddingTask(taskId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-032", "包埋任务不存在或不在当前范围"));
    }

    private void requireTaskOwner(ProcessingTaskSnapshot task, ActorContext actor) {
        if (!actor.actorId().equals(task.assignedActorRef())) throw business("P12-ERR-076", "当前主体不是组织处理责任人");
    }

    private void requireBatchOwner(ProcessingBatchSnapshot batch, ActorContext actor) {
        if (!actor.actorId().equals(batch.assignedActorRef())) throw business("P12-ERR-076", "当前主体不是处理批次责任人");
    }

    private void requireEmbeddingOwner(EmbeddingTaskSnapshot task, ActorContext actor) {
        if (!actor.actorId().equals(task.assignedActorRef())) throw business("P12-ERR-076", "当前主体不是包埋责任人");
    }

    private ProgramVersionSnapshot requireProgram(String code, String label) {
        return repository.programVersion(defaultText(code, "P17-SYNTHETIC-REFERENCE"), defaultText(label, "SYNTHETIC-1"))
                .orElseThrow(() -> business("P12-ERR-035", "组织处理程序版本不存在"));
    }

    private void ensureProgramStartAllowed(ProgramVersionSnapshot version) {
        ProcessingProgramVersion model = new ProcessingProgramVersion(version.id().toString(), version.versionLabel(),
                version.environmentCode(), version.versionStateCode(), version.versionDigest(), version.parameterReference());
        if (!model.active() || !model.allowedIn(runtimeEnvironment)) {
            throw business("P12-ERR-035", "当前环境缺少可用的正式组织处理程序版本");
        }
    }

    private Optional<JdbcProcessingRepository.IdempotentReference> idempotent(String operation, String key, String digest) {
        try {
            return repository.idempotent(operation, key, digest);
        } catch (JdbcProcessingRepository.IdempotencyConflictException exception) {
            throw business("P12-ERR-003", "幂等键已用于不同载荷");
        }
    }

    private void requireHuman(ActorContext actor) {
        if (!"HUMAN_USER".equals(actor.subjectTypeCode())) throw business("P12-ERR-075", "设备或服务身份不能确认有效业务结果");
    }

    private void requireText(String value) { if (blank(value)) throw business("P12-ERR-006", "幂等键不能为空"); }
    private String requireTextValue(String value) { if (blank(value)) throw business("P12-ERR-035", "必填业务事实不能为空"); return value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String defaultText(String value, String fallback) { return blank(value) ? fallback : value; }
    private String token() { return UUID.randomUUID().toString().substring(0, 12); }
    private P15BusinessException versionConflict() { return business("P12-ERR-074", "预期版本冲突，请重新读取后重试"); }
    private P15BusinessException business(String code, String message) { return new P15BusinessException(code, message); }

    private void audit(UUID target, String operation, String permission, ActorContext actor, String reason) {
        audit.append(operation, permission, actor, "COMPLETED", "COMPLETED", target,
                "P17-TECHNICAL-PROCESSING", UUID.randomUUID().toString(), reason);
    }

    private TaskResult taskResult(ProcessingTaskSnapshot task, boolean duplicate) {
        return new TaskResult(task.id(), task.taskNo(), task.tissueBlockId(), task.stateCode(), task.assignedActorRef(),
                task.actualActorRef(), task.concurrencyVersion(), duplicate);
    }

    private BatchResult batchResult(ProcessingBatchSnapshot batch, boolean duplicate) {
        return new BatchResult(batch.id(), batch.taskId(), batch.batchNo(), batch.stateCode(), batch.executionMode(),
                batch.deviceIdentity(), batch.programVersionSnapshot(), batch.assignedActorRef(), batch.concurrencyVersion(), duplicate);
    }

    private MemberResult memberResult(ProcessingMemberSnapshot member, boolean duplicate) {
        return new MemberResult(member.id(), member.batchId(), member.tissueBlockId(), member.plannedBlockNo(),
                member.stateCode(), member.canEnterEmbedding(), member.version(), duplicate);
    }

    private RunResult runResult(ProcessingRunSnapshot run, boolean duplicate) {
        return new RunResult(run.id(), run.batchId(), run.runNo(), run.stateCode(), run.executionMode(), run.externalRunId(), duplicate);
    }

    private ResultResult resultResult(ProcessingResultSnapshot result, boolean duplicate) {
        return new ResultResult(result.id(), result.runId(), result.memberId(), result.stateCode(), result.canEnterEmbedding(), duplicate);
    }

    private EmbeddingResult embeddingResult(EmbeddingTaskSnapshot task, boolean duplicate) {
        return new EmbeddingResult(task.id(), task.taskNo(), task.tissueBlockId(), task.processingResultId(), task.stateCode(),
                task.requirementSnapshot(), task.orientationReference(), task.concurrencyVersion(), duplicate);
    }

    private FormationResult formationResult(FormationSnapshot formation, boolean duplicate) {
        return new FormationResult(formation.id(), formation.tissueBlockId(), formation.inheritedBlockNo(),
                formation.formationVersion(), formation.stateCode(), formation.currentValid(), duplicate);
    }

    private boolean resolvedMember(ProcessingMemberSnapshot member) {
        return member.canEnterEmbedding() || "P17-MEMBER-FAILED".equals(member.stateCode())
                || "P17-MEMBER-AFFECTED".equals(member.stateCode());
    }

    public record CreateTaskCommand(UUID tissueBlockId, String idempotencyKey) { }
    public record VersionCommand(long expectedVersion, String idempotencyKey) { }
    public record CreateBatchCommand(UUID taskId, String programCode, String versionLabel, String executionMode,
            String deviceIdentity, String idempotencyKey) { }
    public record ProgramCommand(String programCode, String versionLabel, long expectedVersion, String idempotencyKey) { }
    public record DeviceCommand(String executionMode, String deviceIdentity, long expectedVersion, String idempotencyKey) { }
    public record AddMemberCommand(UUID tissueBlockId, String idempotencyKey) { }
    public record StepCommand(UUID runId, int sequence, String stepCode, String stateCode, String observedReference,
            String idempotencyKey) { }
    public record RawResultCommand(UUID runId, String externalMessageId, String payloadDigest, String rawStateCode,
            String payloadReference, Instant deviceOccurredAt, String idempotencyKey) { }
    public record ConfirmResultCommand(UUID runId, UUID memberId, String resultStateCode, boolean canEnterEmbedding,
            String summary, long expectedMemberVersion, String idempotencyKey) { }
    public record InterruptCommand(long expectedVersion, String reason, String idempotencyKey) { }
    public record FailureCommand(long expectedVersion, String reason, String idempotencyKey) { }
    public record ImpactCommand(UUID memberId, String impactStateCode, boolean canContinue, boolean requiresReprocess,
            boolean isolationRequired, String reason, String idempotencyKey) { }
    public record RecoveryCommand(UUID exceptionId, String recoveryKindCode, String reason, String idempotencyKey) { }
    public record ReprocessCommand(UUID memberId, String reason, String idempotencyKey) { }
    public record CreateEmbeddingCommand(UUID tissueBlockId, UUID processingResultId, UUID reworkOfFormationId,
            String idempotencyKey) { }
    public record RequirementCommand(String requirementSnapshot, String orientationReference, long expectedVersion,
            String idempotencyKey) { }
    public record CompleteEmbeddingCommand(UUID embeddingTaskId, long expectedTaskVersion, long expectedBlockVersion,
            String replacementReason, String idempotencyKey) { }
    public record ReworkCommand(UUID formationId, String reason, String idempotencyKey) { }
    public record VoidFormationCommand(UUID formationId, long expectedBlockVersion, String reason, String idempotencyKey) { }
    public record TaskResult(UUID taskId, String taskNo, UUID tissueBlockId, String stateCode, String assignedActor,
            String actualActor, long concurrencyVersion, boolean duplicate) { }
    public record BatchResult(UUID batchId, UUID taskId, String batchNo, String stateCode, String executionMode,
            String deviceIdentity, String programVersionSnapshot, String assignedActor, long concurrencyVersion,
            boolean duplicate) { }
    public record MemberResult(UUID memberId, UUID batchId, UUID tissueBlockId, String plannedBlockNo, String stateCode,
            boolean canEnterEmbedding, long concurrencyVersion, boolean duplicate) { }
    public record RunResult(UUID runId, UUID batchId, int runNo, String stateCode, String executionMode,
            String externalRunId, boolean duplicate) { }
    public record StepResult(UUID stepId, int sequence, String stepCode, String stateCode, boolean duplicate) { }
    public record RawResultResult(UUID rawResultId, UUID runId, String stateCode, boolean duplicate) { }
    public record ResultResult(UUID resultId, UUID runId, UUID memberId, String stateCode, boolean canEnterEmbedding,
            boolean duplicate) { }
    public record ImpactResult(UUID impactId, UUID memberId, boolean duplicate) { }
    public record RecoveryResult(UUID recoveryId, UUID exceptionId, boolean duplicate) { }
    public record EmbeddingResult(UUID taskId, String taskNo, UUID tissueBlockId, UUID processingResultId,
            String stateCode, String requirementSnapshot, String orientationReference, long concurrencyVersion,
            boolean duplicate) { }
    public record FormationResult(UUID formationId, UUID tissueBlockId, String inheritedBlockNo, int formationVersion,
            String stateCode, boolean currentValid, boolean duplicate) { }
}

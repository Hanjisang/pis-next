package com.hanjisang.pis.technical.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.technical.domain.BlockNumberingPolicy;
import com.hanjisang.pis.technical.domain.GrossingBatch;
import com.hanjisang.pis.technical.domain.TissueBlock;
import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository;
import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository.BatchSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository.BlockSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository.LabelSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository.PrintSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository.SpecimenSource;

@Service
public class GrossingApplicationService {

    private static final String TASK = "P16-GROSSING-BLOCK-LABELING";
    private final JdbcGrossingRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final BlockNumberingPolicy numberingPolicy;

    public GrossingApplicationService(JdbcGrossingRepository repository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox,
            @org.springframework.beans.factory.annotation.Value("${pis.runtime-environment:local}") String environment) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.numberingPolicy = BlockNumberingPolicy.dev(environment);
    }

    @Transactional
    public BatchResult createBatch(CreateBatchCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-050", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        SpecimenSource source = source(command.specimenId(), actor);
        verifyIdentity(source, command.specimenNo(), command.caseNo(), command.patientIdentityReference());
        String digest = command.specimenId() + ":" + command.caseNo();
        var existing = findIdempotent("P16-CREATE-BATCH", command.idempotencyKey(), digest);
        if (existing.isPresent()) return batchResult(existing.get().resultObjectId(), actor, true);
        BatchSnapshot batch;
        try {
            batch = repository.createBatch(source.specimenId(), "DEV-GROSS-" + token(), actor.hospitalScope(),
                    actor.actorId(), Instant.now());
        } catch (IllegalArgumentException ex) {
            throw business("P12-ERR-022", "标本不存在或不在当前数据范围");
        }
        repository.recordIdempotent("P16-CREATE-BATCH", command.idempotencyKey(), digest, batch.id(), actor.actorId(),
                Instant.now());
        record(batch.id(), "P16-CMD-CREATE-BATCH", "P14-PERM-050", actor, "COMPLETED", "batch created");
        outbox.append("P16-EVT-GROSSING-BATCH-CREATED", batch.id(), "P16-GROSSING-BATCH", 0,
                UUID.randomUUID().toString(), batch.batchNo(), actor.actorId());
        return batchResult(batch.id(), actor, false);
    }

    public List<Map<String, Object>> queue() {
        ActorContext actor = authorization.requireTask("P14-PERM-050", TASK);
        return repository.queue(actor.hospitalScope(), actor.actorId());
    }

    public BatchResult batch(UUID batchId) {
        ActorContext actor = authorization.requireTask("P14-PERM-050", TASK);
        return batchResult(batchId, actor, false);
    }

    @Transactional
    public BatchResult takeover(UUID batchId, TakeoverCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-050", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BatchSnapshot batch = requireBatch(batchId, actor);
        if (!batch.stateCode().equals(GrossingBatch.PLANNED) && !batch.stateCode().equals(GrossingBatch.ASSIGNED)) {
            throw business("P12-ERR-031", "当前取材批次不允许接管");
        }
        if (!repository.takeover(batchId, command.expectedVersion(), actor.actorId(), Instant.now())) {
            throw business("P12-ERR-074", "取材任务已被其他主体接管或版本冲突");
        }
        record(batchId, "P16-CMD-TAKEOVER", "P14-PERM-050", actor, "COMPLETED", "task takeover");
        outbox.append("P16-EVT-GROSSING-TASK-TAKEN-OVER", batchId, "P16-GROSSING-BATCH",
                command.expectedVersion() + 1, UUID.randomUUID().toString(), actor.actorId(), actor.actorId());
        return batchResult(batchId, actor, false);
    }

    @Transactional
    public BatchResult addSpecimen(UUID batchId, AddSpecimenCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-050", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BatchSnapshot batch = requireBatch(batchId, actor);
        if (!GrossingBatch.PLANNED.equals(batch.stateCode()) && !GrossingBatch.ASSIGNED.equals(batch.stateCode())) {
            throw business("P12-ERR-031", "取材开始后不能向批次追加标本");
        }
        SpecimenSource source = source(command.specimenId(), actor);
        verifyIdentity(source, command.specimenNo(), command.caseNo(), command.patientIdentityReference());
        if (!repository.appendSpecimen(batchId, source, actor.actorId(), Instant.now())) {
            throw business("P12-ERR-022", "标本不能加入当前取材批次");
        }
        if (!repository.touchBatch(batchId, actor.hospitalScope(), actor.actorId(), command.expectedVersion())) {
            throw versionConflict();
        }
        record(batchId, "P16-CMD-ADD-BATCH-SPECIMEN", "P14-PERM-050", actor, "COMPLETED", "specimen added");
        return batchResult(batchId, actor, false);
    }

    @Transactional
    public BatchResult transition(UUID batchId, TransitionCommand command, String target, String permission) {
        ActorContext actor = authorization.requireTask(permission, TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BatchSnapshot current = requireBatch(batchId, actor);
        if (!actor.actorId().equals(current.assignedActor())) {
            throw business("P12-ERR-076", "当前主体不是取材责任人");
        }
        try {
            GrossingBatch.persisted(batchId, current.stateCode(), current.concurrencyVersion()).transition(target);
        } catch (IllegalStateException ex) {
            throw business("P12-ERR-034", "当前取材批次不允许该状态转换");
        }
        if (!repository.transitionBatch(batchId, actor.hospitalScope(), actor.actorId(), current.stateCode(), target,
                command.expectedVersion(), Instant.now())) {
            throw business("P12-ERR-074", "取材批次版本冲突");
        }
        record(batchId, "P16-CMD-" + target, permission, actor, "COMPLETED", "batch state transition");
        outbox.append("P16-EVT-GROSSING-STATE-CHANGED", batchId, "P16-GROSSING-BATCH",
                command.expectedVersion() + 1, UUID.randomUUID().toString(), target, actor.actorId());
        return batchResult(batchId, actor, false);
    }

    @Transactional
    public RecordResult recordGrossing(UUID batchId, GrossingRecordCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-013", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BatchSnapshot batch = requireBatch(batchId, actor);
        requireInProgress(batch);
        if (batch.concurrencyVersion() != command.expectedVersion()) throw versionConflict();
        SpecimenSource source = source(command.specimenId(), actor);
        verifyIdentity(source, command.specimenNo(), command.caseNo(), command.patientIdentityReference());
        if (!command.identityVerified() || !command.patientIdentityVerified()) {
            throw business("P12-ERR-079", "患者或标本身份核对未通过，取材已阻断");
        }
        if (command.grossAppearance() == null || command.grossAppearance().isBlank()
                || command.grossDescription() == null || command.grossDescription().isBlank() || command.quantity() <= 0
                || command.quantityUnitCode() == null || command.quantityUnitCode().isBlank()) {
            throw business("P12-ERR-031", "取材记录缺少外观、数量、单位或大体描述");
        }
        var latest = repository.latestRecord(batchId, command.specimenId());
        if (latest.isPresent() && (command.correctionReason() == null || command.correctionReason().isBlank())) {
            throw business("P12-ERR-034", "已存在取材事实，普通修改被拒绝；更正必须提供理由");
        }
        int nextVersion = latest.map(value -> value.version() + 1).orElse(1);
        var record = repository.insertGrossingRecord(batchId, command.specimenId(), nextVersion, true, true,
                command.grossAppearance(), command.grossDescription(), command.quantity(), command.quantityUnitCode(),
                command.correctionReason(), command.reviewActorReference(), actor.actorId(), Instant.now());
        if (!repository.touchBatch(batchId, actor.hospitalScope(), actor.actorId(), command.expectedVersion())) {
            throw versionConflict();
        }
        record(batchId, "P16-CMD-RECORD-GROSSING", "P14-PERM-013", actor, "COMPLETED", "grossing record appended");
        outbox.append("P16-EVT-GROSSING-RECORDED", record.id(), "P16-GROSSING-RECORD", nextVersion,
                UUID.randomUUID().toString(), command.grossDescription(), actor.actorId());
        return new RecordResult(record.id(), nextVersion, false);
    }

    @Transactional
    public SampleResult addSample(UUID batchId, SampleCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-013", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BatchSnapshot batch = requireBatch(batchId, actor);
        requireInProgress(batch);
        if (batch.concurrencyVersion() != command.expectedVersion()) throw versionConflict();
        SpecimenSource source = source(command.specimenId(), actor);
        ensureBatchSpecimen(batchId, source);
        var record = repository.latestRecord(batchId, command.specimenId())
                .orElseThrow(() -> business("P12-ERR-033", "组织取样缺少对应取材记录"));
        if (command.sourceSite() == null || command.sourceSite().isBlank() || command.description() == null
                || command.description().isBlank() || command.quantity() <= 0 || command.unit() == null
                || command.unit().isBlank()) throw business("P12-ERR-033", "组织取样缺少来源、描述或数量");
        var sample = repository.insertSample(batchId, record.id(), source.specimenId(), "DEV-SAMPLE-" + token(),
                command.sourceSite(), command.description(), command.quantity(), command.unit(), actor.actorId(),
                Instant.now());
        if (!repository.touchBatch(batchId, actor.hospitalScope(), actor.actorId(), command.expectedVersion())) {
            throw versionConflict();
        }
        record(batchId, "P16-CMD-ADD-SAMPLE", "P14-PERM-013", actor, "COMPLETED", "tissue sample appended");
        outbox.append("P16-EVT-TISSUE-SAMPLE-RECORDED", sample.id(), "P16-TISSUE-SAMPLE", sample.version(),
                UUID.randomUUID().toString(), sample.sampleNo(), actor.actorId());
        return new SampleResult(sample.id(), sample.sampleNo(), sample.stateCode(), false);
    }

    @Transactional
    public BlockResult createBlock(UUID batchId, CreateBlockCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-014", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BatchSnapshot batch = requireBatch(batchId, actor);
        requireInProgress(batch);
        if (batch.concurrencyVersion() != command.expectedVersion()) throw versionConflict();
        SpecimenSource source = source(command.specimenId(), actor);
        ensureBatchSpecimen(batchId, source);
        String blockNo;
        try { blockNo = numberingPolicy.nextBlockNumber(); }
        catch (IllegalStateException ex) { throw business("P12-ERR-032", "未配置正式蜡块编号策略"); }
        var block = repository.insertBlock(batchId, source.specimenId(), source.caseId(), blockNo,
                textOrDefault(command.blockKindCode(), "ROUTINE"), textOrDefault(command.sourceMaterialKindCode(), "TISSUE"),
                "DEV-BOX-" + token(), actor.hospitalScope(), actor.actorId(), Instant.now());
        if (!repository.touchBatch(batchId, actor.hospitalScope(), actor.actorId(), command.expectedVersion())) {
            throw versionConflict();
        }
        record(block.id(), "P16-CMD-CREATE-BLOCK", "P14-PERM-014", actor, "COMPLETED", "planned block created");
        outbox.append("P16-EVT-BLOCK-NUMBER-ASSIGNED", block.id(), "OBJ-004", block.version(),
                UUID.randomUUID().toString(), block.blockNo(), actor.actorId());
        return new BlockResult(block.id(), block.blockNo(), block.tissueBoxNo(), block.stateCode(), block.version(), false);
    }

    @Transactional
    public AssignmentResult assignSample(UUID blockId, AssignSampleCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-014", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BlockSnapshot block = requireBlock(blockId, actor);
        BatchSnapshot batch = requireBatch(block.batchId(), actor);
        requireInProgress(batch);
        var existingAssignment = repository.sampleAssignment(command.sampleId(), actor.hospitalScope());
        if (existingAssignment.isPresent()) {
            if (existingAssignment.get().equals(blockId)) return new AssignmentResult(blockId, command.sampleId(), true);
            throw business("P12-ERR-033", "组织取样已分配给其他蜡块");
        }
        if (!repository.assignSample(blockId, command.sampleId(), actor.hospitalScope(), actor.actorId(), Instant.now())) {
            throw business("P12-ERR-033", "组织取样不存在、跨范围或已分配给其他蜡块");
        }
        if (!repository.touchBatch(batch.id(), actor.hospitalScope(), actor.actorId(), command.expectedVersion())) {
            throw versionConflict();
        }
        record(blockId, "P16-CMD-ASSIGN-SAMPLE", "P14-PERM-014", actor, "COMPLETED", "sample assigned");
        return new AssignmentResult(blockId, command.sampleId(), false);
    }

    @Transactional
    public LabelResult generateLabel(UUID blockId, GenerateLabelCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-014", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        BlockSnapshot block = requireBlock(blockId, actor);
        if (block.stateCode().equals(TissueBlock.VOIDED)) throw business("P12-ERR-033", "已作废蜡块不能生成标签");
        requireBatch(block.batchId(), actor);
        String digest = blockId + ":" + block.version();
        var existing = findIdempotent("P16-GENERATE-LABEL", command.idempotencyKey(), digest);
        if (existing.isPresent()) return labelResult(existing.get().resultObjectId(), actor, true);
        repository.lockBlockForLabel(blockId);
        var current = repository.labelForTargetVersion(blockId, block.version(), actor.hospitalScope());
        if (current.isPresent()) {
            repository.recordIdempotent("P16-GENERATE-LABEL", command.idempotencyKey(), digest,
                    current.get().id(), actor.actorId(), Instant.now());
            return labelResult(current.get().id(), actor, true);
        }
        int version = repository.nextLabelVersion(blockId);
        String snapshot = "case_no=" + block.caseNo() + "\nblock_no=" + block.blockNo() + "\nspecimen_no="
                + block.specimenNo() + "\ntissue_box_no=" + block.tissueBoxNo() + "\ntemplate=P16-REFERENCE-TEMPLATE-1";
        LabelSnapshot label = repository.generateLabel(blockId, block.version(), version, snapshot,
                "P16|" + block.blockNo(), actor.hospitalScope(), actor.actorId(), Instant.now());
        repository.recordIdempotent("P16-GENERATE-LABEL", command.idempotencyKey(), digest, label.id(), actor.actorId(),
                Instant.now());
        record(label.id(), "P16-CMD-GENERATE-LABEL", "P14-PERM-014", actor, "COMPLETED", "label generated");
        outbox.append("P16-EVT-LABEL-GENERATED", label.id(), "P16-LABEL", version,
                UUID.randomUUID().toString(), label.barcodePayload(), actor.actorId());
        return labelResult(label.id(), actor, false);
    }

    @Transactional
    public PrintResult submitPrint(UUID labelId, PrintCommand command, boolean reprint) {
        ActorContext actor = authorization.requireTask("P14-PERM-014", TASK);
        LabelSnapshot label = repository.label(labelId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-022", "标签不存在或不在当前数据范围"));
        if (label.stateCode().equals("P16-LABEL-VOIDED")) throw business("P12-ERR-033", "已作废标签不能打印");
        if (reprint && (command.reason() == null || command.reason().isBlank())) {
            throw business("P12-ERR-073", "重打必须提供可审计理由");
        }
        String key = command.idempotencyKey();
        requireText(key, "P12-ERR-006");
        PrintSnapshot print = repository.submitPrint(label.id(), key, reprint ? "REPRINT" : "INITIAL", label.id(),
                command.reason(), actor.actorId(), Instant.now());
        record(label.id(), reprint ? "P16-CMD-REPRINT-LABEL" : "P16-CMD-SUBMIT-PRINT", "P14-PERM-014", actor,
                "COMPLETED", reprint ? command.reason() : "reference print submitted");
        outbox.append("P16-EVT-LABEL-PRINT-REQUESTED", print.requestId(), "P16-PRINT-REQUEST", 1,
                UUID.randomUUID().toString(), print.outcome(), actor.actorId());
        return new PrintResult(print.requestId(), print.attemptId(), print.labelId(), print.stateCode(), print.outcome(),
                true);
    }

    @Transactional
    public PrintResult recordPrintResult(PrintResultCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-014", TASK);
        requireText(command.idempotencyKey(), "P12-ERR-006");
        String state = command.outcomeCode().equals("CONFIRMED") ? "P16-PRINT-CONFIRMED" : "P16-PRINT-FAILED";
        if (!repository.recordPrintResult(command.requestId(), state, command.outcomeCode(), command.note(),
                actor.actorId(), Instant.now())) throw business("P12-ERR-074", "打印请求不存在或已完成");
        record(command.requestId(), "P16-CMD-RECORD-PRINT-RESULT", "P14-PERM-014", actor, state, "print result recorded");
        return new PrintResult(command.requestId(), null, command.labelId(), state, command.outcomeCode(), false);
    }

    @Transactional
    public Map<String, Object> voidLabel(UUID labelId, String reason) {
        return voidLabel(labelId, reason, "internal-void-" + labelId);
    }

    @Transactional
    public Map<String, Object> voidLabel(UUID labelId, String reason, String idempotencyKey) {
        ActorContext actor = authorization.requireTask("P14-PERM-014", TASK);
        requireText(idempotencyKey, "P12-ERR-006");
        if (reason == null || reason.isBlank()) throw business("P12-ERR-073", "标签作废必须提供可审计理由");
        if (!repository.voidLabel(labelId, actor.hospitalScope(), actor.actorId(), Instant.now())) {
            throw business("P12-ERR-033", "标签不存在、已作废或不在当前数据范围");
        }
        record(labelId, "P16-CMD-VOID-LABEL", "P14-PERM-014", actor, "COMPLETED", reason);
        outbox.append("P16-EVT-LABEL-VOIDED", labelId, "P16-LABEL", 1, UUID.randomUUID().toString(), reason,
                actor.actorId());
        return Map.of("labelId", labelId, "stateCode", "P16-LABEL-VOIDED", "duplicate", false);
    }

    @Transactional
    public BatchResult complete(UUID batchId, TransitionCommand command) {
        ActorContext actor = authorization.requireTask("P14-PERM-013", TASK);
        BatchSnapshot batch = requireBatch(batchId, actor);
        requireInProgress(batch);
        if (batch.concurrencyVersion() != command.expectedVersion()) throw versionConflict();
        var check = repository.completionCheck(batchId);
        if (!check.complete()) throw business("P12-ERR-034", "取材完成前置条件未满足：记录、取样去向、计划蜡块或标签不完整");
        if (!repository.transitionBatch(batchId, actor.hospitalScope(), actor.actorId(), GrossingBatch.IN_PROGRESS,
                GrossingBatch.COMPLETED, command.expectedVersion(), Instant.now())) throw versionConflict();
        repository.markBlocksGrossingRecorded(batchId, actor.actorId(), Instant.now());
        record(batchId, "P16-CMD-COMPLETE-GROSSING", "P14-PERM-013", actor, "COMPLETED", "grossing complete");
        outbox.append("P16-EVT-GROSSING-COMPLETED", batchId, "P16-GROSSING-BATCH", command.expectedVersion() + 1,
                UUID.randomUUID().toString(), "handoff-ready", actor.actorId());
        return batchResult(batchId, actor, false);
    }

    private BatchResult batchResult(UUID id, ActorContext actor, boolean duplicate) {
        BatchSnapshot batch = requireBatch(id, actor);
        return new BatchResult(batch.id(), batch.batchNo(), batch.taskStateCode(), batch.stateCode(),
                batch.assignedActor(), batch.actualActor(), batch.concurrencyVersion(), duplicate);
    }

    private LabelResult labelResult(UUID id, ActorContext actor, boolean duplicate) {
        LabelSnapshot label = repository.label(id, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-022", "标签不存在或不在当前数据范围"));
        return new LabelResult(label.id(), label.targetObjectId(), label.version(), label.stateCode(), label.snapshot(),
                label.barcodePayload(), duplicate);
    }

    private BatchSnapshot requireBatch(UUID id, ActorContext actor) {
        return repository.batch(id, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-022", "取材批次不存在或不在当前数据范围"));
    }

    private BlockSnapshot requireBlock(UUID id, ActorContext actor) {
        return repository.block(id, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-022", "蜡块计划不存在或不在当前数据范围"));
    }

    private SpecimenSource source(UUID specimenId, ActorContext actor) {
        SpecimenSource source = repository.eligibleSpecimen(specimenId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-022", "标本不存在或不在当前数据范围"));
        if (!"P08-SM-003-ST-03".equals(source.stateCode())) {
            throw business("P12-ERR-023", "只有已接收且未隔离标本可以进入取材");
        }
        return source;
    }

    private void verifyIdentity(SpecimenSource source, String specimenNo, String caseNo, String patientReference) {
        if (patientReference == null || patientReference.isBlank() || !source.specimenNo().equals(specimenNo)
                || !source.caseNo().equals(caseNo)) {
            throw business("P12-ERR-079", "患者、病例或标本身份核对不一致，取材已阻断");
        }
    }

    private void ensureBatchSpecimen(UUID batchId, SpecimenSource source) {
        if (!repository.batchContainsSpecimen(batchId, source.specimenId(), source.organizationReference())) {
            throw business("P12-ERR-022", "标本不属于该取材批次");
        }
    }

    private void requireInProgress(BatchSnapshot batch) {
        if (!GrossingBatch.IN_PROGRESS.equals(batch.stateCode())) throw business("P12-ERR-031", "取材批次尚未开始或已关闭");
    }

    private void record(UUID target, String operation, String permission, ActorContext actor, String outcome, String reason) {
        audit.append(operation, permission, actor, "ALLOWED", outcome, target, "P16-GROSSING", UUID.randomUUID().toString(), reason);
    }

    private P15BusinessException versionConflict() { return business("P12-ERR-074", "版本冲突，请重新读取后提交"); }
    private P15BusinessException business(String code, String message) { return new P15BusinessException(code, message); }
    private void requireText(String value, String code) { if (value == null || value.isBlank()) throw business(code, "缺少幂等键"); }
    private String textOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String token() { return UUID.randomUUID().toString().substring(0, 12).toUpperCase(); }

    private java.util.Optional<JdbcGrossingRepository.IdempotentReference> findIdempotent(String operationCode,
            String key, String digest) {
        try {
            return repository.findIdempotent(operationCode, key, digest);
        } catch (JdbcGrossingRepository.IdempotencyConflictException ex) {
            throw business("P12-ERR-003", "幂等键已经用于不同请求载荷");
        }
    }

    public record CreateBatchCommand(UUID specimenId, String specimenNo, String caseNo, String patientIdentityReference,
            String idempotencyKey) { }
    public record TakeoverCommand(long expectedVersion, String idempotencyKey) {
        public TakeoverCommand(long expectedVersion) { this(expectedVersion, "internal-takeover-v" + expectedVersion); }
    }
    public record AddSpecimenCommand(UUID specimenId, String specimenNo, String caseNo,
            String patientIdentityReference, long expectedVersion, String idempotencyKey) {
        public AddSpecimenCommand(UUID specimenId, String specimenNo, String caseNo, String patientIdentityReference,
                long expectedVersion) {
            this(specimenId, specimenNo, caseNo, patientIdentityReference, expectedVersion,
                    "internal-add-specimen-v" + expectedVersion);
        }
    }
    public record TransitionCommand(long expectedVersion, String idempotencyKey) {
        public TransitionCommand(long expectedVersion) { this(expectedVersion, "internal-transition-v" + expectedVersion); }
    }
    public record GrossingRecordCommand(UUID specimenId, String specimenNo, String caseNo,
            String patientIdentityReference, boolean identityVerified, boolean patientIdentityVerified,
            String grossAppearance, String grossDescription, double quantity, String quantityUnitCode,
            String correctionReason, String reviewActorReference, long expectedVersion, String idempotencyKey) {
        public GrossingRecordCommand(UUID specimenId, String specimenNo, String caseNo, String patientIdentityReference,
                boolean identityVerified, boolean patientIdentityVerified, String grossAppearance,
                String grossDescription, double quantity, String quantityUnitCode, String correctionReason,
                String reviewActorReference, long expectedVersion) {
            this(specimenId, specimenNo, caseNo, patientIdentityReference, identityVerified, patientIdentityVerified,
                    grossAppearance, grossDescription, quantity, quantityUnitCode, correctionReason,
                    reviewActorReference, expectedVersion, "internal-record-v" + expectedVersion);
        }
    }
    public record SampleCommand(UUID specimenId, String sourceSite, String description, double quantity, String unit,
            long expectedVersion, String idempotencyKey) {
        public SampleCommand(UUID specimenId, String sourceSite, String description, double quantity, String unit,
                long expectedVersion) {
            this(specimenId, sourceSite, description, quantity, unit, expectedVersion,
                    "internal-sample-v" + expectedVersion);
        }
    }
    public record CreateBlockCommand(UUID specimenId, String blockKindCode, String sourceMaterialKindCode,
            long expectedVersion, String idempotencyKey) {
        public CreateBlockCommand(UUID specimenId, String blockKindCode, String sourceMaterialKindCode,
                long expectedVersion) {
            this(specimenId, blockKindCode, sourceMaterialKindCode, expectedVersion,
                    "internal-block-v" + expectedVersion);
        }
    }
    public record AssignSampleCommand(UUID sampleId, long expectedVersion, String idempotencyKey) {
        public AssignSampleCommand(UUID sampleId, long expectedVersion) {
            this(sampleId, expectedVersion, "internal-assign-v" + expectedVersion);
        }
    }
    public record GenerateLabelCommand(String idempotencyKey) { }
    public record PrintCommand(String idempotencyKey, String reason) { }
    public record PrintResultCommand(UUID requestId, UUID labelId, String outcomeCode, String note,
            String idempotencyKey) {
        public PrintResultCommand(UUID requestId, UUID labelId, String outcomeCode, String note) {
            this(requestId, labelId, outcomeCode, note, "internal-print-result-" + requestId);
        }
    }
    public record BatchResult(UUID batchId, String batchNo, String taskStateCode, String stateCode,
            String assignedActor, String actualActor, long concurrencyVersion, boolean duplicate) { }
    public record RecordResult(UUID recordId, int recordVersion, boolean duplicate) { }
    public record SampleResult(UUID sampleId, String sampleNo, String stateCode, boolean duplicate) { }
    public record BlockResult(UUID blockId, String blockNo, String tissueBoxNo, String stateCode, long concurrencyVersion,
            boolean duplicate) { }
    public record AssignmentResult(UUID blockId, UUID sampleId, boolean duplicate) { }
    public record LabelResult(UUID labelId, UUID targetObjectId, int labelVersion, String stateCode, String snapshot,
            String barcodePayload, boolean duplicate) { }
    public record PrintResult(UUID requestId, UUID attemptId, UUID labelId, String stateCode, String outcome,
            boolean submitted) { }
}

package com.hanjisang.pis.v2.technical.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.integration.device.IhcDevicePort;
import com.hanjisang.pis.integration.device.LabelPrintService;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityUnit;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository;
import com.hanjisang.pis.v2.material.domain.Block;
import com.hanjisang.pis.v2.material.domain.Grossing;
import com.hanjisang.pis.v2.material.domain.Slide;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrder;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderItem;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderItemResult;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderStatus;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderTarget;
import com.hanjisang.pis.v2.technical.domain.TechnicalOutputType;
import com.hanjisang.pis.v2.technical.domain.TechnicalProject;
import com.hanjisang.pis.v2.technical.domain.TechnicalTargetType;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.IdempotencyResult;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.ItemSnapshot;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.OrderSnapshot;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.OutputSnapshot;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.TargetSnapshot;

@Service
public class V2TechnicalOrderApplicationService {

    public static final String TECHNICAL_ORDER = "P14-PERM-015";
    public static final String TECHNICAL_PROJECT = "P14-PERM-016";
    public static final String TECHNICAL_EXECUTION = "P14-PERM-017";
    public static final String TECHNICAL_QUERY = "P14-PERM-048";
    public static final String DIAGNOSIS_INITIAL = "P14-PERM-034";

    private final JdbcV2TechnicalOrderRepository repository;
    private final JdbcV2DiagnosisRepository diagnosisRepository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MaterialRepository materialRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final IhcDevicePort ihcDevice;
    private final LabelPrintService labelPrintService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public V2TechnicalOrderApplicationService(JdbcV2TechnicalOrderRepository repository,
            JdbcV2DiagnosisRepository diagnosisRepository, JdbcV2RegistrationRepository registrationRepository,
            JdbcV2MaterialRepository materialRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox, IhcDevicePort ihcDevice,
            LabelPrintService labelPrintService) {
        this.repository = repository;
        this.diagnosisRepository = diagnosisRepository;
        this.registrationRepository = registrationRepository;
        this.materialRepository = materialRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.ihcDevice = ihcDevice;
        this.labelPrintService = labelPrintService;
    }

    @Transactional
    public ProjectResult createProject(CreateProjectCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_PROJECT);
        requireText(command.projectCode(), "技术项目编码不能为空");
        requireText(command.projectName(), "技术项目名称不能为空");
        requireText(command.capabilityCode(), "技术项目能力编码不能为空");
        requireText(command.outputTypeCode(), "技术项目输出类型不能为空");
        requireText(command.allowedTargetTypes(), "技术项目目标类型不能为空");
        TechnicalProject project = TechnicalProject.create(UUID.randomUUID(), actor.hospitalScope(),
                command.businessTypeId(), command.projectCode(), command.projectName(), command.capabilityCode(),
                command.outputTypeCode(), command.enabled(), command.allowedTargetTypes(), command.producesSlide(),
                command.producesBlock(), command.producesStructuredResult(), command.requiresResult(),
                command.deviceTypeCode(), command.consumableRequired(), command.defaultSlideType(),
                command.parametersSchema(), command.resultSchema(), command.feeMapping(), command.displayConfiguration(),
                command.requiredBeforeSignOutDefault(), command.configurationVersion());
        repository.insertProject(project, Instant.now(), actor.actorId());
        audit.append("PIS-V2-I04-TECHNICAL-PROJECT-CREATE", TECHNICAL_PROJECT, actor, "ALLOWED", "COMPLETED",
                project.id(), "V2-TECHNICAL-PROJECT", UUID.randomUUID().toString(), "project=" + project.code());
        return projectResult(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResult> listProjects(UUID caseId) {
        ActorContext actor = authorization.require(TECHNICAL_QUERY);
        if (caseId == null) {
            return repository.findAllProjects(actor.hospitalScope(), true).stream().map(this::projectResult).toList();
        }
        Case pathologyCase = activeOrExistingCase(caseId, actor);
        return repository.findProjects(actor.hospitalScope(), pathologyCase.businessTypeId(), true).stream()
                .map(this::projectResult).toList();
    }

    @Transactional
    public TechnicalOrderResult createOrder(CreateTechnicalOrderCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_ORDER);
        requireId(command.diagnosisId(), "诊断ID不能为空");
        requireKey(command.idempotencyKey());
        if (command.items() == null || command.items().isEmpty()) {
            throw reject("V2-TECHNICAL-ORDER-ITEM-REQUIRED", "技术医嘱至少需要一个项目");
        }
        String operation = "PIS-V2-I04-TECHNICAL-ORDER-CREATE";
        String digest = digest(command.diagnosisId(), command.requiredBeforeSignOut(), command.items());
        TechnicalOrderResult replay = replayOrder(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        Diagnosis diagnosis = diagnosisRepository.findDiagnosis(command.diagnosisId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围"));
        Case pathologyCase = activeCase(diagnosis.caseId(), actor);
        requireCurrentResponsibility(diagnosis, actor);
        List<PreparedItem> preparedItems = command.items().stream()
                .map(item -> prepareItem(item, pathologyCase, actor)).toList();
        UUID orderId = UUID.randomUUID();
        if (!repository.insertIdempotency(operation, command.idempotencyKey(), digest, "TECHNICAL_ORDER", orderId,
                actor.actorId(), Instant.now())) {
            return replayAfterReservation(operation, command.idempotencyKey(), digest, actor);
        }
        TechnicalOrder order = TechnicalOrder.pending(orderId,
                repository.allocateOrderNo(actor.hospitalScope(), pathologyCase.id()), diagnosis.id(), pathologyCase.id(),
                command.requiredBeforeSignOut() != null ? command.requiredBeforeSignOut()
                        : preparedItems.stream().anyMatch(item -> item.project().requiredBeforeSignOutDefault()));
        Instant now = Instant.now();
        repository.insertOrder(order, actor.hospitalScope(), now, actor.actorId());
        for (PreparedItem prepared : preparedItems) {
            TechnicalOrderItem item = new TechnicalOrderItem(UUID.randomUUID(), order.id(), prepared.project(),
                    prepared.command().quantity() == null ? 1 : prepared.command().quantity(),
                    normalizeObject(prepared.command().parameters(), "项目参数必须是JSON对象"), prepared.command().note(), 0);
            repository.insertItem(item, actor.hospitalScope(), now, actor.actorId());
            for (ResolvedTarget target : prepared.targets()) {
                repository.insertTarget(new TechnicalOrderTarget(UUID.randomUUID(), item.id(), pathologyCase.id(),
                        target.type(), target.id(), target.displayCode()), actor.hospitalScope(), now, actor.actorId());
            }
        }
        audit.append(operation, TECHNICAL_ORDER, actor, "ALLOWED", "COMPLETED", order.id(), "V2-TECHNICAL-ORDER",
                UUID.randomUUID().toString(), "items=" + preparedItems.size());
        outbox.append("PIS-V2-I04-TECHNICAL-ORDER-CREATED", order.id(), "V2-TECHNICAL-ORDER", order.version(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return orderResult(repository.findOrderSnapshot(order.id(), actor.hospitalScope()).orElseThrow(), false,
                actor.hospitalScope());
    }

    @Transactional
    public TechnicalOrderResult executeOrder(UUID orderId, String idempotencyKey) {
        ActorContext actor = authorization.require(TECHNICAL_EXECUTION);
        requireId(orderId, "技术医嘱ID不能为空");
        requireKey(idempotencyKey);
        String operation = "PIS-V2-I04-TECHNICAL-ORDER-EXECUTE";
        String digest = digest(orderId);
        TechnicalOrderResult replay = replayOrder(operation, idempotencyKey, digest, actor);
        if (replay != null) return replay;
        if (!repository.lockOrder(orderId, actor.hospitalScope())) {
            throw reject("V2-TECHNICAL-ORDER-NOT-FOUND", "技术医嘱不存在或不在当前数据范围");
        }
        OrderSnapshot snapshot = repository.findOrderSnapshot(orderId, actor.hospitalScope()).orElseThrow();
        if (snapshot.derivedStatus() == TechnicalOrderStatus.CANCELLED) {
            throw reject("V2-TECHNICAL-ORDER-CANCELLED", "已取消技术医嘱不能执行");
        }
        if (snapshot.derivedStatus() == TechnicalOrderStatus.COMPLETED) {
            return orderResult(snapshot, false, actor.hospitalScope());
        }
        for (ItemSnapshot itemSnapshot : snapshot.items()) {
            TechnicalProject project = itemSnapshot.item().project();
            if (itemSnapshot.status() == JdbcV2TechnicalOrderRepository.TechnicalItemStatus.COMPLETED) continue;
            submitDeviceAttempt(itemSnapshot, actor);
            if ("SUPPLEMENTARY_GROSSING".equals(project.capabilityCode())) {
                prepareSupplementaryGrossing(itemSnapshot, actor);
                continue;
            }
            for (TargetSnapshot targetSnapshot : itemSnapshot.targets()) {
                for (int occurrence = 1; occurrence <= itemSnapshot.item().quantity(); occurrence++) {
                    if (project.producesSlide()) {
                        createSlideOutput(itemSnapshot, targetSnapshot, null, null, occurrence, actor);
                    }
                }
            }
        }
        TechnicalOrder order = snapshot.order();
        if (order.status() == TechnicalOrderStatus.PENDING) {
            long expectedOrderVersion = order.version();
            Instant now = Instant.now();
            order.syncStatus(TechnicalOrderStatus.EXECUTING);
            if (!repository.updateOrder(order, actor.hospitalScope(), expectedOrderVersion, now,
                    actor.actorId())) {
                throw conflict("技术医嘱版本冲突，开始处理未生效");
            }
        }
        repository.insertIdempotency(operation, idempotencyKey, digest, "TECHNICAL_ORDER", orderId, actor.actorId(),
                Instant.now());
        audit.append(operation, TECHNICAL_EXECUTION, actor, "ALLOWED", "COMPLETED", orderId, "V2-TECHNICAL-ORDER",
                UUID.randomUUID().toString(), "技术医嘱已触发实际产物生成");
        outbox.append("PIS-V2-I04-TECHNICAL-ORDER-EXECUTED", orderId, "V2-TECHNICAL-ORDER", snapshot.order().version(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return orderResult(repository.findOrderSnapshot(orderId, actor.hospitalScope()).orElseThrow(), false,
                actor.hospitalScope());
    }

    @Transactional
    public TechnicalOrderResult enterResult(UUID itemId, EnterResultCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_EXECUTION);
        requireId(itemId, "技术医嘱项目ID不能为空");
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I04-TECHNICAL-RESULT-ENTER";
        String digest = digest(itemId, command.resultData(), command.expectedVersion());
        TechnicalOrderResult replay = replayOrder(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        if (!repository.lockItem(itemId, actor.hospitalScope())) {
            throw reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在或不在当前数据范围");
        }
        OrderSnapshot snapshot = repository.findOrderSnapshotByItemForCommand(itemId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在"));
        ItemSnapshot itemSnapshot = snapshot.items().stream().filter(item -> item.item().id().equals(itemId)).findFirst()
                .orElseThrow();
        if (!itemSnapshot.item().project().requiresResult()) {
            throw reject("V2-TECHNICAL-RESULT-NOT-SUPPORTED", "当前技术项目不产生结构化结果");
        }
        String resultData = normalizeObject(command.resultData(), "技术结果必须是JSON对象");
        Instant now = Instant.now();
        TechnicalOrderItemResult existing = itemSnapshot.result();
        if (existing == null) {
            if (command.expectedVersion() != 0) throw conflict("技术结果版本冲突");
            TechnicalOrderItemResult result = TechnicalOrderItemResult.create(UUID.randomUUID(), itemId,
                    itemSnapshot.item().project().resultSchema(), resultData, now, actor.actorId());
            repository.insertResult(result, actor.hospitalScope());
            repository.insertOutput(UUID.randomUUID(), itemId, null, TechnicalOutputType.RESULT, result.id(), 1, now,
                    actor.actorId());
        } else {
            try {
                existing.update(resultData, command.expectedVersion(), now, actor.actorId());
            } catch (IllegalStateException exception) {
                throw conflict("技术结果版本冲突，请重新读取后重试");
            }
            if (!repository.updateResult(existing, command.expectedVersion(), actor.hospitalScope())) {
                throw conflict("技术结果版本冲突，请重新读取后重试");
            }
        }
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, "TECHNICAL_ORDER", snapshot.order().id(),
                actor.actorId(), now);
        audit.append(operation, TECHNICAL_EXECUTION, actor, "ALLOWED", "COMPLETED", itemId,
                "V2-TECHNICAL-ORDER-ITEM-RESULT", UUID.randomUUID().toString(), "技术结构化结果已录入");
        outbox.append("PIS-V2-I04-TECHNICAL-RESULT-ENTERED", snapshot.order().id(), "V2-TECHNICAL-ORDER",
                snapshot.order().version(), UUID.randomUUID().toString(), digest, actor.actorId());
        return orderResult(repository.findOrderSnapshot(snapshot.order().id(), actor.hospitalScope()).orElseThrow(),
                false, actor.hospitalScope());
    }

    @Transactional
    public TechnicalAcknowledgement acknowledgeResult(UUID itemId) {
        ActorContext actor = authorization.require(DIAGNOSIS_INITIAL);
        requireId(itemId, "技术结果项目ID不能为空");
        if (!repository.itemBelongsToCurrentResponsibility(itemId, actor.actorId(), actor.hospitalScope())) {
            throw reject("V2-TECHNICAL-RESULT-ACK-REJECTED", "只有当前病例责任医生可以确认技术结果");
        }
        Instant now = Instant.now();
        audit.append("PIS-V2-PX02B-TECHNICAL-RESULT-ACK", DIAGNOSIS_INITIAL, actor, "ALLOWED", "COMPLETED",
                itemId, "V2-TECHNICAL-ORDER-ITEM-RESULT", UUID.randomUUID().toString(), "技术结果已查看");
        return new TechnicalAcknowledgement(itemId, actor.actorId(), now);
    }

    @Transactional
    public TechnicalQualityResult evaluateQuality(UUID itemId, TechnicalQualityCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_EXECUTION);
        requireId(itemId, "技术医嘱项目ID不能为空");
        requireText(command.resultCode(), "质控结果不能为空");
        if (!List.of("PASS", "WARNING", "FAIL").contains(command.resultCode())) {
            throw reject("V2-TECHNICAL-QUALITY-RESULT", "质控结果必须为 PASS、WARNING 或 FAIL");
        }
        OrderSnapshot snapshot = repository.findOrderSnapshotByItemForCommand(itemId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在"));
        ItemSnapshot item = snapshot.items().stream().filter(candidate -> candidate.item().id().equals(itemId))
                .findFirst().orElseThrow();
        OutputSnapshot selectedOutput = command.outputId() == null ? null : item.outputs().stream()
                .filter(output -> output.outputId().equals(command.outputId())).findFirst()
                .orElseThrow(() -> reject("V2-TECHNICAL-OUTPUT-NOT-FOUND", "质控目标不属于当前技术医嘱项目"));
        Instant now = Instant.now();
        UUID evaluationId = UUID.randomUUID();
        repository.insertQualityEvaluation(evaluationId, itemId, selectedOutput == null ? null : selectedOutput.id(),
                command.outputId(), command.resultCode(), command.score(), command.note(), now, actor.actorId(),
                actor.hospitalScope());
        audit.append("PIS-V2-I04-TECHNICAL-QUALITY", TECHNICAL_EXECUTION, actor, "ALLOWED", "COMPLETED",
                itemId, "V2-TECHNICAL-QUALITY", UUID.randomUUID().toString(), command.resultCode());
        return new TechnicalQualityResult(evaluationId, itemId, command.outputId(), command.resultCode(), command.score(),
                command.note(), now, actor.actorId());
    }

    @Transactional
    public TechnicalFeeStatusResult updateFeeStatus(UUID itemId, TechnicalFeeStatusCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_EXECUTION);
        requireId(itemId, "技术医嘱项目ID不能为空");
        requireText(command.statusCode(), "费用状态不能为空");
        if (!List.of("NOT_SENT", "PENDING", "SUCCEEDED", "FAILED").contains(command.statusCode())) {
            throw reject("V2-TECHNICAL-FEE-STATUS", "费用状态不受支持");
        }
        if (!repository.lockItem(itemId, actor.hospitalScope())) {
            throw reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在或不在当前数据范围");
        }
        Instant now = Instant.now();
        repository.upsertFeeStatus(itemId, command.statusCode(), command.externalReference(), command.failureReason(),
                now, actor.actorId(), actor.hospitalScope());
        audit.append("PIS-V2-I04-TECHNICAL-FEE-STATUS", TECHNICAL_EXECUTION, actor, "ALLOWED", "COMPLETED",
                itemId, "V2-TECHNICAL-FEE-STATUS", UUID.randomUUID().toString(), command.statusCode());
        return new TechnicalFeeStatusResult(itemId, command.statusCode(), command.externalReference(),
                command.failureReason(), now, actor.actorId());
    }

    @Transactional
    public TechnicalConsumptionResult recordConsumption(UUID itemId, TechnicalConsumptionCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_EXECUTION);
        requireId(itemId, "技术医嘱项目ID不能为空");
        requireId(command.consumableBatchId(), "耗材批次不能为空");
        requireText(command.unitCode(), "耗材单位不能为空");
        requireText(command.reason(), "耗材消耗原因不能为空");
        if (command.quantity() == null || command.quantity().signum() <= 0) {
            throw reject("V2-TECHNICAL-CONSUMPTION-QUANTITY", "耗材消耗数量必须为正数");
        }
        TechnicalOrderItem item = repository.findItem(itemId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在"));
        if (!item.project().consumableRequired()) {
            throw reject("V2-TECHNICAL-CONSUMPTION-NOT-REQUIRED", "当前技术项目未配置耗材消耗");
        }
        if (!repository.consumableBatchInScope(command.consumableBatchId(), actor.hospitalScope())) {
            throw reject("V2-CONSUMABLE-BATCH-NOT-FOUND", "耗材批次不存在或不在当前数据范围");
        }
        Instant now = Instant.now();
        UUID consumptionId = UUID.randomUUID();
        repository.insertConsumption(consumptionId, itemId, command.consumableBatchId(), command.quantity(),
                command.unitCode(), command.reason(), now, actor.actorId(), actor.hospitalScope());
        audit.append("PIS-V2-I04-TECHNICAL-CONSUMPTION", TECHNICAL_EXECUTION, actor, "ALLOWED", "COMPLETED",
                itemId, "V2-TECHNICAL-CONSUMPTION", UUID.randomUUID().toString(), command.reason());
        return new TechnicalConsumptionResult(consumptionId, itemId, command.consumableBatchId(), command.quantity(),
                command.unitCode(), now, actor.actorId());
    }

    @Transactional
    public TechnicalLabelPrintResult printLabel(UUID itemId, TechnicalLabelPrintCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_EXECUTION);
        requireId(itemId, "技术医嘱项目ID不能为空");
        requireId(command.outputId(), "标签目标不能为空");
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I04-TECHNICAL-LABEL-PRINT";
        String digest = digest(itemId, command.outputId(), command.reason());
        IdempotencyResult existing = repository.findIdempotency(operation, command.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "标签打印幂等摘要冲突");
            JdbcV2TechnicalOrderRepository.LabelPrintSnapshot prior = repository
                    .findLabelPrint(existing.resultEntityId(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "标签打印幂等结果对应记录不存在"));
            return new TechnicalLabelPrintResult(prior.id(), prior.itemId(), prior.outputId(), prior.printVersion(),
                    prior.resultCode(), true, prior.failureReason());
        }
        if (!repository.lockItem(itemId, actor.hospitalScope())) {
            throw reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在或不在当前数据范围");
        }
        OrderSnapshot snapshot = repository.findOrderSnapshotByItemForCommand(itemId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-ITEM-NOT-FOUND", "技术医嘱项目不存在"));
        ItemSnapshot item = snapshot.items().stream().filter(candidate -> candidate.item().id().equals(itemId))
                .findFirst().orElseThrow();
        OutputSnapshot output = item.outputs().stream().filter(candidate -> candidate.outputId().equals(command.outputId()))
                .findFirst().orElseThrow(() -> reject("V2-TECHNICAL-OUTPUT-NOT-FOUND", "标签目标不属于当前技术医嘱项目"));
        String labelCode;
        String entityKind;
        if (output.kind() == TechnicalOutputType.SLIDE) {
            Slide slide = materialRepository.findSlide(output.outputId(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-TECHNICAL-OUTPUT-NOT-FOUND", "技术玻片不存在"));
            labelCode = slide.slideCode();
            entityKind = "SLIDE";
        } else if (output.kind() == TechnicalOutputType.BLOCK) {
            Block block = materialRepository.findBlock(output.outputId(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-TECHNICAL-OUTPUT-NOT-FOUND", "技术蜡块不存在"));
            labelCode = block.blockCode();
            entityKind = "BLOCK";
        } else {
            throw reject("V2-TECHNICAL-LABEL-TARGET", "只有蜡块或玻片产物可以打印标签");
        }
        LabelPrintService.PrintResult print = labelPrintService.print(new LabelPrintService.PrintRequest(entityKind,
                command.outputId(), labelCode, "MOCK://SYNTH-PRINTER", labelCode, actor.actorId()));
        Instant now = Instant.now();
        int printVersion = repository.nextLabelPrintVersion(itemId, command.outputId(), actor.hospitalScope());
        UUID printId = UUID.randomUUID();
        repository.insertLabelPrint(printId, itemId, output.id(), command.outputId(), entityKind, printVersion,
                labelCode, print.resultCode(), print.failureReason(), now, actor.actorId(), actor.hospitalScope());
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, "TECHNICAL_LABEL_PRINT", printId,
                actor.actorId(), now);
        audit.append(operation, TECHNICAL_EXECUTION, actor, "ALLOWED", print.resultCode(), command.outputId(),
                "V2-TECHNICAL-LABEL", UUID.randomUUID().toString(), print.failureReason() == null ? labelCode : print.failureReason());
        return new TechnicalLabelPrintResult(printId, itemId, command.outputId(), printVersion, print.resultCode(),
                false, print.failureReason());
    }

    @Transactional
    public TechnicalOrderResult cancelOrder(UUID orderId, CancelOrderCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_ORDER);
        requireId(orderId, "技术医嘱ID不能为空");
        requireText(command.reason(), "取消原因不能为空");
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I04-TECHNICAL-ORDER-CANCEL";
        String digest = digest(orderId, command.reason(), command.expectedVersion());
        TechnicalOrderResult replay = replayOrder(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        if (!repository.lockOrder(orderId, actor.hospitalScope())) {
            throw reject("V2-TECHNICAL-ORDER-NOT-FOUND", "技术医嘱不存在或不在当前数据范围");
        }
        OrderSnapshot snapshot = repository.findOrderSnapshot(orderId, actor.hospitalScope()).orElseThrow();
        if (snapshot.order().version() != command.expectedVersion()) throw conflict("技术医嘱版本冲突");
        TechnicalOrder order = snapshot.order();
        if (snapshot.derivedStatus() == TechnicalOrderStatus.COMPLETED) {
            throw reject("V2-TECHNICAL-ORDER-CANCEL-REJECTED", "Completed technical order cannot be cancelled");
        }
        try {
            order.cancel(actor.actorId(), command.reason(), Instant.now());
        } catch (IllegalStateException exception) {
            throw reject("V2-TECHNICAL-ORDER-CANCEL-REJECTED", exception.getMessage());
        }
        if (!repository.updateOrder(order, actor.hospitalScope(), command.expectedVersion(), Instant.now(),
                actor.actorId())) throw conflict("技术医嘱版本冲突，取消未生效");
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, "TECHNICAL_ORDER", orderId,
                actor.actorId(), Instant.now());
        audit.append(operation, TECHNICAL_ORDER, actor, "ALLOWED", "COMPLETED", orderId, "V2-TECHNICAL-ORDER",
                UUID.randomUUID().toString(), command.reason());
        outbox.append("PIS-V2-I04-TECHNICAL-ORDER-CANCELLED", orderId, "V2-TECHNICAL-ORDER", order.version(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return orderResult(repository.findOrderSnapshot(orderId, actor.hospitalScope()).orElseThrow(), false,
                actor.hospitalScope());
    }

    @Transactional(readOnly = true)
    public TechnicalOrderResult getOrder(UUID orderId) {
        ActorContext actor = authorization.require(TECHNICAL_QUERY);
        return orderResult(repository.findOrderSnapshot(orderId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-ORDER-NOT-FOUND", "技术医嘱不存在或不在当前数据范围")),
                false, actor.hospitalScope());
    }

    @Transactional(readOnly = true)
    public List<TechnicalOrderResult> diagnosisOrders(UUID diagnosisId) {
        ActorContext actor = authorization.require(TECHNICAL_QUERY);
        return repository.findOrderSnapshotsByDiagnosis(diagnosisId, actor.hospitalScope()).stream()
                .map(snapshot -> orderResult(snapshot, false, actor.hospitalScope())).toList();
    }

    /** Shared sign-out gate; the report module must not duplicate I04's blocking projection. */
    public boolean hasBlockingTechnicalOrders(UUID diagnosisId, String organizationReference) {
        return repository.findOrderSnapshotsByDiagnosis(diagnosisId, organizationReference).stream()
                .anyMatch(OrderSnapshot::blocking);
    }

    @Transactional(readOnly = true)
    public WorkbenchResult workbench() {
        ActorContext actor = authorization.require(TECHNICAL_QUERY);
        return new WorkbenchResult(repository.findWorkbenchSnapshots(actor.hospitalScope()).stream()
                .map(snapshot -> orderResult(snapshot, false, actor.hospitalScope())).toList());
    }

    private PreparedItem prepareItem(CreateItemCommand command, Case pathologyCase, ActorContext actor) {
        requireId(command.projectId(), "技术项目ID不能为空");
        TechnicalProject project = repository.findProject(command.projectId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-PROJECT-NOT-FOUND", "技术项目不存在或不在当前数据范围"));
        if (!project.enabled()) throw reject("V2-TECHNICAL-PROJECT-DISABLED", "技术项目已停用");
        if (!project.businessTypeId().equals(pathologyCase.businessTypeId())) {
            throw reject("V2-TECHNICAL-PROJECT-MISMATCH", "技术项目不适用于当前业务类型");
        }
        if (command.quantity() != null && command.quantity() < 1) throw reject("V2-INVALID-REQUEST", "项目数量必须为正数");
        if (command.targets() == null || command.targets().isEmpty()) {
            throw reject("V2-TECHNICAL-TARGET-REQUIRED", "技术医嘱项目至少需要一个目标");
        }
        List<ResolvedTarget> targets = command.targets().stream().map(target -> resolveTarget(target, project,
                pathologyCase, actor)).toList();
        return new PreparedItem(command, project, targets);
    }

    private ResolvedTarget resolveTarget(TargetCommand command, TechnicalProject project, Case pathologyCase,
            ActorContext actor) {
        if (command == null || command.targetType() == null || command.targetId() == null) {
            throw reject("V2-TECHNICAL-TARGET-INVALID", "技术目标类型和ID不能为空");
        }
        if (!project.supportsTarget(command.targetType())) {
            throw reject("V2-TECHNICAL-TARGET-TYPE", "技术项目不支持当前目标类型");
        }
        UUID caseId = pathologyCase.id();
        String displayCode;
        switch (command.targetType()) {
            case CASE -> {
                if (!caseId.equals(command.targetId())) throw reject("V2-TECHNICAL-CROSS-CASE", "病例目标不属于当前病例");
                displayCode = pathologyCase.caseNo();
            }
            case SPECIMEN -> {
                Specimen specimen = registrationRepository.findSpecimen(command.targetId(), actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-TECHNICAL-TARGET-NOT-FOUND", "标本目标不存在"));
                if (!caseId.equals(specimen.caseId()) || specimen.deleted()) throw crossCase();
                displayCode = specimen.specimenCode();
            }
            case BLOCK -> {
                Block block = materialRepository.findBlock(command.targetId(), actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-TECHNICAL-TARGET-NOT-FOUND", "蜡块目标不存在"));
                if (!caseId.equals(block.caseId()) || block.isDeleted()) throw crossCase();
                displayCode = block.blockCode();
            }
            case SLIDE -> {
                Slide slide = materialRepository.findSlide(command.targetId(), actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-TECHNICAL-TARGET-NOT-FOUND", "切片目标不存在"));
                if (!caseId.equals(slide.caseId()) || slide.isDeleted()) throw crossCase();
                displayCode = slide.slideCode();
            }
            default -> throw reject("V2-TECHNICAL-TARGET-TYPE", "不支持的技术目标类型");
        }
        return new ResolvedTarget(command.targetType(), command.targetId(), displayCode);
    }

    private void prepareSupplementaryGrossing(ItemSnapshot itemSnapshot, ActorContext actor) {
        TechnicalOrderItem item = itemSnapshot.item();
        if (itemSnapshot.outputs().stream().anyMatch(output -> output.kind() == TechnicalOutputType.GROSSING)) return;
        JsonNode parameters = objectNode(item.parameters());
        java.util.LinkedHashSet<UUID> specimenIds = new java.util.LinkedHashSet<>();
        UUID parameterSpecimenId = parameterUuid(parameters, "specimenId");
        for (TargetSnapshot targetSnapshot : itemSnapshot.targets()) {
            UUID specimenId = parameterSpecimenId;
            if (targetSnapshot.target().targetType() == TechnicalTargetType.SPECIMEN) {
                specimenId = targetSnapshot.target().targetId();
            } else if (targetSnapshot.target().targetType() == TechnicalTargetType.BLOCK) {
                Block source = materialRepository.findBlock(targetSnapshot.target().targetId(), actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-TECHNICAL-TARGET-NOT-FOUND", "材块目标不存在"));
                specimenId = source.specimenId();
            }
            if (specimenId != null) specimenIds.add(specimenId);
        }
        if (specimenIds.isEmpty()) throw reject("V2-TECHNICAL-SPECIMEN-REQUIRED", "补充取材必须明确来源标本");
        if (itemSnapshot.targets().isEmpty()) throw reject("V2-TECHNICAL-TARGET-NOT-FOUND", "补充取材缺少目标");
        UUID caseId = itemSnapshot.targets().get(0).target().caseId();
        Instant now = Instant.now();
        UUID grossingId = UUID.randomUUID();
        Grossing grossing = Grossing.open(grossingId, caseId,
                materialRepository.allocateGrossingNo(actor.hospitalScope(), caseId), Grossing.TECHNICAL_ORDER,
                item.id(), textParameter(parameters, "grossDescription", "技术补充取材-" + item.project().name()),
                textParameter(parameters, "grossingInstruction", null), actor.actorId(), actor.actorId(), now);
        materialRepository.insertGrossing(grossing, actor.hospitalScope(), actor.actorId(), now);
        for (UUID specimenId : specimenIds) {
            Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-TECHNICAL-SPECIMEN-NOT-FOUND", "补充取材来源标本不存在"));
            if (!specimen.caseId().equals(caseId)) throw crossCase();
            materialRepository.insertGrossingSpecimen(grossingId, specimen.id(),
                    materialRepository.nextGrossingSpecimenSequence(grossingId), "待补充取材描述");
        }
        recordOutput(item.id(), itemSnapshot.targets().get(0).target().id(), TechnicalOutputType.GROSSING,
                grossingId, 1, actor);
    }

    private void createSlideOutput(ItemSnapshot itemSnapshot, TargetSnapshot targetSnapshot, UUID forcedBlockId,
            UUID ignored, int occurrence, ActorContext actor) {
        TechnicalOrderItem item = itemSnapshot.item();
        Block block = forcedBlockId == null && targetSnapshot.target().targetType() == TechnicalTargetType.BLOCK
                ? materialRepository.findBlock(targetSnapshot.target().targetId(), actor.hospitalScope()).orElse(null)
                : null;
        Slide sourceSlide = forcedBlockId == null && targetSnapshot.target().targetType() == TechnicalTargetType.SLIDE
                ? materialRepository.findSlide(targetSnapshot.target().targetId(), actor.hospitalScope()).orElse(null) : null;
        UUID blockId = forcedBlockId != null ? forcedBlockId : block == null ? sourceSlide == null ? null : sourceSlide.blockId()
                : block.id();
        UUID specimenId = block == null ? sourceSlide == null ? targetSnapshot.target().targetType() == TechnicalTargetType.SPECIMEN
                ? targetSnapshot.target().targetId() : null : sourceSlide.specimenId() : block.specimenId();
        if (blockId == null && specimenId == null) throw reject("V2-TECHNICAL-MATERIAL-TARGET", "技术切片目标缺少材料来源");
        String sourceCode = targetSnapshot.target().displayCode();
        String slideCode = (sourceCode + "-" + item.project().code() + "-" + item.id().toString().substring(0, 8)
                + (occurrence == 1 ? "" : "-" + occurrence)).replaceAll("[^A-Za-z0-9_-]", "-");
        if (slideCode.length() > 120) slideCode = slideCode.substring(0, 120);
        if (blockId != null && materialRepository.slideOutputExists(blockId, Slide.TECHNICAL_ORDER, item.id(),
                item.project().code(), occurrence)) return;
        Slide slide = Slide.technicalFromTarget(UUID.randomUUID(), targetSnapshot.target().caseId(), blockId, specimenId,
                slideCode, item.project().defaultSlideType() == null ? "TECHNICAL" : item.project().defaultSlideType(),
                item.id(), item.project().code(), occurrence, true);
        materialRepository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), Instant.now());
        recordOutput(item.id(), targetSnapshot.target().id(), TechnicalOutputType.SLIDE, slide.id(), occurrence, actor);
    }

    private void recordOutput(UUID itemId, UUID targetId, TechnicalOutputType outputType, UUID outputId, int occurrence,
            ActorContext actor) {
        repository.insertOutput(UUID.randomUUID(), itemId, targetId, outputType, outputId, occurrence, Instant.now(),
                actor.actorId());
    }

    private void submitDeviceAttempt(ItemSnapshot itemSnapshot, ActorContext actor) {
        TechnicalProject project = itemSnapshot.item().project();
        if (project.deviceTypeCode() == null) return;
        Instant requestedAt = Instant.now();
        String requestReference = "TECH-DEVICE-" + UUID.randomUUID();
        try {
            IhcDevicePort.Submission submission = ihcDevice.submit(new IhcDevicePort.Request(itemSnapshot.item().id(),
                    project.code(), project.deviceTypeCode(), itemSnapshot.item().parameters(), actor.actorId()));
            String effectiveReference = submission.requestReference() == null || submission.requestReference().isBlank()
                    ? requestReference : submission.requestReference();
            repository.insertDeviceAttempt(UUID.randomUUID(), itemSnapshot.item().id(), project.deviceTypeCode(),
                    submission.adapterCode(), effectiveReference, submission.statusCode(), 0,
                    submission.errorCode(), submission.errorMessage(), requestedAt, submission.completedAt(),
                    actor.hospitalScope());
            audit.append("PIS-V2-I04-DEVICE-SUBMIT", TECHNICAL_EXECUTION, actor, "ALLOWED",
                    submission.succeeded() ? "COMPLETED" : "FAILED", itemSnapshot.item().id(),
                    "V2-TECHNICAL-DEVICE-ATTEMPT", UUID.randomUUID().toString(),
                    submission.errorMessage() == null ? effectiveReference : submission.errorMessage());
        } catch (RuntimeException exception) {
            repository.insertDeviceAttempt(UUID.randomUUID(), itemSnapshot.item().id(), project.deviceTypeCode(),
                    "UNAVAILABLE", requestReference, "FAILED", 0, "DEVICE_ADAPTER_FAILURE",
                    exception.getMessage(), requestedAt, Instant.now(), actor.hospitalScope());
            audit.append("PIS-V2-I04-DEVICE-SUBMIT", TECHNICAL_EXECUTION, actor, "ALLOWED", "FAILED",
                    itemSnapshot.item().id(), "V2-TECHNICAL-DEVICE-ATTEMPT", UUID.randomUUID().toString(),
                    exception.getMessage() == null ? "设备适配器调用失败" : exception.getMessage());
        }
    }

    private TechnicalOrderResult replayOrder(String operation, String key, String digest, ActorContext actor) {
        IdempotencyResult existing = repository.findIdempotency(operation, key).orElse(null);
        if (existing == null) return null;
        if (!existing.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "技术医嘱幂等摘要冲突");
        if (existing.resultEntityId() == null) throw reject("V2-IDEMPOTENCY-INVALID", "技术医嘱幂等结果缺少主体");
        return orderResult(repository.findOrderSnapshot(existing.resultEntityId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "技术医嘱幂等结果对应主体不存在")),
                true, actor.hospitalScope());
    }

    private TechnicalOrderResult replayAfterReservation(String operation, String key, String digest,
            ActorContext actor) {
        return replayOrder(operation, key, digest, actor);
    }

    private TechnicalOrderResult orderResult(OrderSnapshot snapshot, boolean duplicate, String organizationReference) {
        Case pathologyCase = registrationRepository.findCase(snapshot.order().caseId(), organizationReference)
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "技术医嘱病例不存在或不在当前数据范围"));
        return new TechnicalOrderResult(snapshot.order().id(), snapshot.order().orderNo(), snapshot.order().diagnosisId(),
                snapshot.order().caseId(), pathologyCase.caseNo(), pathologyCase.patientReference(),
                snapshot.derivedStatus(), snapshot.order().requiredBeforeSignOut(),
                snapshot.blocking(), snapshot.order().version(), snapshot.order().cancelledAt(),
                snapshot.order().cancellationReason(), snapshot.items().stream().map(item -> new ItemResult(
                item.item().id(), item.item().project().id(), item.item().project().code(), item.item().project().name(),
                item.item().project().capabilityCode(), item.item().project().outputTypeCode(),
                item.item().project().requiresResult(), item.item().project().deviceTypeCode(),
                item.item().project().consumableRequired(), item.item().quantity(), item.status().name(),
                item.expectedCount(), item.completedCount(),
                item.targets().stream().map(target -> new TargetResult(target.target().id(), target.target().targetType(),
                target.target().targetId(), target.target().displayCode())).toList(),
                item.outputs().stream().map(output -> new OutputResult(output.kind(), output.outputId(),
                output.occurrenceNo())).toList(), item.result() == null ? null : new ResultView(item.result().id(),
                item.result().data(), item.result().version(), item.result().enteredAt()))).toList(), duplicate);
    }

    private ProjectResult projectResult(TechnicalProject project) {
        return new ProjectResult(project.id(), project.businessTypeId(), project.code(), project.name(),
                project.capabilityCode(), project.outputTypeCode(), project.enabled(),
                project.allowedTargetTypes().stream().map(Enum::name).sorted().toList(), project.producesSlide(),
                project.producesBlock(), project.producesStructuredResult(), project.requiresResult(),
                project.deviceTypeCode(), project.consumableRequired(), project.defaultSlideType(),
                project.parametersSchema(), project.resultSchema(),
                project.requiredBeforeSignOutDefault(), project.configurationVersion());
    }

    private void requireCurrentResponsibility(Diagnosis diagnosis, ActorContext actor) {
        boolean current = diagnosisRepository.findResponsibilities(diagnosis.id(), actor.hospitalScope()).stream()
                .anyMatch(item -> item.isCurrent() && actor.actorId().equals(item.doctorId()));
        if (!current) throw reject("V2-TECHNICAL-DIAGNOSIS-RESPONSIBILITY", "只有当前诊断责任医生可以开立技术医嘱");
    }

    private Case activeCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) throw reject("V2-CASE-CANCELLED", "已取消病例不能开展技术医嘱");
        return pathologyCase;
    }

    private Case activeOrExistingCase(UUID caseId, ActorContext actor) {
        return registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
    }

    private static UUID parameterUuid(JsonNode parameters, String field) {
        String value = textParameter(parameters, field, null);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { throw reject("V2-TECHNICAL-PARAMETER", field + "必须是UUID"); }
    }

    private static String textParameter(JsonNode parameters, String field, String fallback) {
        JsonNode value = parameters == null ? null : parameters.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private JsonNode objectNode(String value) {
        try { return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value); }
        catch (Exception exception) { throw reject("V2-TECHNICAL-PARAMETER", "项目参数必须是有效JSON对象"); }
    }

    private String normalizeObject(String value, String message) {
        JsonNode node = objectNode(value);
        if (!node.isObject()) throw reject("V2-TECHNICAL-PARAMETER", message);
        return node.toString();
    }

    private static P15BusinessException crossCase() { return reject("V2-TECHNICAL-CROSS-CASE", "技术目标不属于当前病例"); }
    private static P15BusinessException conflict(String message) { return new P15BusinessException("V2-VERSION-CONFLICT", message, 409); }
    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }
    private static void requireId(UUID value, String message) { if (value == null) throw reject("V2-INVALID-REQUEST", message); }
    private static void requireKey(String value) { requireText(value, "幂等键不能为空"); }
    private static void requireText(String value, String message) { if (value == null || value.isBlank()) throw reject("V2-INVALID-REQUEST", message); }

    private static String digest(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256不可用", exception); }
    }

    private record PreparedItem(CreateItemCommand command, TechnicalProject project, List<ResolvedTarget> targets) { }
    private record ResolvedTarget(TechnicalTargetType type, UUID id, String displayCode) { }
    public record CreateProjectCommand(UUID businessTypeId, String projectCode, String projectName,
            String capabilityCode, String outputTypeCode, boolean enabled, String allowedTargetTypes,
            boolean producesSlide, boolean producesBlock, boolean producesStructuredResult, boolean requiresResult,
            String deviceTypeCode, boolean consumableRequired, String defaultSlideType, String parametersSchema,
            String resultSchema, String feeMapping, String displayConfiguration,
            boolean requiredBeforeSignOutDefault, int configurationVersion) { }
    public record CreateTechnicalOrderCommand(UUID diagnosisId, Boolean requiredBeforeSignOut,
            List<CreateItemCommand> items, String idempotencyKey) { }
    public record CreateItemCommand(UUID projectId, Integer quantity, String parameters, String note,
            List<TargetCommand> targets) { }
    public record TargetCommand(TechnicalTargetType targetType, UUID targetId) { }
    public record EnterResultCommand(String resultData, long expectedVersion, String idempotencyKey) { }
    public record CancelOrderCommand(long expectedVersion, String reason, String idempotencyKey) { }

    public record ProjectResult(UUID projectId, UUID businessTypeId, String projectCode, String projectName,
            String capabilityCode, String outputTypeCode, boolean enabled, List<String> allowedTargetTypes,
            boolean producesSlide, boolean producesBlock, boolean producesStructuredResult, boolean requiresResult,
            String deviceTypeCode, boolean consumableRequired, String defaultSlideType, String parametersSchema,
            String resultSchema, boolean requiredBeforeSignOutDefault, int configurationVersion) { }
    public record TechnicalOrderResult(UUID orderId, String orderNo, UUID diagnosisId, UUID caseId,
            String caseNo, String patientReference, TechnicalOrderStatus status, boolean requiredBeforeSignOut,
            boolean blocking, long version,
            Instant cancelledAt, String cancellationReason, List<ItemResult> items, boolean duplicate) { }
    public record ItemResult(UUID itemId, UUID projectId, String projectCode, String projectName,
            String capabilityCode, String outputTypeCode, boolean requiresResult, String deviceTypeCode,
            boolean consumableRequired, int quantity, String status, int expectedCount, int completedCount,
            List<TargetResult> targets, List<OutputResult> outputs,
            ResultView result) { }
    public record TargetResult(UUID targetId, TechnicalTargetType targetType, UUID targetObjectId, String displayCode) { }
    public record OutputResult(TechnicalOutputType outputKind, UUID outputId, int occurrenceNo) { }
    public record ResultView(UUID resultId, String resultData, long version, Instant enteredAt) { }
    public record WorkbenchResult(List<TechnicalOrderResult> orders) { }
    public record TechnicalAcknowledgement(UUID itemId, String acknowledgedBy, Instant acknowledgedAt) { }
    public record TechnicalQualityCommand(UUID outputId, String resultCode, BigDecimal score, String note) { }
    public record TechnicalQualityResult(UUID evaluationId, UUID itemId, UUID outputId, String resultCode,
            BigDecimal score, String note, Instant evaluatedAt, String evaluatedBy) { }
    public record TechnicalFeeStatusCommand(String statusCode, String externalReference, String failureReason) { }
    public record TechnicalFeeStatusResult(UUID itemId, String statusCode, String externalReference,
            String failureReason, Instant updatedAt, String updatedBy) { }
    public record TechnicalConsumptionCommand(UUID consumableBatchId, BigDecimal quantity, String unitCode,
            String reason) { }
    public record TechnicalConsumptionResult(UUID consumptionId, UUID itemId, UUID consumableBatchId,
            BigDecimal quantity, String unitCode, Instant occurredAt, String occurredBy) { }
    public record TechnicalLabelPrintCommand(UUID outputId, String reason, String idempotencyKey) { }
    public record TechnicalLabelPrintResult(UUID printId, UUID itemId, UUID outputId, int printVersion,
            String resultCode, boolean duplicate, String failureReason) { }
}

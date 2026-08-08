package com.hanjisang.pis.v2.technical.application;

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

    private final JdbcV2TechnicalOrderRepository repository;
    private final JdbcV2DiagnosisRepository diagnosisRepository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MaterialRepository materialRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public V2TechnicalOrderApplicationService(JdbcV2TechnicalOrderRepository repository,
            JdbcV2DiagnosisRepository diagnosisRepository, JdbcV2RegistrationRepository registrationRepository,
            JdbcV2MaterialRepository materialRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.diagnosisRepository = diagnosisRepository;
        this.registrationRepository = registrationRepository;
        this.materialRepository = materialRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public ProjectResult createProject(CreateProjectCommand command) {
        ActorContext actor = authorization.require(TECHNICAL_PROJECT);
        requireText(command.projectCode(), "技术项目编码不能为空");
        requireText(command.projectName(), "技术项目名称不能为空");
        requireText(command.allowedTargetTypes(), "技术项目目标类型不能为空");
        TechnicalProject project = TechnicalProject.create(UUID.randomUUID(), actor.hospitalScope(),
                command.businessTypeId(), command.projectCode(), command.projectName(), command.enabled(),
                command.allowedTargetTypes(), command.producesSlide(), command.producesBlock(),
                command.producesStructuredResult(), command.defaultSlideType(), command.parametersSchema(),
                command.resultSchema(), command.feeMapping(), command.displayConfiguration(),
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
        return orderResult(repository.findOrderSnapshot(order.id(), actor.hospitalScope()).orElseThrow(), false);
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
            return orderResult(snapshot, false);
        }
        for (ItemSnapshot itemSnapshot : snapshot.items()) {
            TechnicalProject project = itemSnapshot.item().project();
            if (itemSnapshot.status() == JdbcV2TechnicalOrderRepository.TechnicalItemStatus.COMPLETED) continue;
            for (TargetSnapshot targetSnapshot : itemSnapshot.targets()) {
                for (int occurrence = 1; occurrence <= itemSnapshot.item().quantity(); occurrence++) {
                    if (project.producesBlock()) {
                        MaterialOutput material = createSupplementaryMaterial(itemSnapshot, targetSnapshot, occurrence,
                                actor);
                        recordOutput(itemSnapshot.item().id(), targetSnapshot.target().id(), TechnicalOutputType.GROSSING,
                                material.grossingId(), occurrence, actor);
                        recordOutput(itemSnapshot.item().id(), targetSnapshot.target().id(), TechnicalOutputType.BLOCK,
                                material.blockId(), occurrence, actor);
                        if (project.producesSlide()) {
                            createTechnicalSlide(itemSnapshot, targetSnapshot, material.blockId(), null, occurrence,
                                    actor);
                        }
                    } else if (project.producesSlide()) {
                        createTechnicalSlide(itemSnapshot, targetSnapshot, null, null, occurrence, actor);
                    }
                }
            }
        }
        repository.insertIdempotency(operation, idempotencyKey, digest, "TECHNICAL_ORDER", orderId, actor.actorId(),
                Instant.now());
        audit.append(operation, TECHNICAL_EXECUTION, actor, "ALLOWED", "COMPLETED", orderId, "V2-TECHNICAL-ORDER",
                UUID.randomUUID().toString(), "技术医嘱已触发实际产物生成");
        outbox.append("PIS-V2-I04-TECHNICAL-ORDER-EXECUTED", orderId, "V2-TECHNICAL-ORDER", snapshot.order().version(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return orderResult(repository.findOrderSnapshot(orderId, actor.hospitalScope()).orElseThrow(), false);
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
        if (!itemSnapshot.item().project().producesStructuredResult()) {
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
        return orderResult(repository.findOrderSnapshot(snapshot.order().id(), actor.hospitalScope()).orElseThrow(), false);
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
        return orderResult(repository.findOrderSnapshot(orderId, actor.hospitalScope()).orElseThrow(), false);
    }

    @Transactional(readOnly = true)
    public TechnicalOrderResult getOrder(UUID orderId) {
        ActorContext actor = authorization.require(TECHNICAL_QUERY);
        return orderResult(repository.findOrderSnapshot(orderId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-ORDER-NOT-FOUND", "技术医嘱不存在或不在当前数据范围")), false);
    }

    @Transactional(readOnly = true)
    public List<TechnicalOrderResult> diagnosisOrders(UUID diagnosisId) {
        ActorContext actor = authorization.require(TECHNICAL_QUERY);
        return repository.findOrderSnapshotsByDiagnosis(diagnosisId, actor.hospitalScope()).stream()
                .map(snapshot -> orderResult(snapshot, false)).toList();
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
                .map(snapshot -> orderResult(snapshot, false)).toList());
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

    private MaterialOutput createSupplementaryMaterial(ItemSnapshot itemSnapshot, TargetSnapshot targetSnapshot,
            int occurrence, ActorContext actor) {
        TechnicalOrderItem item = itemSnapshot.item();
        JsonNode parameters = objectNode(item.parameters());
        UUID specimenId = parameterUuid(parameters, "specimenId");
        if (targetSnapshot.target().targetType() == TechnicalTargetType.SPECIMEN) specimenId = targetSnapshot.target().targetId();
        if (targetSnapshot.target().targetType() == TechnicalTargetType.BLOCK) {
            Block source = materialRepository.findBlock(targetSnapshot.target().targetId(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-TECHNICAL-TARGET-NOT-FOUND", "蜡块目标不存在"));
            specimenId = source.specimenId();
        }
        if (specimenId == null) throw reject("V2-TECHNICAL-SPECIMEN-REQUIRED", "补充取材必须明确来源标本");
        Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TECHNICAL-SPECIMEN-NOT-FOUND", "补充取材来源标本不存在"));
        if (!specimen.caseId().equals(targetSnapshot.target().caseId())) throw crossCase();
        UUID caseId = targetSnapshot.target().caseId();
        Instant now = Instant.now();
        UUID grossingId = UUID.randomUUID();
        Grossing grossing = Grossing.open(grossingId, caseId,
                materialRepository.allocateGrossingNo(actor.hospitalScope(), caseId), Grossing.TECHNICAL_ORDER,
                item.id(), textParameter(parameters, "grossDescription", "技术补充取材-" + item.project().name()),
                textParameter(parameters, "grossingInstruction", null), actor.actorId(), actor.actorId(), now);
        materialRepository.insertGrossing(grossing, actor.hospitalScope(), actor.actorId(), now);
        materialRepository.insertGrossingSpecimen(grossingId, specimen.id(),
                materialRepository.nextGrossingSpecimenSequence(grossingId), "技术医嘱补充取材");
        String blockCode = textParameter(parameters, "blockCode", "B-" + item.project().code() + "-"
                + item.id().toString().substring(0, 8) + (occurrence == 1 ? "" : "-" + occurrence));
        if (materialRepository.findActiveBlockIdByCode(caseId, blockCode, actor.hospitalScope()).isPresent()) {
            throw reject("V2-TECHNICAL-BLOCK-CODE-CONFLICT", "技术医嘱生成的蜡块编号已存在");
        }
        Block block = Block.create(UUID.randomUUID(), caseId, grossingId, specimen.id(), blockCode,
                "TECHNICAL_ORDER");
        materialRepository.insertBlock(block, actor.hospitalScope(), actor.actorId(), now);
        grossing.complete(now, actor.actorId());
        if (!materialRepository.saveGrossing(grossing, actor.hospitalScope(), 0, actor.actorId(), now)) {
            throw conflict("补充取材完成时版本冲突");
        }
        return new MaterialOutput(grossingId, block.id());
    }

    private void createTechnicalSlide(ItemSnapshot itemSnapshot, TargetSnapshot targetSnapshot, UUID forcedBlockId,
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

    private TechnicalOrderResult replayOrder(String operation, String key, String digest, ActorContext actor) {
        IdempotencyResult existing = repository.findIdempotency(operation, key).orElse(null);
        if (existing == null) return null;
        if (!existing.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "技术医嘱幂等摘要冲突");
        if (existing.resultEntityId() == null) throw reject("V2-IDEMPOTENCY-INVALID", "技术医嘱幂等结果缺少主体");
        return orderResult(repository.findOrderSnapshot(existing.resultEntityId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "技术医嘱幂等结果对应主体不存在")), true);
    }

    private TechnicalOrderResult replayAfterReservation(String operation, String key, String digest,
            ActorContext actor) {
        return replayOrder(operation, key, digest, actor);
    }

    private TechnicalOrderResult orderResult(OrderSnapshot snapshot, boolean duplicate) {
        return new TechnicalOrderResult(snapshot.order().id(), snapshot.order().orderNo(), snapshot.order().diagnosisId(),
                snapshot.order().caseId(), snapshot.derivedStatus(), snapshot.order().requiredBeforeSignOut(),
                snapshot.blocking(), snapshot.order().version(), snapshot.order().cancelledAt(),
                snapshot.order().cancellationReason(), snapshot.items().stream().map(item -> new ItemResult(
                item.item().id(), item.item().project().id(), item.item().project().code(), item.item().project().name(),
                item.item().quantity(), item.status().name(), item.expectedCount(), item.completedCount(),
                item.targets().stream().map(target -> new TargetResult(target.target().id(), target.target().targetType(),
                target.target().targetId(), target.target().displayCode())).toList(),
                item.outputs().stream().map(output -> new OutputResult(output.kind(), output.outputId(),
                output.occurrenceNo())).toList(), item.result() == null ? null : new ResultView(item.result().id(),
                item.result().data(), item.result().version(), item.result().enteredAt()))).toList(), duplicate);
    }

    private ProjectResult projectResult(TechnicalProject project) {
        return new ProjectResult(project.id(), project.businessTypeId(), project.code(), project.name(),
                project.enabled(), project.allowedTargetTypes().stream().map(Enum::name).sorted().toList(),
                project.producesSlide(), project.producesBlock(), project.producesStructuredResult(),
                project.defaultSlideType(), project.parametersSchema(), project.resultSchema(),
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
    private record MaterialOutput(UUID grossingId, UUID blockId) { }

    public record CreateProjectCommand(UUID businessTypeId, String projectCode, String projectName, boolean enabled,
            String allowedTargetTypes, boolean producesSlide, boolean producesBlock, boolean producesStructuredResult,
            String defaultSlideType, String parametersSchema, String resultSchema, String feeMapping,
            String displayConfiguration, boolean requiredBeforeSignOutDefault, int configurationVersion) { }
    public record CreateTechnicalOrderCommand(UUID diagnosisId, Boolean requiredBeforeSignOut,
            List<CreateItemCommand> items, String idempotencyKey) { }
    public record CreateItemCommand(UUID projectId, Integer quantity, String parameters, String note,
            List<TargetCommand> targets) { }
    public record TargetCommand(TechnicalTargetType targetType, UUID targetId) { }
    public record EnterResultCommand(String resultData, long expectedVersion, String idempotencyKey) { }
    public record CancelOrderCommand(long expectedVersion, String reason, String idempotencyKey) { }

    public record ProjectResult(UUID projectId, UUID businessTypeId, String projectCode, String projectName,
            boolean enabled, List<String> allowedTargetTypes, boolean producesSlide, boolean producesBlock,
            boolean producesStructuredResult, String defaultSlideType, String parametersSchema, String resultSchema,
            boolean requiredBeforeSignOutDefault, int configurationVersion) { }
    public record TechnicalOrderResult(UUID orderId, String orderNo, UUID diagnosisId, UUID caseId,
            TechnicalOrderStatus status, boolean requiredBeforeSignOut, boolean blocking, long version,
            Instant cancelledAt, String cancellationReason, List<ItemResult> items, boolean duplicate) { }
    public record ItemResult(UUID itemId, UUID projectId, String projectCode, String projectName, int quantity,
            String status, int expectedCount, int completedCount, List<TargetResult> targets, List<OutputResult> outputs,
            ResultView result) { }
    public record TargetResult(UUID targetId, TechnicalTargetType targetType, UUID targetObjectId, String displayCode) { }
    public record OutputResult(TechnicalOutputType outputKind, UUID outputId, int occurrenceNo) { }
    public record ResultView(UUID resultId, String resultData, long version, Instant enteredAt) { }
    public record WorkbenchResult(List<TechnicalOrderResult> orders) { }
}

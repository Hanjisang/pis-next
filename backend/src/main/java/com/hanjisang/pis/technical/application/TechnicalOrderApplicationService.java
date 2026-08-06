package com.hanjisang.pis.technical.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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
import com.hanjisang.pis.technical.domain.TechnicalOrder;
import com.hanjisang.pis.technical.domain.TechnicalProjectType;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.BlockTargetSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.ConfigurationSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.OrderListRow;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.OrderSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.PlannedOutputSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.ProjectSnapshot;
import com.hanjisang.pis.technical.infrastructure.JdbcTechnicalOrderRepository.ResultReferenceSnapshot;

@Service
public class TechnicalOrderApplicationService {

    public static final String TASK = "P18-TECHNICAL-ORDER";
    private static final String ORDER_PERMISSION = "P14-PERM-015";
    private static final String PLAN_PERMISSION = "P14-PERM-016";
    private static final String EXECUTION_PERMISSION = "P14-PERM-017";
    private static final String QUEUE_PERMISSION = "P14-PERM-050";
    private static final String ORDER_KIND = "TECHNICAL_ORDER";
    private static final String ORDER_SUBJECT = "P18-TECHNICAL-ORDER";
    private static final String PROJECT_SUBJECT = "P18-TECHNICAL-PROJECT";

    private final JdbcTechnicalOrderRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final String runtimeEnvironment;

    public TechnicalOrderApplicationService(JdbcTechnicalOrderRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox,
            @Value("${pis.runtime-environment:local}") String runtimeEnvironment) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.runtimeEnvironment = runtimeEnvironment;
    }

    public List<OrderListRow> orders() {
        ActorContext actor = authorized(QUEUE_PERMISSION);
        return repository.list(actor.hospitalScope());
    }

    public OrderResult order(UUID orderId) {
        ActorContext actor = authorized(QUEUE_PERMISSION);
        return orderResult(requireOrder(orderId, actor), actor);
    }

    @Transactional
    public OrderResult createOrder(CreateOrderCommand command) {
        ActorContext actor = authorized(ORDER_PERMISSION);
        requireText(command.idempotencyKey());
        if (command.projects() == null || command.projects().isEmpty()) {
            throw business("P12-ERR-034", "技术医嘱至少需要一个具有明确目标的项目");
        }
        String digest = digest(command.caseId() + "|" + command.orderKindCode() + "|" + command.priorityCode() + "|"
                + command.reasonText() + "|" + command.projects());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-CREATE-ORDER",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return orderResult(requireOrder(replay.get().resultObjectId(), actor), actor, true);

        UUID expectedCase = null;
        for (ProjectCommand project : command.projects()) {
            ConfigurationSnapshot configuration = configuration(project);
            BlockTargetSnapshot target = requireTarget(project.actualBlockFormationId(), actor);
            if (!target.caseId().equals(command.caseId())) {
                throw business("P12-ERR-036", "技术医嘱目标与病例不匹配");
            }
            if (expectedCase != null && !expectedCase.equals(target.caseId())) {
                throw business("P12-ERR-036", "同一技术医嘱不能跨病例绑定目标");
            }
            expectedCase = target.caseId();
            if (repository.duplicateCandidate(command.caseId(), target.formationId(), configuration.projectTypeCode(),
                    actor.hospitalScope()).isPresent()) {
                throw business("P12-ERR-034", "存在相同目标和项目类型的未关闭技术医嘱");
            }
        }

        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        OrderSnapshot order = new OrderSnapshot(orderId, "P18-ORDER-" + token(), command.caseId(),
                defaultText(command.orderKindCode(), ORDER_KIND), "DRAFT", defaultText(command.priorityCode(), "ROUTINE"),
                requireTextValue(command.reasonText(), "医嘱原因不能为空"), actor.actorId(),
                defaultText(command.representedActorRef(), actor.actorId()), actor.hospitalScope(), 1, 0, now, actor.actorId());
        repository.createOrder(order);

        int sequence = 1;
        for (ProjectCommand project : command.projects()) {
            ConfigurationSnapshot configuration = configuration(project);
            UUID projectId = UUID.randomUUID();
            ProjectSnapshot projectSnapshot = new ProjectSnapshot(projectId, orderId, "P18-PROJECT-" + token(),
                    configuration.id(), configuration.projectTypeCode(), "P08-SM-007-ST-01", "PENDING", "NOT_RECEIVED",
                    "NOT_HANDOFF", "WAITING", requireTextValue(project.usageCode(), "项目用途不能为空"),
                    defaultText(project.priorityCode(), order.priorityCode()), requireTextValue(project.reasonText(), "项目原因不能为空"),
                    null, 1, 0, command.caseId(), actor.hospitalScope());
            repository.createProject(projectSnapshot, configuration, now, actor.actorId());
            repository.bindTarget(projectId, command.caseId(), project.actualBlockFormationId(), 1, "VALID", actor.actorId(),
                    "P18 initial target binding", "INITIAL_BIND", now);
            appendPlannedOutputs(projectId, project.plannedOutputs(), actor, now, sequence++);
            audit(projectId, "P18-CMD-CREATE-PROJECT", ORDER_PERMISSION, actor, "technical project created");
            publish("P12-API-015", projectId, PROJECT_SUBJECT, 0, actor, configuration.configurationDigest());
        }
        repository.recordIdempotent("P18-CREATE-ORDER", command.idempotencyKey(), digest, orderId, actor.actorId(), now);
        auditOrder(orderId, "P18-CMD-CREATE-ORDER", ORDER_PERMISSION, actor, "technical order created");
        publish("P12-API-015", orderId, ORDER_SUBJECT, 0, actor, order.orderNo());
        return orderResult(requireOrder(orderId, actor), actor);
    }

    @Transactional
    public ProjectResult addProject(UUID orderId, AddProjectCommand command) {
        ActorContext actor = authorized(ORDER_PERMISSION);
        requireText(command.idempotencyKey());
        OrderSnapshot order = requireOrder(orderId, actor);
        TechnicalOrder.requireDraft(order.orderLifecycleStateCode());
        ConfigurationSnapshot configuration = configuration(command.project());
        BlockTargetSnapshot target = requireTarget(command.project().actualBlockFormationId(), actor);
        requireSameCase(order.caseId(), target.caseId());
        if (repository.duplicateCandidate(order.caseId(), target.formationId(), configuration.projectTypeCode(), actor.hospitalScope()).isPresent()) {
            throw business("P12-ERR-034", "存在相同目标和项目类型的未关闭技术医嘱");
        }
        String digest = digest(orderId + "|" + command.project());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-ADD-PROJECT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(replay.get().resultObjectId(), actor), actor, true);
        Instant now = Instant.now();
        ProjectSnapshot project = new ProjectSnapshot(UUID.randomUUID(), orderId, "P18-PROJECT-" + token(), configuration.id(),
                configuration.projectTypeCode(), "P08-SM-007-ST-01", "PENDING", "NOT_RECEIVED", "NOT_HANDOFF", "WAITING",
                requireTextValue(command.project().usageCode(), "项目用途不能为空"), defaultText(command.project().priorityCode(), order.priorityCode()),
                requireTextValue(command.project().reasonText(), "项目原因不能为空"), null, 1, 0, order.caseId(), actor.hospitalScope());
        repository.createProject(project, configuration, now, actor.actorId());
        repository.bindTarget(project.id(), order.caseId(), target.formationId(), 1, "VALID", actor.actorId(), "appended project target",
                "APPEND", now);
        appendPlannedOutputs(project.id(), command.project().plannedOutputs(), actor, now, 1);
        repository.recordIdempotent("P18-ADD-PROJECT", command.idempotencyKey(), digest, project.id(), actor.actorId(), now);
        audit(project.id(), "P18-CMD-ADD-PROJECT", ORDER_PERMISSION, actor, "project appended");
        publish("P12-API-015", project.id(), PROJECT_SUBJECT, 0, actor, configuration.configurationDigest());
        return projectResult(requireProject(project.id(), actor), actor, false);
    }

    @Transactional
    public ProjectResult bindTarget(UUID projectId, BindTargetCommand command) {
        ActorContext actor = authorized(ORDER_PERMISSION);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        OrderSnapshot order = requireOrder(project.orderId(), actor);
        TechnicalOrder.requireDraft(order.orderLifecycleStateCode());
        if (!"P08-SM-007-ST-01".equals(project.projectTaskStateCode())) {
            throw business("P12-ERR-036", "项目提交后不能普通更换目标");
        }
        BlockTargetSnapshot target = requireTarget(command.actualBlockFormationId(), actor);
        requireSameCase(project.caseId(), target.caseId());
        String digest = digest(projectId + "|" + command.actualBlockFormationId() + "|" + command.reasonText());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-BIND-TARGET",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        int nextTargetVersion = repository.target(projectId).map(value -> value.targetVersionNo() + 1).orElse(1);
        repository.bindTarget(projectId, project.caseId(), target.formationId(), nextTargetVersion, "CORRECTED", actor.actorId(),
                requireTextValue(command.reasonText(), "目标变更原因不能为空"), "CORRECT_TARGET", now);
        repository.appendChange(projectId, "TARGET_CORRECTION", project.recordVersionNo(), command.reasonText(), actor.actorId(), now);
        repository.recordIdempotent("P18-BIND-TARGET", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-CORRECT-TARGET", ORDER_PERMISSION, actor, "target corrected");
        publish("P12-EVC-021", projectId, PROJECT_SUBJECT, project.concurrencyVersion(), actor, "target-correction");
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public ProjectResult addPlannedOutput(UUID projectId, PlannedOutputCommand command) {
        ActorContext actor = authorized(PLAN_PERMISSION);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        OrderSnapshot order = requireOrder(project.orderId(), actor);
        TechnicalOrder.requireDraft(order.orderLifecycleStateCode());
        validateOutput(command);
        String digest = digest(projectId + "|" + command.sequenceNo() + "|" + command.outputKindCode() + "|"
                + command.plannedQuantity() + "|" + command.plannedStainProjectCode());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-ADD-OUTPUT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        repository.addPlannedOutput(new PlannedOutputSnapshot(UUID.randomUUID(), projectId, command.sequenceNo(), command.outputKindCode(),
                command.slidePurposeCode(), command.plannedLayerReference(), command.plannedQuantity(), command.plannedStainProjectCode(),
                command.plannedUsageCode(), command.plannedLabelQuantity(), command.executionNote(), actor.actorId()), now);
        repository.appendChange(projectId, "PLANNED_OUTPUT_ADDED", project.recordVersionNo(), "planned output appended", actor.actorId(), now);
        repository.recordIdempotent("P18-ADD-OUTPUT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-ADD-PLANNED-OUTPUT", PLAN_PERMISSION, actor, "planned output added");
        publish("P12-API-016", projectId, PROJECT_SUBJECT, project.concurrencyVersion(), actor, digest);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public OrderResult submit(UUID orderId, VersionCommand command) {
        ActorContext actor = authorized(ORDER_PERMISSION);
        requireText(command.idempotencyKey());
        OrderSnapshot order = requireOrder(orderId, actor);
        List<ProjectSnapshot> projects = repository.projects(orderId, actor.hospitalScope());
        if (projects.isEmpty() || projects.stream().anyMatch(project -> repository.target(project.id()).isEmpty())) {
            throw business("P12-ERR-034", "所有技术医嘱项目都必须绑定有效目标");
        }
        if (projects.stream().anyMatch(project -> repository.plannedOutputs(project.id()).isEmpty())) {
            throw business("P12-ERR-035", "所有技术医嘱项目都必须有计划产物");
        }
        String digest = digest(orderId + "|" + command.expectedVersion());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-SUBMIT-ORDER",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return orderResult(requireOrder(orderId, actor), actor, true);
        Instant now = Instant.now();
        for (ProjectSnapshot project : projects) {
            if (!"P08-SM-007-ST-01".equals(project.projectTaskStateCode())) continue;
            if (!repository.submitProject(project.id(), actor.hospitalScope(), project.concurrencyVersion(), now, actor.actorId())) {
                throw versionConflict();
            }
            repository.appendProjectState(project.id(), project.projectTaskStateCode(), "P08-SM-007-ST-02", "P18-SUBMIT",
                    project.concurrencyVersion(), project.concurrencyVersion() + 1, actor.actorId(), "technical order submitted", now);
        }
        if (!repository.submitOrder(orderId, actor.hospitalScope(), command.expectedVersion(), now, actor.actorId())) {
            throw versionConflict();
        }
        repository.recordIdempotent("P18-SUBMIT-ORDER", command.idempotencyKey(), digest, orderId, actor.actorId(), now);
        auditOrder(orderId, "P18-CMD-SUBMIT-ORDER", ORDER_PERMISSION, actor, "technical order submitted");
        publish("P12-API-015", orderId, ORDER_SUBJECT, order.concurrencyVersion() + 1, actor, digest);
        return orderResult(requireOrder(orderId, actor), actor, false);
    }

    @Transactional
    public ProjectResult review(UUID projectId, ReviewCommand command) {
        ActorContext actor = authorized(ORDER_PERMISSION);
        requireHuman(actor);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        requireTextValue(command.reasonText(), "审核原因不能为空");
        String digest = digest(projectId + "|" + command.decisionCode() + "|" + command.reasonText() + "|" + command.expectedVersion());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-REVIEW-PROJECT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        boolean updated = "APPROVED".equals(command.decisionCode())
                ? repository.approveProject(projectId, actor.hospitalScope(), command.expectedVersion(), actor.actorId(), now)
                : "REJECTED".equals(command.decisionCode())
                        && repository.rejectProject(projectId, actor.hospitalScope(), command.expectedVersion(), now);
        if (!updated) throw versionConflict();
        repository.appendReview(projectId, command.decisionCode(), command.reasonText(), actor.actorId(),
                (int) command.expectedVersion() + 1, now);
        repository.appendProjectState(projectId, project.projectTaskStateCode(), project.projectTaskStateCode(), "P18-REVIEW",
                command.expectedVersion(), command.expectedVersion() + 1, actor.actorId(), command.reasonText(), now);
        repository.recordIdempotent("P18-REVIEW-PROJECT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-REVIEW-PROJECT", ORDER_PERMISSION, actor, "technical project reviewed");
        publish("P12-API-015", projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        repository.refreshDerivedOrderState(project.orderId(), actor.hospitalScope(), actor.actorId(), now);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public ProjectResult receive(UUID projectId, VersionCommand command) {
        ActorContext actor = authorized(QUEUE_PERMISSION);
        return updateProject(projectId, command, "P18-RECEIVE-PROJECT", "P18-CMD-RECEIVE-PROJECT", QUEUE_PERMISSION,
                (project, now) -> repository.receiveProject(project.id(), actor.hospitalScope(), command.expectedVersion(), now),
                "RECEIVED", "P18-RECEIVE", "P12-API-015", actor);
    }

    @Transactional
    public ProjectResult assign(UUID projectId, AssignCommand command) {
        ActorContext actor = authorized(QUEUE_PERMISSION);
        requireHuman(actor);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        String assignedActor = requireTextValue(command.assignedActorRef(), "责任人不能为空");
        String digest = digest(projectId + "|" + assignedActor + "|" + command.expectedVersion());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-ASSIGN-PROJECT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        if (!repository.assignProject(projectId, actor.hospitalScope(), command.expectedVersion(), assignedActor, now)) {
            throw versionConflict();
        }
        repository.appendResponsibility(projectId, "EXECUTION", project.assignedActorRef(), assignedActor, "ASSIGN", command.reasonText(),
                actor.actorId(), now);
        repository.appendProjectState(projectId, project.projectTaskStateCode(), project.projectTaskStateCode(), "P18-ASSIGN",
                command.expectedVersion(), command.expectedVersion() + 1, actor.actorId(), "execution responsibility assigned", now);
        repository.recordIdempotent("P18-ASSIGN-PROJECT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-ASSIGN-PROJECT", QUEUE_PERMISSION, actor, "execution responsibility assigned");
        publish("P12-API-015", projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public ProjectResult takeover(UUID projectId, VersionCommand command) {
        ActorContext actor = authorized(QUEUE_PERMISSION);
        AssignCommand assign = new AssignCommand(actor.actorId(), command.expectedVersion(), command.idempotencyKey(), "task takeover");
        return assign(projectId, assign);
    }

    @Transactional
    public ProjectResult handoff(UUID projectId, VersionCommand command) {
        ActorContext actor = authorized(EXECUTION_PERMISSION);
        requireHuman(actor);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        if (project.assignedActorRef() == null || !actor.actorId().equals(project.assignedActorRef())) {
            throw business("P12-ERR-077", "当前主体不是该技术医嘱项目责任人");
        }
        String digest = digest(projectId + "|" + command.expectedVersion());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-HANDOFF-PROJECT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        if (!repository.handoffProject(projectId, actor.hospitalScope(), command.expectedVersion(), actor.actorId(), now)) {
            throw versionConflict();
        }
        repository.appendProjectState(projectId, project.projectTaskStateCode(), "P08-SM-007-ST-03", "P18-EXECUTION-HANDOFF",
                command.expectedVersion(), command.expectedVersion() + 1, actor.actorId(), "normalized execution handoff only", now);
        repository.recordIdempotent("P18-HANDOFF-PROJECT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-EXECUTION-HANDOFF", EXECUTION_PERMISSION, actor, "execution handoff recorded");
        publish("P12-API-017", projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        repository.refreshDerivedOrderState(project.orderId(), actor.hospitalScope(), actor.actorId(), now);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public ProjectResult referenceResult(UUID projectId, ResultCommand command) {
        ActorContext actor = authorized(EXECUTION_PERMISSION);
        requireHuman(actor);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        if (!"HANDED_OFF".equals(project.executionHandoffStateCode())) {
            throw business("P12-ERR-037", "技术结果只能关联到已完成执行交接的项目");
        }
        requireTextValue(command.resultIdentity(), "结果引用身份不能为空");
        requireTextValue(command.resultDigest(), "结果摘要不能为空");
        String digest = digest(projectId + "|" + command.resultIdentity() + "|" + command.resultDigest());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-REFERENCE-RESULT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        repository.appendResult(new ResultReferenceSnapshot(UUID.randomUUID(), projectId,
                defaultText(command.resultReferenceKindCode(), "NORMALIZED_BOUNDARY_REFERENCE"), command.resultIdentity(),
                command.resultDigest(), defaultText(command.resultEnvironmentCode(), defaultEnvironment()),
                requireTextValue(command.note(), "结果说明不能为空"), actor.actorId()), now);
        if (!repository.setResultReferenced(projectId, actor.hospitalScope(), command.expectedVersion(), now)) {
            throw versionConflict();
        }
        repository.appendProjectState(projectId, project.projectTaskStateCode(), project.projectTaskStateCode(), "P18-RESULT-REFERENCE",
                command.expectedVersion(), command.expectedVersion() + 1, actor.actorId(), "normalized result reference", now);
        repository.recordIdempotent("P18-REFERENCE-RESULT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-REFERENCE-RESULT", EXECUTION_PERMISSION, actor, "normalized result reference recorded");
        publish("P12-API-017", projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        repository.refreshDerivedOrderState(project.orderId(), actor.hospitalScope(), actor.actorId(), now);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public ProjectResult closeProject(UUID projectId, VersionCommand command) {
        ActorContext actor = authorized(EXECUTION_PERMISSION);
        requireHuman(actor);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        if (!"REFERENCED".equals(project.resultStateCode())) {
            throw business("P12-ERR-037", "没有规范化结果引用不能关闭技术医嘱项目");
        }
        String digest = digest(projectId + "|" + command.expectedVersion());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-CLOSE-PROJECT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        if (!repository.closeProject(projectId, actor.hospitalScope(), command.expectedVersion(), now)) throw versionConflict();
        repository.appendProjectState(projectId, project.projectTaskStateCode(), project.projectTaskStateCode(), "P18-CLOSE-PROJECT",
                command.expectedVersion(), command.expectedVersion() + 1, actor.actorId(), "project closed from result evidence", now);
        repository.recordIdempotent("P18-CLOSE-PROJECT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-CLOSE-PROJECT", EXECUTION_PERMISSION, actor, "technical project closed");
        publish("P12-API-017", projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        repository.refreshDerivedOrderState(project.orderId(), actor.hospitalScope(), actor.actorId(), now);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    @Transactional
    public ProjectResult cancel(UUID projectId, CancelCommand command) {
        ActorContext actor = authorized(ORDER_PERMISSION);
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        requireTextValue(command.reasonText(), "取消原因不能为空");
        if ("P08-SM-007-ST-03".equals(project.projectTaskStateCode()) && "FULL_CANCEL".equals(command.cancellationKindCode())) {
            throw business("P12-ERR-035", "已交接执行的项目不能完全取消");
        }
        String digest = digest(projectId + "|" + command.cancellationKindCode() + "|" + command.reasonText());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent("P18-CANCEL-PROJECT",
                command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        if (!repository.cancelProject(projectId, actor.hospitalScope(), command.expectedVersion(), now)) throw versionConflict();
        repository.appendCancellation(projectId, command.cancellationKindCode(), command.reasonText(),
                defaultText(command.impactSummary(), "后续技术执行停止，历史事实保留"), actor.actorId(), now);
        repository.appendProjectState(projectId, project.projectTaskStateCode(), "P08-SM-007-ST-04", "P18-CANCEL-PROJECT",
                command.expectedVersion(), command.expectedVersion() + 1, actor.actorId(), command.reasonText(), now);
        repository.recordIdempotent("P18-CANCEL-PROJECT", command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, "P18-CMD-CANCEL-PROJECT", ORDER_PERMISSION, actor, "technical project cancelled");
        publish("P12-EVC-021", projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        repository.refreshDerivedOrderState(project.orderId(), actor.hospitalScope(), actor.actorId(), now);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    private ProjectResult updateProject(UUID projectId, VersionCommand command, String operation, String auditOperation,
            String permission, ProjectUpdater updater, String targetState, String event, String outboxEvent, ActorContext actor) {
        requireText(command.idempotencyKey());
        ProjectSnapshot project = requireProject(projectId, actor);
        String digest = digest(projectId + "|" + command.expectedVersion());
        Optional<JdbcTechnicalOrderRepository.IdempotentReference> replay = repository.idempotent(operation, command.idempotencyKey(), digest);
        if (replay.isPresent()) return projectResult(requireProject(projectId, actor), actor, true);
        Instant now = Instant.now();
        if (!updater.update(project, now)) throw versionConflict();
        repository.appendProjectState(projectId, project.projectTaskStateCode(), targetState, event, command.expectedVersion(),
                command.expectedVersion() + 1, actor.actorId(), event, now);
        repository.recordIdempotent(operation, command.idempotencyKey(), digest, projectId, actor.actorId(), now);
        audit(projectId, auditOperation, permission, actor, operation);
        publish(outboxEvent, projectId, PROJECT_SUBJECT, command.expectedVersion() + 1, actor, digest);
        repository.refreshDerivedOrderState(project.orderId(), actor.hospitalScope(), actor.actorId(), now);
        return projectResult(requireProject(projectId, actor), actor, false);
    }

    private void appendPlannedOutputs(UUID projectId, List<PlannedOutputCommand> outputs, ActorContext actor, Instant now, int seed) {
        if (outputs == null || outputs.isEmpty()) throw business("P12-ERR-035", "技术医嘱项目必须声明计划产物");
        int fallbackSequence = seed;
        for (PlannedOutputCommand output : outputs) {
            validateOutput(output);
            repository.addPlannedOutput(new PlannedOutputSnapshot(UUID.randomUUID(), projectId,
                    output.sequenceNo() > 0 ? output.sequenceNo() : fallbackSequence++, output.outputKindCode(), output.slidePurposeCode(),
                    output.plannedLayerReference(), output.plannedQuantity(), output.plannedStainProjectCode(), output.plannedUsageCode(),
                    output.plannedLabelQuantity(), output.executionNote(), actor.actorId()), now);
        }
    }

    private void validateOutput(PlannedOutputCommand output) {
        if (output.plannedQuantity() <= 0 || output.plannedLabelQuantity() < 0) {
            throw business("P12-ERR-035", "计划产物数量不合法");
        }
        requireTextValue(output.outputKindCode(), "计划产物类型不能为空");
        requireTextValue(output.slidePurposeCode(), "计划片用途不能为空");
        requireTextValue(output.plannedUsageCode(), "计划产物用途不能为空");
    }

    private ConfigurationSnapshot configuration(ProjectCommand project) {
        try {
            TechnicalProjectType.parse(project.projectTypeCode());
        } catch (IllegalArgumentException exception) {
            throw business("P12-ERR-082", "技术项目类型不在正式配置范围内");
        }
        ConfigurationSnapshot configuration = repository.configuration(project.projectCode(), project.versionLabel())
                .orElseThrow(() -> business("P12-ERR-082", "技术项目配置不存在或未生效"));
        if (!configuration.projectTypeCode().equals(project.projectTypeCode()) || !"ACTUAL_BLOCK".equals(configuration.targetKindCode())) {
            throw business("P12-ERR-082", "技术项目配置与目标类型不一致");
        }
        return configuration;
    }

    private BlockTargetSnapshot requireTarget(UUID formationId, ActorContext actor) {
        if (formationId == null) throw business("P12-ERR-035", "技术医嘱项目必须绑定实际蜡块目标");
        BlockTargetSnapshot target = repository.validActualBlock(formationId, actor.hospitalScope())
                .orElseThrow(() -> business("P12-ERR-035", "目标实际蜡块不存在、未形成、作废或不在当前范围"));
        return target;
    }

    private void requireSameCase(UUID expected, UUID actual) {
        if (!expected.equals(actual)) throw business("P12-ERR-036", "目标与病例不匹配");
    }

    private OrderSnapshot requireOrder(UUID id, ActorContext actor) {
        return repository.order(id, actor.hospitalScope()).orElseThrow(() -> business("P12-ERR-011", "技术医嘱不存在或不在当前范围"));
    }

    private ProjectSnapshot requireProject(UUID id, ActorContext actor) {
        return repository.project(id, actor.hospitalScope()).orElseThrow(() -> business("P12-ERR-034", "技术医嘱项目不存在或不在当前范围"));
    }

    private ActorContext authorized(String permission) {
        return authorization.requireTask(permission, TASK);
    }

    private void requireHuman(ActorContext actor) {
        if ("DEVICE".equals(actor.subjectTypeCode()) || "SERVICE".equals(actor.subjectTypeCode())) {
            throw business("P12-ERR-075", "设备或服务身份不能承担技术医嘱责任", 403);
        }
    }

    private void audit(UUID targetId, String operation, String permission, ActorContext actor, String reason) {
        audit.append(operation, permission, actor, "RECORDED", "SUCCESS", targetId, PROJECT_SUBJECT,
                UUID.randomUUID().toString(), reason);
    }

    private void auditOrder(UUID targetId, String operation, String permission, ActorContext actor, String reason) {
        audit.append(operation, permission, actor, "RECORDED", "SUCCESS", targetId, ORDER_SUBJECT,
                UUID.randomUUID().toString(), reason);
    }

    private void publish(String eventType, UUID subjectId, String subjectKind, long version, ActorContext actor, String digest) {
        outbox.append(eventType, subjectId, subjectKind, version, UUID.randomUUID().toString(), digest, actor.actorId());
    }

    private OrderResult orderResult(OrderSnapshot order, ActorContext actor) { return orderResult(order, actor, false); }

    private OrderResult orderResult(OrderSnapshot order, ActorContext actor, boolean duplicate) {
        List<ProjectResult> projects = repository.projects(order.id(), actor.hospitalScope()).stream()
                .map(project -> projectResult(project, actor, false)).toList();
        return new OrderResult(order.id(), order.orderNo(), order.caseId(), order.orderLifecycleStateCode(), order.priorityCode(),
                order.concurrencyVersion(), duplicate, projects);
    }

    private ProjectResult projectResult(ProjectSnapshot project, ActorContext actor, boolean duplicate) {
        var target = repository.target(project.id()).orElse(null);
        List<PlannedOutputSnapshot> outputs = repository.plannedOutputs(project.id());
        return new ProjectResult(project.id(), project.orderId(), project.projectNo(), project.projectTypeCode(),
                project.projectTaskStateCode(), project.reviewStateCode(), project.receivingStateCode(),
                project.executionHandoffStateCode(), project.resultStateCode(), project.assignedActorRef(),
                target == null ? null : target.actualBlockFormationId(), outputs.stream().map(this::outputResult).toList(),
                project.concurrencyVersion(), duplicate);
    }

    private OutputResult outputResult(PlannedOutputSnapshot output) {
        return new OutputResult(output.id(), output.sequenceNo(), output.outputKindCode(), output.slidePurposeCode(),
                output.plannedQuantity(), output.plannedStainProjectCode(), output.plannedUsageCode(), output.plannedLabelQuantity());
    }

    private P15BusinessException versionConflict() { return business("P12-ERR-010", "技术医嘱版本冲突，请重新读取后重试", 409); }

    private P15BusinessException business(String code, String message) { return business(code, message, 422); }

    private P15BusinessException business(String code, String message, int status) { return new P15BusinessException(code, message, status); }

    private String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }

    private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private String requireTextValue(String value, String message) {
        if (value == null || value.isBlank()) throw business("P12-ERR-001", message);
        return value.trim();
    }

    private void requireText(String value) { requireTextValue(value, "幂等键不能为空"); }

    private String defaultEnvironment() { return "local".equalsIgnoreCase(runtimeEnvironment) ? "DEV" : "FORMAL"; }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("digest unavailable", exception);
        }
    }

    @FunctionalInterface
    private interface ProjectUpdater { boolean update(ProjectSnapshot project, Instant now); }

    public record CreateOrderCommand(UUID caseId, String orderKindCode, String priorityCode, String reasonText,
            String representedActorRef, List<ProjectCommand> projects, String idempotencyKey) { }
    public record AddProjectCommand(ProjectCommand project, String idempotencyKey) { }
    public record ProjectCommand(String projectCode, String versionLabel, String projectTypeCode, UUID actualBlockFormationId,
            String usageCode, String priorityCode, String reasonText, List<PlannedOutputCommand> plannedOutputs) { }
    public record PlannedOutputCommand(int sequenceNo, String outputKindCode, String slidePurposeCode,
            String plannedLayerReference, int plannedQuantity, String plannedStainProjectCode, String plannedUsageCode,
            int plannedLabelQuantity, String executionNote, String idempotencyKey) { }
    public record BindTargetCommand(UUID actualBlockFormationId, String reasonText, String idempotencyKey) { }
    public record VersionCommand(long expectedVersion, String idempotencyKey) { }
    public record ReviewCommand(String decisionCode, String reasonText, long expectedVersion, String idempotencyKey) { }
    public record AssignCommand(String assignedActorRef, long expectedVersion, String idempotencyKey, String reasonText) { }
    public record ResultCommand(String resultReferenceKindCode, String resultIdentity, String resultDigest,
            String resultEnvironmentCode, String note, long expectedVersion, String idempotencyKey) { }
    public record CancelCommand(String cancellationKindCode, String reasonText, String impactSummary, long expectedVersion,
            String idempotencyKey) { }
    public record OrderResult(UUID orderId, String orderNo, UUID caseId, String stateCode, String priorityCode,
            long concurrencyVersion, boolean duplicate, List<ProjectResult> projects) { }
    public record ProjectResult(UUID projectId, UUID orderId, String projectNo, String projectTypeCode, String taskStateCode,
            String reviewStateCode, String receivingStateCode, String executionHandoffStateCode, String resultStateCode,
            String assignedActorRef, UUID actualBlockFormationId, List<OutputResult> plannedOutputs, long concurrencyVersion,
            boolean duplicate) { }
    public record OutputResult(UUID outputId, int sequenceNo, String outputKindCode, String slidePurposeCode,
            int plannedQuantity, String plannedStainProjectCode, String plannedUsageCode, int plannedLabelQuantity) { }
}

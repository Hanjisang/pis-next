package com.hanjisang.pis.v2.frozen.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.integration.gateway.IntegrationCapability;
import com.hanjisang.pis.integration.gateway.IntegrationGatewayApplicationService;
import com.hanjisang.pis.integration.gateway.IntegrationRequestDto;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.diagnosis.domain.AssignmentSource;
import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisContextType;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisTemplateVersion;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityRole;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityUnit;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository;
import com.hanjisang.pis.v2.frozen.domain.FrozenRound;
import com.hanjisang.pis.v2.frozen.infrastructure.JdbcV2FrozenRoundRepository;
import com.hanjisang.pis.v2.frozen.infrastructure.JdbcV2FrozenRoundRepository.Notification;
import com.hanjisang.pis.v2.frozen.infrastructure.JdbcV2FrozenRoundRepository.Production;
import com.hanjisang.pis.v2.frozen.infrastructure.JdbcV2FrozenRoundRepository.TatPolicy;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

/** Frozen orchestration over unified Case, Specimen, Slide, Diagnosis and Report facts. */
@Service
public class V2FrozenApplicationService {

    private static final String FROZEN = "FROZEN";
    private static final String ROUTINE = "HISTOLOGY";
    private static final String FROZEN_PERMISSION = "P14-PERM-019";
    private static final String APPLICATION_ACCEPT_PERMISSION = "P14-PERM-003";
    private static final String FROZEN_ROUND_CANCEL_PERMISSION = "P14-PERM-020";
    private static final String FROZEN_END_PERMISSION = "P14-PERM-021";
    private static final String DIAGNOSIS_PERMISSION = "P14-PERM-034";
    private static final String QUERY_PERMISSION = "P14-PERM-048";

    private final JdbcV2FrozenRoundRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2DiagnosisRepository diagnosisRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final IntegrationGatewayApplicationService integration;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public V2FrozenApplicationService(JdbcV2FrozenRoundRepository repository,
            JdbcV2RegistrationRepository registrationRepository, JdbcV2DiagnosisRepository diagnosisRepository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox,
            IntegrationGatewayApplicationService integration) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.integration = integration;
    }

    @Transactional
    public RoundResult openRound(UUID caseId, OpenRoundCommand command) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        requireKey(command.idempotencyKey());
        lockCase(caseId, actor);
        Case frozenCase = frozenCase(caseId, actor);
        rejectIfEnded(caseId, actor);

        FrozenRound current = repository.findCurrent(caseId, actor.hospitalScope()).orElse(null);
        if (current != null && !command.createNew()) return roundResult(current, actor.hospitalScope(), false);
        if (current != null && command.createNew()) {
            throw reject("V2-FROZEN-ROUND-IN-PROGRESS", "上一轮冰冻尚未结束，不能创建下一轮");
        }

        Instant now = Instant.now();
        FrozenRound round = openRoundInternal(frozenCase, actor,
                command.arrivalTime() == null ? now : command.arrivalTime(), now);
        audit.append("PIS-V2-I06-FROZEN-ROUND-OPEN", FROZEN_PERMISSION, actor, "ALLOWED", "COMPLETED", round.id(),
                "V2-FROZEN-ROUND", UUID.randomUUID().toString(), "roundNo=" + round.roundNo());
        outbox.append("V2-I06-FROZEN-ROUND-OPENED", round.id(), "V2-FROZEN-ROUND", round.version(),
                UUID.randomUUID().toString(), command.idempotencyKey(), actor.actorId());
        return roundResult(round, actor.hospitalScope(), false);
    }

    @Transactional
    public RoundResult registerSpecimen(UUID caseId, RegisterFrozenSpecimenCommand command) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        requireText(command.specimenCode(), "冰冻标本代码不能为空");
        requireText(command.collectionSite(), "冰冻标本部位不能为空");
        requireKey(command.idempotencyKey());
        lockCase(caseId, actor);
        Case frozenCase = frozenCase(caseId, actor);
        rejectIfEnded(caseId, actor);

        OptionalSpecimen existing = existingSpecimen(caseId, command.specimenCode(), actor);
        if (existing.id() != null) {
            UUID roundId = repository.findRoundIdBySpecimen(existing.id(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-FROZEN-SPECIMEN-CONFLICT", "冰冻标本已存在但未绑定轮次"));
            FrozenRound existingRound = repository.find(roundId, actor.hospitalScope()).orElseThrow();
            return roundResult(existingRound, actor.hospitalScope(), true);
        }

        FrozenRound round = repository.findCurrent(caseId, actor.hospitalScope()).orElse(null);
        if (round == null || !round.acceptsSpecimen()) {
            Instant now = Instant.now();
            round = openRoundInternal(frozenCase, actor, now, now);
        }
        Instant now = Instant.now();
        UUID specimenId = UUID.randomUUID();
        Specimen specimen = Specimen.register(specimenId, caseId,
                registrationRepository.allocateNumber(actor.hospitalScope(), FROZEN, "SPECIMEN", now),
                command.specimenCode(), command.specimenKindCode(), "FROZEN_ROUND", round.id().toString(),
                command.collectionSite(), command.collectionMethodCode(), command.labelCode());
        registrationRepository.insertSpecimen(specimen, actor.hospitalScope(), actor.actorId(), now);
        repository.linkSpecimen(round.id(), specimen.id(), repository.nextSpecimenSequence(round.id()), actor.actorId(), now);
        audit.append("PIS-V2-I06-FROZEN-SPECIMEN-REGISTER", FROZEN_PERMISSION, actor, "ALLOWED", "COMPLETED",
                specimen.id(), "V2-SPECIMEN", UUID.randomUUID().toString(), "roundNo=" + round.roundNo());
        return roundResult(round, actor.hospitalScope(), false);
    }

    /**
     * Completes the Frozen registration boundary for a specimen created by the
     * Application -> Registration transaction. The specimen itself remains
     * owned by the unified registration model; this method only creates or
     * links the round fact that makes the Frozen projection discoverable.
     */
    @Transactional
    public void bootstrapRegisteredSpecimen(UUID caseId, UUID specimenId) {
        // Registration acceptance is the authoritative permission for this
        // boundary. A registrar must not need Frozen queue access merely to
        // establish the initial FrozenRound fact.
        ActorContext actor = authorization.require(APPLICATION_ACCEPT_PERMISSION);
        lockCase(caseId, actor);
        Case frozenCase = frozenCase(caseId, actor);
        rejectIfEnded(caseId, actor);
        if (!repository.specimenBelongsToCase(specimenId, caseId, actor.hospitalScope())) {
            throw reject("V2-FROZEN-SPECIMEN-SCOPE", "冰冻标本与病例不匹配");
        }
        if (repository.findRoundIdBySpecimen(specimenId, actor.hospitalScope()).isPresent()) return;

        FrozenRound round = repository.findCurrent(caseId, actor.hospitalScope()).orElse(null);
        Instant now = Instant.now();
        if (round == null || !round.acceptsSpecimen()) {
            round = openRoundInternal(frozenCase, actor, now, now);
        }
        if (!repository.hasSpecimen(round.id(), specimenId)) {
            repository.linkSpecimen(round.id(), specimenId, repository.nextSpecimenSequence(round.id()),
                    actor.actorId(), now);
        }
        audit.append("PIS-V2-I06-FROZEN-REGISTRATION-BOOTSTRAP", APPLICATION_ACCEPT_PERMISSION, actor, "ALLOWED",
                "COMPLETED", specimenId, "V2-SPECIMEN", UUID.randomUUID().toString(),
                "roundNo=" + round.roundNo());
    }

    @Transactional
    public void cancelRound(UUID roundId, CancelRoundCommand command) {
        ActorContext actor = authorization.require(FROZEN_ROUND_CANCEL_PERMISSION);
        requireKey(command.idempotencyKey());
        requireText(command.reason(), "冰冻轮次取消原因不能为空");
        FrozenRound round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在或不在当前数据范围"));
        lockCase(round.caseId(), actor);
        frozenCase(round.caseId(), actor);
        if (round.cancelled()) return;
        if (FrozenRound.SIGNED.equals(round.status()) || FrozenRound.ENDED.equals(round.status())) {
            throw new P15BusinessException("V2-FROZEN-ROUND-CANCEL-CONFLICT", "已报告或已结束的冰冻轮次不能取消", 409);
        }
        long version = round.version();
        round.cancel(Instant.now(), actor.actorId(), command.reason());
        if (!repository.update(round, actor.hospitalScope(), version)) throw conflict("轮次取消发生并发冲突");
        audit.append("PIS-V2-I06-FROZEN-ROUND-CANCEL", FROZEN_ROUND_CANCEL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                round.id(), "V2-FROZEN-ROUND", UUID.randomUUID().toString(), command.reason());
    }

    @Transactional
    public DiagnosisResult createDiagnosis(UUID roundId, String idempotencyKey) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireKey(idempotencyKey);
        FrozenRound round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在"));
        lockCase(round.caseId(), actor);
        Case frozenCase = frozenCase(round.caseId(), actor);
        round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在或不在当前数据范围"));
        if (round.cancelled()) throw reject("V2-FROZEN-ROUND-CANCELLED", "已取消轮次不能创建诊断");
        Production production = repository.production(roundId);
        if (!production.complete()) throw reject("V2-FROZEN-PRODUCTION-INCOMPLETE", "当前冰冻轮次制片尚未全部完成");
        Diagnosis existing = diagnosisRepository.findDiagnosisByContext(DiagnosisContextType.FROZEN_ROUND, roundId,
                actor.hospitalScope()).orElse(null);
        if (existing != null) return new DiagnosisResult(existing.id(), round.id(), true);
        DiagnosisTemplateVersion template = diagnosisRepository.findPublishedTemplateVersion(frozenCase.businessTypeId(),
                actor.hospitalScope()).orElseThrow(() -> reject("V2-FROZEN-DIAGNOSIS-TEMPLATE", "冰冻诊断模板未发布"));
        Instant now = Instant.now();
        Diagnosis diagnosis = Diagnosis.createForContext(UUID.randomUUID(), frozenCase.id(),
                DiagnosisContextType.FROZEN_ROUND, round.id(), template.id(), "{}", null, null, null, now,
                actor.actorId());
        diagnosisRepository.insertDiagnosis(diagnosis, actor.hospitalScope(), now, actor.actorId());
        ResponsibilityUnit responsibility = ResponsibilityUnit.assign(UUID.randomUUID(), diagnosis.id(),
                ResponsibilityRole.INITIAL, actor.actorId(), diagnosisRepository.nextResponsibilitySequence(diagnosis.id()),
                AssignmentSource.SELF_CLAIM, "冰冻轮次诊断", now, actor.actorId());
        diagnosisRepository.insertResponsibility(responsibility);
        audit.append("PIS-V2-I06-FROZEN-DIAGNOSIS-CREATE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED",
                diagnosis.id(), "V2-DIAGNOSIS", UUID.randomUUID().toString(), "roundNo=" + round.roundNo());
        return new DiagnosisResult(diagnosis.id(), round.id(), false);
    }

    @Transactional(readOnly = true)
    public FrozenWorkspace workspace(UUID caseId) {
        var frozenAccess = authorization.decide(FROZEN_PERMISSION);
        ActorContext actor = frozenAccess.allowed() ? frozenAccess.actor() : authorization.require(DIAGNOSIS_PERMISSION);
        // Historical Frozen cases remain readable after cancellation/end. Write commands
        // still use frozenCase(...), which requires an ACTIVE case.
        Case frozenCase = frozenCaseForView(caseId, actor);
        List<RoundView> rounds = repository.findByCase(caseId, actor.hospitalScope()).stream().map(round -> {
            Production production = repository.production(round.id());
            UUID diagnosisId = diagnosisRepository.findDiagnosisByContext(DiagnosisContextType.FROZEN_ROUND, round.id(),
                    actor.hospitalScope()).map(Diagnosis::id).orElse(null);
            List<RoundSpecimenView> specimens = repository.findSpecimenIds(round.id()).stream()
                    .map(specimenId -> registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                            .map(item -> new RoundSpecimenView(item.id(), item.specimenNo(), item.specimenCode(),
                                    item.specimenKindCode(), item.collectionSite(), item.specimenName()))
                            .orElse(null))
                    .filter(Objects::nonNull).toList();
            FrozenWorkspace.TatView tat = tat(round, Instant.now());
            Notification notification = repository.latestNotification(round.id(), actor.hospitalScope()).orElse(null);
            boolean alertAcknowledged = repository.findTatAlertAction(round.id(), actor.hospitalScope(), tat.status())
                    .isPresent();
            String reportStatus = repository.currentReportStatus(round.id(), actor.hospitalScope());
            return new RoundView(round.id(), round.roundNo(), round.status(), specimens, production.totalRequired(),
                    production.completedRequired(), production.complete(), diagnosisId, round.arrivalTime(),
                    round.registeredAt(), round.grossingStartTime(), round.slideCompletedTime(),
                    round.diagnosisSignedTime(), round.cancelledAt(), round.cancellationReason(), tat.elapsedMinutes(),
                    tat.status(), alertAcknowledged,
                    notification == null ? null : notification.statusCode(),
                    notification == null ? null : notification.messageLogId(),
                    notification == null ? List.of() : notificationAttempts(notification), reportStatus);
        }).toList();
        JdbcV2FrozenRoundRepository.FrozenEnd end = repository.findEnd(caseId, actor.hospitalScope()).orElse(null);
        String routinePathologyNo = end == null ? null
                : registrationRepository.findCase(end.routineCaseId(), actor.hospitalScope()).map(Case::caseNo).orElse(null);
        return new FrozenWorkspace(frozenCase.id(), frozenCase.caseNo(), frozenCase.businessTypeCode(), rounds,
                end == null ? null : end.routineCaseId(), routinePathologyNo, end != null);
    }

    @Transactional
    public EndResult finishFrozenCase(UUID caseId, FinishFrozenCommand command) {
        ActorContext actor = authorization.require(FROZEN_END_PERMISSION);
        requireKey(command.idempotencyKey());
        lockCase(caseId, actor);
        Case frozenCase = frozenCase(caseId, actor);
        JdbcV2FrozenRoundRepository.FrozenEnd existing = repository.findEnd(caseId, actor.hospitalScope()).orElse(null);
        if (existing != null) {
            String routineNo = registrationRepository.findCase(existing.routineCaseId(), actor.hospitalScope())
                    .map(Case::caseNo).orElse(null);
            return new EndResult(existing.routineCaseId(), routineNo, List.of(), true);
        }

        List<FrozenRound> rounds = repository.findByCase(caseId, actor.hospitalScope());
        List<FrozenRound> activeRounds = rounds.stream().filter(round -> !round.cancelled()).toList();
        if (activeRounds.isEmpty()) throw reject("V2-FROZEN-END-BLOCKED", "没有可转常规的有效冰冻轮次");
        FrozenRound blocked = activeRounds.stream().filter(round -> !FrozenRound.SIGNED.equals(round.status())).findFirst()
                .orElse(null);
        if (blocked != null) {
            throw reject("V2-FROZEN-END-BLOCKED", "第" + blocked.roundNo() + "轮尚未完成冰冻诊断或报告签发");
        }
        FrozenRound withoutEffectiveReport = activeRounds.stream()
                .filter(round -> !"EFFECTIVE".equals(repository.currentReportStatus(round.id(), actor.hospitalScope())))
                .findFirst().orElse(null);
        if (withoutEffectiveReport != null) {
            throw reject("V2-FROZEN-END-BLOCKED", "第" + withoutEffectiveReport.roundNo() + "轮没有当前有效冰冻报告");
        }

        List<UUID> selectedSpecimens = selectedSpecimens(command.specimenIds(), activeRounds, actor);
        if (selectedSpecimens.isEmpty()) throw reject("V2-FROZEN-END-NO-SPECIMEN", "至少选择一个转入常规的冰冻标本");
        String routineBusinessType = repository.configuredRoutineBusinessType(actor.hospitalScope()).orElse(ROUTINE);
        var businessType = registrationRepository.findBusinessType(routineBusinessType)
                .orElseThrow(() -> reject("V2-FROZEN-END-ROUTINE-TYPE", "常规业务类型不存在"));
        String routineApplicationItemCode = registrationRepository.findActiveApplicationItemCode(routineBusinessType)
                .orElseThrow(() -> reject("V2-FROZEN-END-ROUTING", "常规申请项目映射不存在"));
        Instant now = Instant.now();
        UUID routineId = UUID.randomUUID();
        String routineNo = registrationRepository.allocateNumber(actor.hospitalScope(), routineBusinessType, "CASE", now);
        Case routine = Case.routineFromFrozen(routineId, routineNo, "V2-FROZEN-END", "FROZEN:" + frozenCase.id(),
                routineApplicationItemCode, businessType.id(), businessType.code(), frozenCase.patientReference(),
                frozenCase.visitReference(), frozenCase.id());
        registrationRepository.insertCase(routine, actor.hospitalScope(), now, actor.actorId());

        JdbcV2FrozenRoundRepository.FrozenEnd end = new JdbcV2FrozenRoundRepository.FrozenEnd(UUID.randomUUID(),
                caseId, routineId, command.idempotencyKey(), now, actor.actorId());
        // The end row is the parent of the selected specimen mappings. Keep both in the
        // same transaction, but insert the parent before its foreign-key children.
        repository.insertEnd(end);
        List<UUID> routineSpecimenIds = new ArrayList<>();
        int sequence = 1;
        for (UUID frozenSpecimenId : selectedSpecimens) {
            Specimen source = registrationRepository.findSpecimen(frozenSpecimenId, actor.hospitalScope())
                    .filter(item -> item.caseId().equals(caseId) && !item.deleted())
                    .orElseThrow(() -> reject("V2-FROZEN-END-SPECIMEN", "待转常规标本不存在或不在当前病例"));
            UUID routineSpecimenId = UUID.randomUUID();
            String code = sequence == 1 ? "FROZEN-REMAINDER" : "FROZEN-REMAINDER-" + sequence;
            Specimen routineSpecimen = Specimen.registerWithSource(routineSpecimenId, routineId,
                    registrationRepository.allocateNumber(actor.hospitalScope(), routineBusinessType, "SPECIMEN", now), code,
                    source.specimenName(), source.specimenKindCode(), Specimen.FROZEN_REMAINDER, "FROZEN_SPECIMEN",
                    source.id().toString(), source.collectionSite(), source.collectionMethodCode(),
                    source.preparationMethodCode(), source.lateralityCode(), source.quantityValue(), source.quantityUnitCode(),
                    source.description(), source.removedAt(), source.fixedAt(), source.receivedAt(),
                    routineNo + "-SPECIMEN-LABEL-" + sequence);
            registrationRepository.insertSpecimen(routineSpecimen, actor.hospitalScope(), actor.actorId(), now);
            UUID roundId = repository.findRoundIdBySpecimen(source.id(), actor.hospitalScope()).orElseThrow();
            repository.insertEndSpecimen(end.id(), source.id(), routineSpecimenId, roundId,
                    actor.hospitalScope(), actor.actorId(), now);
            routineSpecimenIds.add(routineSpecimenId);
            sequence++;
        }
        for (FrozenRound round : activeRounds) {
            long version = round.version();
            round.end(now, actor.actorId());
            if (!repository.update(round, actor.hospitalScope(), version)) throw conflict("冰冻轮次结束发生并发冲突");
        }
        audit.append("PIS-V2-I06-FROZEN-END", FROZEN_END_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId,
                "V2-FROZEN-CASE", UUID.randomUUID().toString(), "routineCaseId=" + routineId);
        outbox.append("V2-I06-FROZEN-ENDED", caseId, "V2-CASE", frozenCase.concurrencyVersion(),
                UUID.randomUUID().toString(), command.idempotencyKey(), actor.actorId());
        return new EndResult(routineId, routineNo, routineSpecimenIds, false);
    }

    /** Called by unified Report sign-out; notification failure never throws into report creation. */
    @Transactional
    public void notifyReportSigned(UUID diagnosisId, UUID reportId, String reportNo, String organizationReference,
            Instant signedAt) {
        FrozenRound round = repository.findByDiagnosis(diagnosisId, organizationReference).orElse(null);
        if (round == null) return;
        Case frozenCase = registrationRepository.findCase(round.caseId(), organizationReference).orElse(null);
        if (frozenCase == null) return;
        String digest = reportId + ":" + reportNo + ":" + round.id();
        integration.dispatch("MOCK_FROZEN_NOTIFICATION", new IntegrationRequestDto(organizationReference, "OUTBOUND",
                "PIS", "OR_HIS", "FROZEN-REPORT:" + reportId, IntegrationCapability.CLINICAL_RESULT_NOTIFICATION.name(),
                "FROZEN_ROUND:" + round.id(), "mock://fail-once/frozen-notification/" + reportId, digest, signedAt));
    }

    @Transactional
    public NotificationResult retryNotification(UUID roundId) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        FrozenRound round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在"));
        Notification notification = repository.latestNotification(round.id(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-NOTIFICATION-NOT-FOUND", "当前轮次没有可重试通知"));
        if (!"EFFECTIVE".equals(notification.reportStatus())) {
            throw reject("V2-FROZEN-NOTIFICATION-REPORT-NOT-EFFECTIVE", "当前通知对应的报告已撤回，不能重试");
        }
        if ("SUCCEEDED".equals(notification.statusCode())) {
            throw new P15BusinessException("V2-FROZEN-NOTIFICATION-ALREADY-DELIVERED", "术中结果已发送成功", 409);
        }
        if ("SENDING".equals(notification.statusCode())) {
            throw new P15BusinessException("V2-FROZEN-NOTIFICATION-IN-PROGRESS", "术中结果正在发送", 409);
        }
        IntegrationGatewayApplicationService.DispatchResult result;
        try {
            result = integration.retry(notification.messageLogId(), "MOCK_FROZEN_NOTIFICATION");
        } catch (IntegrationGatewayApplicationService.DeliveryInProgressException exception) {
            throw new P15BusinessException("V2-FROZEN-NOTIFICATION-IN-PROGRESS", "术中结果正在发送", 409);
        }
        return new NotificationResult(result.messageLogId(), result.statusCode(), result.retryCount(),
                result.errorCode(), result.errorMessage());
    }

    @Transactional(readOnly = true)
    public NotificationHistory notificationHistory(UUID roundId) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        FrozenRound round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在"));
        Notification notification = repository.latestNotification(round.id(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-NOTIFICATION-NOT-FOUND", "当前轮次没有术中结果发送记录"));
        return new NotificationHistory(notification.reportId(), notification.reportNo(), notification.reportStatus(),
                notification.target(), "模拟发送", notification.statusCode(), notification.lastAttemptAt(),
                notification.errorCode(), notification.errorMessage(), notificationAttempts(notification));
    }

    @Transactional
    public TatAlertResult acknowledgeTatAlert(UUID roundId, String note) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        FrozenRound round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在"));
        FrozenWorkspace.TatView result = tat(round, Instant.now());
        if (!"WARNING".equals(result.status()) && !"OVERDUE".equals(result.status())) {
            throw new P15BusinessException("V2-FROZEN-TAT-NOT-ALERT", "当前轮次尚未达到提醒阈值", 409);
        }
        repository.acknowledgeTatAlert(roundId, actor.hospitalScope(), result.status(), note, actor.actorId(),
                Instant.now());
        return new TatAlertResult(roundId, result.status(), true);
    }

    @Transactional(readOnly = true)
    public ComparisonResult comparison(UUID caseId) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        authorization.require(QUERY_PERMISSION);
        Case selected = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        UUID frozenCaseId;
        UUID routineCaseId;
        if (FROZEN.equals(selected.businessTypeCode())) {
            frozenCaseId = selected.id();
            JdbcV2FrozenRoundRepository.FrozenEnd end = repository.findEnd(selected.id(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-FROZEN-COMPARISON-NOT-AVAILABLE", "当前冰冻病例尚未转入常规"));
            routineCaseId = end.routineCaseId();
        } else {
            routineCaseId = selected.id();
            frozenCaseId = selected.frozenSourceCaseId();
            if (frozenCaseId == null) {
                throw reject("V2-FROZEN-COMPARISON-NOT-AVAILABLE", "当前常规病例没有来源冰冻病例");
            }
            registrationRepository.findCase(frozenCaseId, actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-FROZEN-COMPARISON-SCOPE", "来源冰冻病例不在当前数据范围"));
        }
        Case frozenCase = registrationRepository.findCase(frozenCaseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-COMPARISON-SCOPE", "来源冰冻病例不在当前数据范围"));
        Case routineCase = registrationRepository.findCase(routineCaseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-COMPARISON-SCOPE", "常规病例不在当前数据范围"));
        List<ComparisonRound> rounds = repository.findRoundComparisons(frozenCaseId, actor.hospitalScope()).stream()
                .map(row -> new ComparisonRound(row.roundId(), row.roundNo(), repository.specimenSummary(row.roundId()),
                        comparisonDiagnosis(row.diagnosisSnapshot(), row.reportStatus(), true), row.reportStatus(), row.signedAt(),
                        row.signedBy(), comparisonTat(row.arrivalTime(), row.signedAt()))).toList();
        var routine = repository.findRoutineComparison(routineCaseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-COMPARISON-SCOPE", "常规病例不在当前数据范围"));
        return new ComparisonResult(frozenCase.id(), frozenCase.caseNo(), routineCase.id(), routine.pathologyNo(), rounds,
                comparisonDiagnosis(routine.diagnosisSnapshot(), routine.reportStatus(), false), routine.reportStatus(),
                routine.signedAt(), routine.signedBy());
    }

    public void markReportSigned(UUID diagnosisId, String organizationReference) {
        FrozenRound round = repository.findByDiagnosis(diagnosisId, organizationReference).orElse(null);
        if (round == null || FrozenRound.SIGNED.equals(round.status())) return;
        if (round.cancelled()) throw reject("V2-FROZEN-ROUND-CANCELLED", "已取消冰冻轮次不能签发报告");
        long version = round.version();
        round.markSigned(Instant.now());
        if (!repository.update(round, organizationReference, version)) throw conflict("冰冻轮次签发状态发生并发冲突");
    }

    private List<UUID> selectedSpecimens(List<UUID> requested, List<FrozenRound> rounds, ActorContext actor) {
        List<UUID> all = rounds.stream().flatMap(round -> repository.findActiveSpecimenIds(round.id()).stream()).toList();
        if (requested == null || requested.isEmpty()) return all;
        for (UUID specimenId : requested) {
            if (!all.contains(specimenId) || !repository.specimenBelongsToCase(specimenId,
                    rounds.get(0).caseId(), actor.hospitalScope())) {
                throw reject("V2-FROZEN-END-SPECIMEN", "选择的标本不属于当前冰冻病例");
            }
        }
        return requested.stream().distinct().toList();
    }

    private FrozenRound openRoundInternal(Case frozenCase, ActorContext actor, Instant arrivalTime, Instant now) {
        FrozenRound round = FrozenRound.open(UUID.randomUUID(), frozenCase.id(), repository.nextRoundNo(frozenCase.id()),
                arrivalTime, now);
        repository.insert(round, actor.hospitalScope(), actor.actorId(), now);
        linkUnassignedSpecimens(round, actor, now);
        return round;
    }

    private void linkUnassignedSpecimens(FrozenRound round, ActorContext actor, Instant now) {
        int sequence = repository.nextSpecimenSequence(round.id());
        for (UUID specimenId : repository.findUnlinkedCaseSpecimenIds(round.caseId(), actor.hospitalScope())) {
            if (!repository.specimenBelongsToCase(specimenId, round.caseId(), actor.hospitalScope())) {
                throw reject("V2-FROZEN-SPECIMEN-SCOPE", "标本与冰冻病例不匹配");
            }
            repository.linkSpecimen(round.id(), specimenId, sequence++, actor.actorId(), now);
        }
    }

    private FrozenWorkspace.TatView tat(FrozenRound round, Instant now) {
        Instant end = round.diagnosisSignedTime() == null ? now : round.diagnosisSignedTime();
        long elapsedMinutes = Math.max(0, Duration.between(round.arrivalTime(), end).toMinutes());
        TatPolicy policy = repository.frozenTatPolicy().orElse(new TatPolicy(BigDecimal.ONE, BigDecimal.valueOf(2)));
        BigDecimal hours = BigDecimal.valueOf(Duration.between(round.arrivalTime(), end).toSeconds())
                .divide(BigDecimal.valueOf(3600), 6, java.math.RoundingMode.HALF_UP);
        String status = hours.compareTo(policy.overdueHours()) >= 0 ? "OVERDUE"
                : hours.compareTo(policy.warningHours()) >= 0 ? "WARNING" : "NORMAL";
        return new FrozenWorkspace.TatView(elapsedMinutes, status, policy.warningHours(), policy.overdueHours());
    }

    private List<NotificationAttempt> notificationAttempts(Notification notification) {
        return integration.attempts(notification.messageLogId()).stream().map(item -> new NotificationAttempt(
                item.attemptId(), item.attemptNo(), item.startedAt(), item.resultCode(), item.errorCode(),
                item.errorMessage())).toList();
    }

    private String comparisonDiagnosis(String diagnosisSnapshot, String reportStatus, boolean frozen) {
        if ("WITHDRAWN".equals(reportStatus)) {
            return frozen ? "冰冻报告已撤回，当前无有效正式报告" : "常规报告已撤回，当前无有效正式报告";
        }
        if (!"EFFECTIVE".equals(reportStatus)) {
            return frozen ? "冰冻诊断尚未形成有效正式报告" : "常规病理尚未完成诊断";
        }
        try {
            String text = objectMapper.readTree(diagnosisSnapshot).path("diagnosisText").asText();
            return value(text, frozen ? "冰冻正式报告未记录诊断文字" : "常规正式报告未记录诊断文字");
        } catch (Exception ignored) {
            return frozen ? "冰冻正式报告诊断快照无法读取" : "常规正式报告诊断快照无法读取";
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long comparisonTat(Instant arrival, Instant signedAt) {
        return Math.max(0, Duration.between(arrival, signedAt == null ? Instant.now() : signedAt).toMinutes());
    }

    private void lockCase(UUID caseId, ActorContext actor) {
        if (!repository.lockCase(caseId, actor.hospitalScope())) {
            throw reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围");
        }
    }

    private void rejectIfEnded(UUID caseId, ActorContext actor) {
        if (repository.findEnd(caseId, actor.hospitalScope()).isPresent()) {
            throw reject("V2-FROZEN-ENDED", "冰冻病例已结束，不能新增轮次或标本");
        }
    }

    private Case frozenCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode()) || !FROZEN.equals(pathologyCase.businessTypeCode())) {
            throw reject("V2-FROZEN-CASE-REQUIRED", "当前病例不是可操作的 ACTIVE 冰冻病例");
        }
        return pathologyCase;
    }

    private Case frozenCaseForView(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "Frozen case not found in current data scope"));
        if (!FROZEN.equals(pathologyCase.businessTypeCode())) {
            throw reject("V2-FROZEN-CASE-REQUIRED", "The selected case is not a Frozen case");
        }
        return pathologyCase;
    }

    private OptionalSpecimen existingSpecimen(UUID caseId, String code, ActorContext actor) {
        UUID specimenId = registrationRepository.findSpecimenIdByCode(caseId, code).orElse(null);
        return new OptionalSpecimen(specimenId);
    }

    private RoundResult roundResult(FrozenRound round, String organizationReference, boolean duplicate) {
        Production production = repository.production(round.id());
        return new RoundResult(round.id(), round.caseId(), round.roundNo(), round.status(), repository.findSpecimenIds(round.id()),
                production.totalRequired(), production.completedRequired(), production.complete(), duplicate);
    }

    private static void requireKey(String value) { requireText(value, "幂等键不能为空"); }
    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw reject("V2-INVALID-REQUEST", message);
    }
    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }
    private static P15BusinessException conflict(String message) { return new P15BusinessException("V2-VERSION-CONFLICT", message, 409); }

    private record OptionalSpecimen(UUID id) { }

    public record OpenRoundCommand(Instant arrivalTime, String idempotencyKey, boolean createNew) {
        public OpenRoundCommand(Instant arrivalTime, String idempotencyKey) { this(arrivalTime, idempotencyKey, false); }
    }

    public record RegisterFrozenSpecimenCommand(String specimenCode, String specimenKindCode, String collectionSite,
            String collectionMethodCode, String labelCode, String idempotencyKey) { }

    public record CancelRoundCommand(String reason, String idempotencyKey) { }

    public record FinishFrozenCommand(String idempotencyKey, List<UUID> specimenIds) {
        public FinishFrozenCommand(String idempotencyKey) { this(idempotencyKey, List.of()); }
    }

    public record RoundResult(UUID roundId, UUID caseId, int roundNo, String status, List<UUID> specimenIds,
            int totalRequiredSlides, int completedRequiredSlides, boolean productionComplete, boolean duplicate) { }

    public record DiagnosisResult(UUID diagnosisId, UUID roundId, boolean duplicate) { }

    public record EndResult(UUID routineCaseId, String routinePathologyNo, List<UUID> routineSpecimenIds,
            boolean duplicate) { }

    public record NotificationResult(UUID messageLogId, String statusCode, int retryCount, String errorCode,
            String errorMessage) { }

    public record NotificationAttempt(UUID attemptId, int attemptNo, Instant attemptedAt, String resultCode,
            String errorCode, String errorMessage) { }

    public record NotificationHistory(UUID reportId, String reportNo, String reportStatus, String target,
            String channel, String statusCode, Instant lastAttemptAt, String errorCode, String errorMessage,
            List<NotificationAttempt> attempts) { }

    public record TatAlertResult(UUID roundId, String statusCode, boolean acknowledged) { }

    public record ComparisonRound(UUID roundId, int roundNo, String specimenSummary, String diagnosisText,
            String reportStatus, Instant signedAt, String doctor, long tatMinutes) { }

    public record ComparisonResult(UUID frozenCaseId, String frozenPathologyNo, UUID routineCaseId,
            String routinePathologyNo, List<ComparisonRound> frozenRounds, String routineDiagnosis,
            String routineReportStatus, Instant routineSignedAt, String routineDoctor) { }

    public record RoundView(UUID roundId, int roundNo, String status, List<RoundSpecimenView> specimens,
            int totalRequiredSlides, int completedRequiredSlides, boolean productionComplete, UUID diagnosisId,
            Instant arrivalTime, Instant registeredAt, Instant grossingStartTime, Instant slideCompletedTime,
            Instant diagnosisSignedTime, Instant cancelledAt, String cancellationReason, long elapsedMinutes,
            String tatStatus, boolean tatAlertAcknowledged, String notificationStatus, UUID notificationMessageLogId,
            List<NotificationAttempt> notificationAttempts, String reportStatus) { }

    public record RoundSpecimenView(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            String collectionSite, String specimenName) { }

    public record FrozenWorkspace(UUID frozenCaseId, String pathologyNo, String businessTypeCode, List<RoundView> rounds,
            UUID routineCaseId, String routinePathologyNo, boolean ended) {
        public record TatView(long elapsedMinutes, String status, BigDecimal warningHours, BigDecimal overdueHours) { }
    }
}

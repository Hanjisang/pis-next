package com.hanjisang.pis.v2.frozen.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
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
import com.hanjisang.pis.v2.frozen.infrastructure.JdbcV2FrozenRoundRepository.Production;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2FrozenApplicationService {

    private static final String FROZEN = "FROZEN";
    private static final String ROUTINE = "HISTOLOGY";
    private static final String FROZEN_PERMISSION = "P14-PERM-008";
    private static final String DIAGNOSIS_PERMISSION = "P14-PERM-034";

    private final JdbcV2FrozenRoundRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2DiagnosisRepository diagnosisRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public V2FrozenApplicationService(JdbcV2FrozenRoundRepository repository,
            JdbcV2RegistrationRepository registrationRepository, JdbcV2DiagnosisRepository diagnosisRepository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public RoundResult openRound(UUID caseId, OpenRoundCommand command) {
        ActorContext actor = authorization.require(FROZEN_PERMISSION);
        requireKey(command.idempotencyKey());
        repository.lockCase(caseId, actor.hospitalScope());
        Case frozenCase = frozenCase(caseId, actor);
        if (repository.findEnd(caseId, actor.hospitalScope()).isPresent()) {
            throw reject("V2-FROZEN-ENDED", "冰冻病例已结束，不能建立新轮次");
        }
        FrozenRound existing = repository.findCurrent(caseId, actor.hospitalScope()).orElse(null);
        if (existing != null) return roundResult(existing, actor.hospitalScope(), false);
        Instant now = Instant.now();
        FrozenRound round = FrozenRound.open(UUID.randomUUID(), frozenCase.id(), repository.nextRoundNo(caseId),
                command.arrivalTime() == null ? now : command.arrivalTime(), now);
        repository.insert(round, actor.hospitalScope(), actor.actorId(), now);
        linkUnassignedSpecimens(round, actor, now);
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
        repository.lockCase(caseId, actor.hospitalScope());
        Case frozenCase = frozenCase(caseId, actor);
        FrozenRound round = repository.findCurrent(caseId, actor.hospitalScope()).orElse(null);
        if (round == null) {
            round = openRoundInternal(frozenCase, actor, Instant.now());
        }
        if (!round.acceptsSpecimen()) round = openRoundInternal(frozenCase, actor, Instant.now());
        if (registrationRepository.findSpecimenIdByCode(caseId, command.specimenCode()).isPresent()) {
            throw reject("V2-FROZEN-SPECIMEN-CONFLICT", "当前病例下冰冻标本代码已存在");
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

    @Transactional
    public DiagnosisResult createDiagnosis(UUID roundId, String idempotencyKey) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireKey(idempotencyKey);
        FrozenRound round = repository.find(roundId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FROZEN-ROUND-NOT-FOUND", "冰冻轮次不存在"));
        Production production = repository.production(roundId);
        if (!production.complete()) throw reject("V2-FROZEN-PRODUCTION-INCOMPLETE", "当前冰冻轮次切片尚未全部完成");
        Diagnosis existing = diagnosisRepository.findDiagnosisByContext(DiagnosisContextType.FROZEN_ROUND, roundId,
                actor.hospitalScope()).orElse(null);
        if (existing != null) return new DiagnosisResult(existing.id(), round.id(), true);
        Case frozenCase = frozenCase(round.caseId(), actor);
        DiagnosisTemplateVersion template = diagnosisRepository.findPublishedTemplateVersion(frozenCase.businessTypeId(),
                actor.hospitalScope()).orElseThrow(() -> reject("V2-FROZEN-DIAGNOSIS-TEMPLATE", "冰冻诊断模板未发布"));
        Instant now = Instant.now();
        Diagnosis diagnosis = Diagnosis.createForContext(UUID.randomUUID(), frozenCase.id(),
                DiagnosisContextType.FROZEN_ROUND, round.id(), template.id(), "{}", null, null, null, now,
                actor.actorId());
        diagnosisRepository.insertDiagnosis(diagnosis, actor.hospitalScope(), now, actor.actorId());
        ResponsibilityUnit responsibility = ResponsibilityUnit.assign(UUID.randomUUID(), diagnosis.id(),
                ResponsibilityRole.INITIAL, actor.actorId(), diagnosisRepository.nextResponsibilitySequence(diagnosis.id()),
                AssignmentSource.SELF_CLAIM, "冰冻轮次快速诊断", now, actor.actorId());
        diagnosisRepository.insertResponsibility(responsibility);
        audit.append("PIS-V2-I06-FROZEN-DIAGNOSIS-CREATE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED",
                diagnosis.id(), "V2-DIAGNOSIS", UUID.randomUUID().toString(), "roundNo=" + round.roundNo());
        return new DiagnosisResult(diagnosis.id(), round.id(), false);
    }

    @Transactional(readOnly = true)
    public FrozenWorkspace workspace(UUID caseId) {
        var frozenAccess = authorization.decide(FROZEN_PERMISSION);
        ActorContext actor = frozenAccess.allowed() ? frozenAccess.actor() : authorization.require(DIAGNOSIS_PERMISSION);
        Case frozenCase = frozenCase(caseId, actor);
        List<RoundView> rounds = repository.findByCase(caseId, actor.hospitalScope()).stream().map(round -> {
            Production production = repository.production(round.id());
            UUID diagnosisId = diagnosisRepository.findDiagnosisByContext(DiagnosisContextType.FROZEN_ROUND, round.id(),
                    actor.hospitalScope()).map(Diagnosis::id).orElse(null);
            List<RoundSpecimenView> specimens = repository.findSpecimenIds(round.id()).stream()
                    .map(specimenId -> registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                            .map(item -> new RoundSpecimenView(item.id(), item.specimenNo(), item.specimenCode(),
                                    item.specimenKindCode(), item.collectionSite()))
                            .orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return new RoundView(round.id(), round.roundNo(), round.status(), specimens,
                    production.totalRequired(), production.completedRequired(), production.complete(), diagnosisId,
                    round.arrivalTime(), round.registeredAt(), round.grossingStartTime(), round.slideCompletedTime(),
                    round.diagnosisSignedTime());
        }).toList();
        return new FrozenWorkspace(frozenCase.id(), frozenCase.caseNo(), frozenCase.businessTypeCode(), rounds,
                repository.findEnd(caseId, actor.hospitalScope()).map(JdbcV2FrozenRoundRepository.FrozenEnd::routineCaseId)
                        .orElse(null));
    }

    @Transactional
    public EndResult finishFrozenCase(UUID caseId, FinishFrozenCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireKey(command.idempotencyKey());
        repository.lockCase(caseId, actor.hospitalScope());
        Case frozenCase = frozenCase(caseId, actor);
        var existing = repository.findEnd(caseId, actor.hospitalScope());
        if (existing.isPresent()) {
            // Frozen End is a case-level fact. A browser retry after refresh can
            // carry a new transport idempotency key, but it must still replay the
            // existing Routine Case instead of creating or rejecting another one.
            return new EndResult(existing.get().routineCaseId(), true);
        }
        List<FrozenRound> rounds = repository.findByCase(caseId, actor.hospitalScope());
        if (rounds.isEmpty() || rounds.stream().anyMatch(round -> !FrozenRound.SIGNED.equals(round.status()))) {
            throw reject("V2-FROZEN-END-BLOCKED", "所有冰冻轮次必须先独立签发");
        }
        Instant now = Instant.now();
        for (FrozenRound round : rounds) {
            if (!FrozenRound.ENDED.equals(round.status())) {
                long version = round.version();
                round.end(now, actor.actorId());
                if (!repository.update(round, actor.hospitalScope(), version)) throw conflict("冰冻轮次结束发生并发冲突");
            }
        }
        var businessType = registrationRepository.findBusinessType(ROUTINE)
                .orElseThrow(() -> reject("V2-FROZEN-END-ROUTINE-TYPE", "组织病理业务类型不存在"));
        UUID routineId = UUID.randomUUID();
        String routineNo = registrationRepository.allocateNumber(actor.hospitalScope(), ROUTINE, "CASE", now);
        Case routine = Case.routineFromFrozen(routineId, routineNo, "V2-FROZEN-END", "FROZEN:" + frozenCase.id(),
                "SYNTH-HISTOLOGY", businessType.id(), businessType.code(), frozenCase.patientReference(),
                frozenCase.visitReference(), frozenCase.id());
        registrationRepository.insertCase(routine, actor.hospitalScope(), now, actor.actorId());
        UUID specimenId = UUID.randomUUID();
        Specimen remainder = Specimen.register(specimenId, routine.id(),
                registrationRepository.allocateNumber(actor.hospitalScope(), ROUTINE, "SPECIMEN", now),
                "FROZEN-REMAINDER", "TISSUE", "FROZEN_REMAINDER", frozenCase.id().toString(), "冰冻剩余组织",
                "TRANSFER_FROM_FROZEN", null);
        registrationRepository.insertSpecimen(remainder, actor.hospitalScope(), actor.actorId(), now);
        repository.insertEnd(new JdbcV2FrozenRoundRepository.FrozenEnd(UUID.randomUUID(), caseId, routineId,
                command.idempotencyKey(), now, actor.actorId()));
        audit.append("PIS-V2-I06-FROZEN-END", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId,
                "V2-FROZEN-CASE", UUID.randomUUID().toString(), "routineCaseId=" + routineId);
        outbox.append("V2-I06-FROZEN-ENDED", caseId, "V2-CASE", frozenCase.concurrencyVersion(),
                UUID.randomUUID().toString(), command.idempotencyKey(), actor.actorId());
        return new EndResult(routineId, false);
    }

    public void markReportSigned(UUID diagnosisId, String organizationReference) {
        FrozenRound round = repository.findByDiagnosis(diagnosisId, organizationReference).orElse(null);
        if (round == null || FrozenRound.SIGNED.equals(round.status())) return;
        long version = round.version();
        round.markSigned(Instant.now());
        if (!repository.update(round, organizationReference, version)) throw conflict("冰冻轮次签发状态更新发生并发冲突");
    }

    private FrozenRound openRoundInternal(Case frozenCase, ActorContext actor, Instant now) {
        FrozenRound round = FrozenRound.open(UUID.randomUUID(), frozenCase.id(), repository.nextRoundNo(frozenCase.id()),
                now, now);
        repository.insert(round, actor.hospitalScope(), actor.actorId(), now);
        return round;
    }

    private void linkUnassignedSpecimens(FrozenRound round, ActorContext actor, Instant now) {
        int sequence = repository.nextSpecimenSequence(round.id());
        for (UUID specimenId : repository.findUnlinkedCaseSpecimenIds(round.caseId(), actor.hospitalScope())) {
            repository.linkSpecimen(round.id(), specimenId, sequence++, actor.actorId(), now);
        }
    }

    private Case frozenCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode()) || !FROZEN.equals(pathologyCase.businessTypeCode())) {
            throw reject("V2-FROZEN-CASE-REQUIRED", "当前病例不是ACTIVE冰冻病例");
        }
        return pathologyCase;
    }

    private static void requireKey(String value) { requireText(value, "幂等键不能为空"); }
    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw reject("V2-INVALID-REQUEST", message);
    }
    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }
    private static P15BusinessException conflict(String message) { return new P15BusinessException("V2-VERSION-CONFLICT", message, 409); }

    private RoundResult roundResult(FrozenRound round, String organizationReference, boolean duplicate) {
        Production production = repository.production(round.id());
        return new RoundResult(round.id(), round.caseId(), round.roundNo(), round.status(), repository.findSpecimenIds(round.id()),
                production.totalRequired(), production.completedRequired(), production.complete(), duplicate);
    }

    public record OpenRoundCommand(Instant arrivalTime, String idempotencyKey) { }
    public record RegisterFrozenSpecimenCommand(String specimenCode, String specimenKindCode, String collectionSite,
            String collectionMethodCode, String labelCode, String idempotencyKey) { }
    public record FinishFrozenCommand(String idempotencyKey) { }
    public record RoundResult(UUID roundId, UUID caseId, int roundNo, String status, List<UUID> specimenIds,
            int totalRequiredSlides, int completedRequiredSlides, boolean productionComplete, boolean duplicate) { }
    public record DiagnosisResult(UUID diagnosisId, UUID roundId, boolean duplicate) { }
    public record EndResult(UUID routineCaseId, boolean duplicate) { }
    public record RoundView(UUID roundId, int roundNo, String status, List<RoundSpecimenView> specimens,
            int totalRequiredSlides, int completedRequiredSlides, boolean productionComplete, UUID diagnosisId,
            Instant arrivalTime, Instant registeredAt, Instant grossingStartTime, Instant slideCompletedTime,
            Instant diagnosisSignedTime) { }
    public record RoundSpecimenView(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            String collectionSite) { }
    public record FrozenWorkspace(UUID frozenCaseId, String pathologyNo, String businessTypeCode, List<RoundView> rounds,
            UUID routineCaseId) { }
}

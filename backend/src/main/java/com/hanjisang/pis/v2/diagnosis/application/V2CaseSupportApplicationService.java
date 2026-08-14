package com.hanjisang.pis.v2.diagnosis.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2CaseSupportRepository;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2CaseSupportRepository.ConsultationRow;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2CaseSupportRepository.FollowUpRow;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository.IdempotencyResult;

@Service
public class V2CaseSupportApplicationService {

    private static final String DIAGNOSIS_PERMISSION = "P14-PERM-034";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2CaseSupportRepository repository;
    private final JdbcV2DiagnosisRepository diagnosisRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2CaseSupportApplicationService(JdbcV2CaseSupportRepository repository,
            JdbcV2DiagnosisRepository diagnosisRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit) {
        this.repository = repository;
        this.diagnosisRepository = diagnosisRepository;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional
    public FavoriteResult favorite(UUID caseId) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireCase(caseId, actor, true);
        repository.addFavorite(caseId, actor.actorId(), actor.hospitalScope(), Instant.now());
        audit.append("PIS-V2-CASE-FAVORITE-ADD", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId,
                "V2-CASE-FAVORITE", UUID.randomUUID().toString(), "病例已收藏");
        return new FavoriteResult(caseId, true);
    }

    @Transactional
    public FavoriteResult unfavorite(UUID caseId) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireCase(caseId, actor, true);
        repository.removeFavorite(caseId, actor.actorId(), actor.hospitalScope());
        audit.append("PIS-V2-CASE-FAVORITE-REMOVE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId,
                "V2-CASE-FAVORITE", UUID.randomUUID().toString(), "病例已取消收藏");
        return new FavoriteResult(caseId, false);
    }

    @Transactional(readOnly = true)
    public FavoriteResult favoriteState(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        requireCase(caseId, actor, false);
        return new FavoriteResult(caseId, repository.isFavorite(caseId, actor.actorId(), actor.hospitalScope()));
    }

    @Transactional(readOnly = true)
    public List<UUID> favorites() {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return repository.favorites(actor.actorId(), actor.hospitalScope());
    }

    @Transactional
    public FollowUpResult createFollowUp(UUID caseId, FollowUpCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireKey(command.idempotencyKey());
        if (command.followUpDate() == null || command.plan() == null || command.plan().isBlank()) {
            throw reject("V2-FOLLOW-UP-INVALID", "随访日期和计划不能为空");
        }
        String operation = "PIS-V2-CASE-FOLLOW-UP-CREATE";
        String commandDigest = digest(caseId, command.followUpDate(), command.plan().trim());
        FollowUpResult replay = replayFollowUp(operation, command.idempotencyKey(), commandDigest, actor);
        if (replay != null) return replay;
        requireCase(caseId, actor, true);
        replay = replayFollowUp(operation, command.idempotencyKey(), commandDigest, actor);
        if (replay != null) return replay;
        Instant now = Instant.now();
        UUID id = repository.insertFollowUp(caseId, command.followUpDate(), command.plan().trim(), actor.actorId(),
                now, actor.hospitalScope());
        diagnosisRepository.insertIdempotency(operation, command.idempotencyKey(), commandDigest, "CASE_FOLLOW_UP",
                id, actor.actorId(), now);
        audit.append(operation, DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CASE-FOLLOW-UP", UUID.randomUUID().toString(), "随访计划已记录");
        return followUp(repository.findFollowUp(id, actor.hospitalScope()).orElseThrow());
    }

    @Transactional
    public FollowUpResult completeFollowUp(UUID followUpId, CompleteFollowUpCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireKey(command.idempotencyKey());
        if (blank(command.content()) && blank(command.result())) {
            throw reject("V2-FOLLOW-UP-INVALID", "随访内容和结果至少填写一项");
        }
        String operation = "PIS-V2-CASE-FOLLOW-UP-COMPLETE";
        String commandDigest = digest(followUpId, command.content(), command.result());
        FollowUpResult replay = replayFollowUp(operation, command.idempotencyKey(), commandDigest, actor);
        if (replay != null) return replay;
        FollowUpRow existing = repository.findFollowUp(followUpId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-FOLLOW-UP-NOT-FOUND", "随访不存在或不在当前数据范围"));
        requireCase(existing.caseId(), actor, true);
        replay = replayFollowUp(operation, command.idempotencyKey(), commandDigest, actor);
        if (replay != null) return replay;
        Instant now = Instant.now();
        FollowUpRow row = repository.completeFollowUp(followUpId, command.content(), command.result(), now,
                actor.hospitalScope()).orElseThrow(() -> reject("V2-FOLLOW-UP-CONFLICT", "随访已完成或不存在"));
        diagnosisRepository.insertIdempotency(operation, command.idempotencyKey(), commandDigest, "CASE_FOLLOW_UP",
                followUpId, actor.actorId(), now);
        audit.append(operation, DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", followUpId,
                "V2-CASE-FOLLOW-UP", UUID.randomUUID().toString(), "随访已完成");
        return followUp(row);
    }

    @Transactional(readOnly = true)
    public List<FollowUpResult> followUps(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        requireCase(caseId, actor, false);
        return repository.followUps(caseId, actor.hospitalScope()).stream().map(this::followUp).toList();
    }

    @Transactional
    public ConsultationResult createConsultation(UUID caseId, ConsultationCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        requireKey(command.idempotencyKey());
        require(command.initiatorRef(), "发起医生不能为空");
        require(command.participantRefs(), "参与医生不能为空");
        require(command.reason(), "会诊原因不能为空");
        Instant now = Instant.now();
        String operation = "PIS-V2-CASE-CONSULTATION-CREATE";
        String commandDigest = digest(caseId, command.consultationAt(), command.initiatorRef().trim(),
                command.participantRefs().trim(), command.reason().trim(), command.discussion(), command.conclusion(),
                command.note(), command.attachmentReference());
        ConsultationResult replay = replayConsultation(operation, command.idempotencyKey(), commandDigest, actor);
        if (replay != null) return replay;
        requireCase(caseId, actor, true);
        replay = replayConsultation(operation, command.idempotencyKey(), commandDigest, actor);
        if (replay != null) return replay;
        ConsultationRow input = new ConsultationRow(null, caseId,
                command.consultationAt() == null ? now : command.consultationAt(), command.initiatorRef().trim(),
                command.participantRefs().trim(), command.reason().trim(), command.discussion(), command.conclusion(),
                command.note(), command.attachmentReference(), actor.actorId(), now);
        UUID id = repository.insertConsultation(input, actor.hospitalScope());
        diagnosisRepository.insertIdempotency(operation, command.idempotencyKey(), commandDigest, "CASE_CONSULTATION",
                id, actor.actorId(), now);
        audit.append(operation, DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CASE-CONSULTATION", UUID.randomUUID().toString(), "会诊记录已保存");
        return consultation(repository.findConsultation(id, actor.hospitalScope()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<ConsultationResult> consultations(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        requireCase(caseId, actor, false);
        return repository.consultations(caseId, actor.hospitalScope()).stream().map(this::consultation).toList();
    }

    private FollowUpResult replayFollowUp(String operation, String key, String commandDigest, ActorContext actor) {
        IdempotencyResult existing = diagnosisRepository.findIdempotency(operation, key).orElse(null);
        if (existing == null) return null;
        requireDigest(existing, commandDigest);
        return followUp(repository.findFollowUp(existing.resultEntityId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-CONFLICT", "幂等结果不在当前数据范围")));
    }

    private ConsultationResult replayConsultation(String operation, String key, String commandDigest,
            ActorContext actor) {
        IdempotencyResult existing = diagnosisRepository.findIdempotency(operation, key).orElse(null);
        if (existing == null) return null;
        requireDigest(existing, commandDigest);
        return consultation(repository.findConsultation(existing.resultEntityId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-CONFLICT", "幂等结果不在当前数据范围")));
    }

    private void requireCase(UUID caseId, ActorContext actor, boolean lock) {
        boolean found = lock ? repository.lockCaseInScope(caseId, actor.hospitalScope())
                : repository.caseInScope(caseId, actor.hospitalScope());
        if (!found) throw reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围");
    }

    private static void requireDigest(IdempotencyResult existing, String commandDigest) {
        if (!existing.payloadDigest().equals(commandDigest)) {
            throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的病例支持命令摘要冲突");
        }
    }

    private FollowUpResult followUp(FollowUpRow row) {
        return new FollowUpResult(row.followUpId(), row.caseId(), row.followUpDate(), row.plan(), row.content(),
                row.result(), row.operatorRef(), row.createdAt(), row.completedAt());
    }

    private ConsultationResult consultation(ConsultationRow row) {
        return new ConsultationResult(row.consultationId(), row.caseId(), row.consultationAt(), row.initiatorRef(),
                row.participantRefs(), row.reason(), row.discussion(), row.conclusion(), row.note(),
                row.attachmentReference(), row.recordedByRef(), row.createdAt());
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) throw reject("V2-CONSULTATION-INVALID", message);
    }

    private static void requireKey(String value) {
        if (blank(value)) throw reject("V2-IDEMPOTENCY-KEY-REQUIRED", "幂等键不能为空");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String digest(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }

    public record FollowUpCommand(LocalDate followUpDate, String plan, String idempotencyKey) { }
    public record CompleteFollowUpCommand(String content, String result, String idempotencyKey) { }
    public record ConsultationCommand(Instant consultationAt, String initiatorRef, String participantRefs,
            String reason, String discussion, String conclusion, String note, String attachmentReference,
            String idempotencyKey) { }
    public record FavoriteResult(UUID caseId, boolean favorite) { }
    public record FollowUpResult(UUID followUpId, UUID caseId, LocalDate followUpDate, String plan, String content,
            String result, String operatorRef, Instant createdAt, Instant completedAt) { }
    public record ConsultationResult(UUID consultationId, UUID caseId, Instant consultationAt, String initiatorRef,
            String participantRefs, String reason, String discussion, String conclusion, String note,
            String attachmentReference, String recordedByRef, Instant createdAt) { }
}

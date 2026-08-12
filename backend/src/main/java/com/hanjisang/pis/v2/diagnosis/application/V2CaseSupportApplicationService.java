package com.hanjisang.pis.v2.diagnosis.application;

import java.time.Instant;
import java.time.LocalDate;
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

@Service
public class V2CaseSupportApplicationService {

    private static final String DIAGNOSIS_PERMISSION = "P14-PERM-034";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2CaseSupportRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2CaseSupportApplicationService(JdbcV2CaseSupportRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional
    public FavoriteResult favorite(UUID caseId) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        repository.addFavorite(caseId, actor.actorId(), actor.hospitalScope(), Instant.now());
        audit.append("PIS-V2-CASE-FAVORITE-ADD", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId,
                "V2-CASE-FAVORITE", UUID.randomUUID().toString(), "病例已收藏");
        return new FavoriteResult(caseId, true);
    }

    @Transactional
    public FavoriteResult unfavorite(UUID caseId) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        repository.removeFavorite(caseId, actor.actorId(), actor.hospitalScope());
        audit.append("PIS-V2-CASE-FAVORITE-REMOVE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId,
                "V2-CASE-FAVORITE", UUID.randomUUID().toString(), "病例已取消收藏");
        return new FavoriteResult(caseId, false);
    }

    @Transactional(readOnly = true)
    public FavoriteResult favoriteState(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
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
        if (command.followUpDate() == null || command.plan() == null || command.plan().isBlank()) {
            throw reject("V2-FOLLOW-UP-INVALID", "随访日期和计划不能为空");
        }
        UUID id = repository.insertFollowUp(caseId, command.followUpDate(), command.plan().trim(), actor.actorId(),
                Instant.now(), actor.hospitalScope());
        audit.append("PIS-V2-CASE-FOLLOW-UP-CREATE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CASE-FOLLOW-UP", UUID.randomUUID().toString(), "随访计划已记录");
        return followUp(repository.followUps(caseId, actor.hospitalScope()).stream()
                .filter(item -> item.followUpId().equals(id)).findFirst().orElseThrow());
    }

    @Transactional
    public FollowUpResult completeFollowUp(UUID followUpId, CompleteFollowUpCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        FollowUpRow row = repository.completeFollowUp(followUpId, command.content(), command.result(), Instant.now(),
                actor.hospitalScope()).orElseThrow(() -> reject("V2-FOLLOW-UP-CONFLICT", "随访已完成或不存在"));
        audit.append("PIS-V2-CASE-FOLLOW-UP-COMPLETE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", followUpId,
                "V2-CASE-FOLLOW-UP", UUID.randomUUID().toString(), "随访已完成");
        return followUp(row);
    }

    @Transactional(readOnly = true)
    public List<FollowUpResult> followUps(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return repository.followUps(caseId, actor.hospitalScope()).stream().map(this::followUp).toList();
    }

    @Transactional
    public ConsultationResult createConsultation(UUID caseId, ConsultationCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_PERMISSION);
        require(command.initiatorRef(), "发起医生不能为空");
        require(command.participantRefs(), "参与医生不能为空");
        require(command.reason(), "会诊原因不能为空");
        Instant now = Instant.now();
        ConsultationRow input = new ConsultationRow(null, caseId,
                command.consultationAt() == null ? now : command.consultationAt(), command.initiatorRef().trim(),
                command.participantRefs().trim(), command.reason().trim(), command.discussion(), command.conclusion(),
                command.note(), command.attachmentReference(), actor.actorId(), now);
        UUID id = repository.insertConsultation(input, actor.hospitalScope());
        audit.append("PIS-V2-CASE-CONSULTATION-CREATE", DIAGNOSIS_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CASE-CONSULTATION", UUID.randomUUID().toString(), "会诊记录已保存");
        return consultation(repository.consultations(caseId, actor.hospitalScope()).stream()
                .filter(item -> item.consultationId().equals(id)).findFirst().orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<ConsultationResult> consultations(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return repository.consultations(caseId, actor.hospitalScope()).stream().map(this::consultation).toList();
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

    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }

    public record FollowUpCommand(LocalDate followUpDate, String plan) { }
    public record CompleteFollowUpCommand(String content, String result) { }
    public record ConsultationCommand(Instant consultationAt, String initiatorRef, String participantRefs,
            String reason, String discussion, String conclusion, String note, String attachmentReference) { }
    public record FavoriteResult(UUID caseId, boolean favorite) { }
    public record FollowUpResult(UUID followUpId, UUID caseId, LocalDate followUpDate, String plan, String content,
            String result, String operatorRef, Instant createdAt, Instant completedAt) { }
    public record ConsultationResult(UUID consultationId, UUID caseId, Instant consultationAt, String initiatorRef,
            String participantRefs, String reason, String discussion, String conclusion, String note,
            String attachmentReference, String recordedByRef, Instant createdAt) { }
}

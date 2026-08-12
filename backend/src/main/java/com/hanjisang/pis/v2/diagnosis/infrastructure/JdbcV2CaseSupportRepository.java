package com.hanjisang.pis.v2.diagnosis.infrastructure;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2CaseSupportRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2CaseSupportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isFavorite(UUID caseId, String userReference, String organizationReference) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.case_favorite
                 WHERE case_id = ? AND user_reference = ? AND organization_reference = ?
                """, Integer.class, caseId, userReference, organizationReference);
        return count != null && count == 1;
    }

    public void addFavorite(UUID caseId, String userReference, String organizationReference, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO pis_v2.case_favorite (case_id, user_reference, organization_reference, created_at)
                    VALUES (?, ?, ?, ?)
                    """, caseId, userReference, organizationReference, Timestamp.from(now));
        } catch (DuplicateKeyException ignored) {
            // Favorite is an idempotent user action.
        }
    }

    public void removeFavorite(UUID caseId, String userReference, String organizationReference) {
        jdbc.update("""
                DELETE FROM pis_v2.case_favorite
                 WHERE case_id = ? AND user_reference = ? AND organization_reference = ?
                """, caseId, userReference, organizationReference);
    }

    public List<UUID> favorites(String userReference, String organizationReference) {
        return jdbc.queryForList("""
                SELECT case_id FROM pis_v2.case_favorite
                 WHERE user_reference = ? AND organization_reference = ? ORDER BY created_at DESC
                """, UUID.class, userReference, organizationReference);
    }

    public UUID insertFollowUp(UUID caseId, LocalDate date, String plan, String operatorRef, Instant now,
            String organizationReference) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.case_follow_up
                    (id, case_id, follow_up_date, plan, operator_ref, created_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, caseId, Date.valueOf(date), plan, operatorRef, Timestamp.from(now), organizationReference);
        return id;
    }

    public List<FollowUpRow> followUps(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT id, case_id, follow_up_date, plan, content, result, operator_ref, created_at, completed_at
                  FROM pis_v2.case_follow_up WHERE case_id = ? AND organization_reference = ?
                 ORDER BY follow_up_date DESC, created_at DESC
                """, (rs, rowNum) -> new FollowUpRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getDate("follow_up_date").toLocalDate(), rs.getString("plan"), rs.getString("content"),
                rs.getString("result"), rs.getString("operator_ref"), rs.getTimestamp("created_at").toInstant(),
                instant(rs, "completed_at")), caseId, organizationReference);
    }

    public Optional<FollowUpRow> completeFollowUp(UUID followUpId, String content, String result,
            Instant completedAt, String organizationReference) {
        int changed = jdbc.update("""
                UPDATE pis_v2.case_follow_up SET content = ?, result = ?, completed_at = ?
                 WHERE id = ? AND organization_reference = ? AND completed_at IS NULL
                """, content, result, Timestamp.from(completedAt), followUpId, organizationReference);
        if (changed != 1) return Optional.empty();
        return jdbc.query("""
                SELECT id, case_id, follow_up_date, plan, content, result, operator_ref, created_at, completed_at
                  FROM pis_v2.case_follow_up WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new FollowUpRow(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getDate("follow_up_date").toLocalDate(), rs.getString("plan"),
                rs.getString("content"), rs.getString("result"), rs.getString("operator_ref"),
                rs.getTimestamp("created_at").toInstant(), instant(rs, "completed_at"))) : Optional.empty(), followUpId,
                organizationReference);
    }

    public UUID insertConsultation(ConsultationRow item, String organizationReference) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.case_consultation
                    (id, case_id, consultation_at, initiator_ref, participant_refs, reason, discussion,
                     conclusion, note, attachment_reference, recorded_by_ref, created_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, item.caseId(), Timestamp.from(item.consultationAt()), item.initiatorRef(),
                item.participantRefs(), item.reason(), item.discussion(), item.conclusion(), item.note(),
                item.attachmentReference(), item.recordedByRef(), Timestamp.from(item.createdAt()), organizationReference);
        return id;
    }

    public List<ConsultationRow> consultations(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT id, case_id, consultation_at, initiator_ref, participant_refs, reason, discussion,
                       conclusion, note, attachment_reference, recorded_by_ref, created_at
                  FROM pis_v2.case_consultation WHERE case_id = ? AND organization_reference = ?
                 ORDER BY consultation_at DESC, id DESC
                """, (rs, rowNum) -> new ConsultationRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getTimestamp("consultation_at").toInstant(), rs.getString("initiator_ref"),
                rs.getString("participant_refs"), rs.getString("reason"), rs.getString("discussion"),
                rs.getString("conclusion"), rs.getString("note"), rs.getString("attachment_reference"),
                rs.getString("recorded_by_ref"), rs.getTimestamp("created_at").toInstant()), caseId, organizationReference);
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record FollowUpRow(UUID followUpId, UUID caseId, LocalDate followUpDate, String plan, String content,
            String result, String operatorRef, Instant createdAt, Instant completedAt) { }
    public record ConsultationRow(UUID consultationId, UUID caseId, Instant consultationAt, String initiatorRef,
            String participantRefs, String reason, String discussion, String conclusion, String note,
            String attachmentReference, String recordedByRef, Instant createdAt) { }
}

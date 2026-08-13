package com.hanjisang.pis.v2.frozen.infrastructure;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.frozen.domain.FrozenRound;

@Repository
public class JdbcV2FrozenRoundRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2FrozenRoundRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean lockCase(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT id FROM pis_v2.pathology_case
                WHERE id = ? AND organization_reference = ? FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), caseId, organizationReference);
    }

    public Optional<FrozenRound> find(UUID roundId, String organizationReference) {
        return jdbc.query(select() + " WHERE r.id = ? AND r.organization_reference = ?",
                rs -> rs.next() ? Optional.of(toRound(rs)) : Optional.empty(), roundId, organizationReference);
    }

    public Optional<FrozenRound> findCurrent(UUID caseId, String organizationReference) {
        return jdbc.query(select() + " WHERE r.case_id = ? AND r.organization_reference = ?"
                + " AND r.status_code IN ('OPEN', 'PRODUCTION_COMPLETE') ORDER BY r.round_no DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(toRound(rs)) : Optional.empty(), caseId, organizationReference);
    }

    public List<FrozenRound> findByCase(UUID caseId, String organizationReference) {
        return jdbc.query(select() + " WHERE r.case_id = ? AND r.organization_reference = ? ORDER BY r.round_no",
                (rs, rowNum) -> toRound(rs), caseId, organizationReference);
    }

    public Optional<FrozenRound> findByDiagnosis(UUID diagnosisId, String organizationReference) {
        return jdbc.query(select() + " JOIN pis_v2.diagnosis d ON d.context_type = 'FROZEN_ROUND'"
                + " AND d.context_id = r.id WHERE d.id = ? AND r.organization_reference = ?",
                rs -> rs.next() ? Optional.of(toRound(rs)) : Optional.empty(), diagnosisId, organizationReference);
    }

    public int nextRoundNo(UUID caseId) {
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(round_no), 0) + 1 FROM pis_v2.frozen_round WHERE case_id = ?
                """, Integer.class, caseId);
        return next == null ? 1 : next;
    }

    public void insert(FrozenRound round, String organizationReference, String actorRef, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.frozen_round
                    (id, case_id, round_no, status_code, arrival_time, registered_at, grossing_start_time,
                     slide_completed_time, diagnosis_signed_time, ended_at, ended_by_ref,
                     cancelled_at, cancelled_by_ref, cancellation_reason, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, round.id(), round.caseId(), round.roundNo(), round.status(), Timestamp.from(round.arrivalTime()),
                Timestamp.from(round.registeredAt()), timestamp(round.grossingStartTime()),
                timestamp(round.slideCompletedTime()), timestamp(round.diagnosisSignedTime()), timestamp(round.endedAt()),
                round.endedBy(), timestamp(round.cancelledAt()), round.cancelledBy(), round.cancellationReason(),
                round.version(), organizationReference, Timestamp.from(now), actorRef);
    }

    public boolean update(FrozenRound round, String organizationReference, long expectedVersion) {
        return jdbc.update("""
                UPDATE pis_v2.frozen_round
                   SET status_code = ?, grossing_start_time = ?, slide_completed_time = ?,
                       diagnosis_signed_time = ?, ended_at = ?, ended_by_ref = ?, cancelled_at = ?,
                       cancelled_by_ref = ?, cancellation_reason = ?, concurrency_version = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                """, round.status(), timestamp(round.grossingStartTime()), timestamp(round.slideCompletedTime()),
                timestamp(round.diagnosisSignedTime()), timestamp(round.endedAt()), round.endedBy(),
                timestamp(round.cancelledAt()), round.cancelledBy(), round.cancellationReason(), round.version(),
                round.id(), organizationReference, expectedVersion) == 1;
    }

    public boolean hasSpecimen(UUID roundId, UUID specimenId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.frozen_round_specimen
                WHERE frozen_round_id = ? AND specimen_id = ?
                """, Integer.class, roundId, specimenId) > 0;
    }

    public Optional<UUID> findRoundIdBySpecimen(UUID specimenId, String organizationReference) {
        return jdbc.query("""
                SELECT frs.frozen_round_id
                  FROM pis_v2.frozen_round_specimen frs
                  JOIN pis_v2.frozen_round fr ON fr.id = frs.frozen_round_id
                 WHERE frs.specimen_id = ? AND fr.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), specimenId,
                organizationReference);
    }

    public boolean specimenBelongsToCase(UUID specimenId, UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT 1 FROM pis_v2.specimen
                 WHERE id = ? AND case_id = ? AND organization_reference = ? AND deleted_at IS NULL
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), specimenId, caseId, organizationReference);
    }

    public int nextSpecimenSequence(UUID roundId) {
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM pis_v2.frozen_round_specimen
                WHERE frozen_round_id = ?
                """, Integer.class, roundId);
        return next == null ? 1 : next;
    }

    public void linkSpecimen(UUID roundId, UUID specimenId, int sequence, String actorRef, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.frozen_round_specimen
                    (frozen_round_id, specimen_id, sequence_no, linked_at, linked_by_ref)
                VALUES (?, ?, ?, ?, ?)
                """, roundId, specimenId, sequence, Timestamp.from(now), actorRef);
    }

    public List<UUID> findSpecimenIds(UUID roundId) {
        return jdbc.queryForList("""
                SELECT specimen_id FROM pis_v2.frozen_round_specimen
                WHERE frozen_round_id = ? ORDER BY sequence_no
                """, UUID.class, roundId);
    }

    public List<UUID> findActiveSpecimenIds(UUID roundId) {
        return jdbc.queryForList("""
                SELECT frs.specimen_id
                  FROM pis_v2.frozen_round_specimen frs
                  JOIN pis_v2.specimen s ON s.id = frs.specimen_id
                 WHERE frs.frozen_round_id = ? AND s.deleted_at IS NULL
                 ORDER BY frs.sequence_no
                """, UUID.class, roundId);
    }

    public List<UUID> findUnlinkedCaseSpecimenIds(UUID caseId, String organizationReference) {
        return jdbc.queryForList("""
                SELECT s.id
                  FROM pis_v2.specimen s
                  JOIN pis_v2.pathology_case c ON c.id = s.case_id
                 WHERE s.case_id = ?
                   AND c.organization_reference = ?
                   AND s.deleted_at IS NULL
                   AND NOT EXISTS (
                       SELECT 1
                         FROM pis_v2.frozen_round_specimen frs
                        WHERE frs.specimen_id = s.id
                   )
                 ORDER BY s.created_at, s.id
                """, UUID.class, caseId, organizationReference);
    }

    public Production production(UUID roundId) {
        return jdbc.query("""
                SELECT
                    (SELECT COUNT(*)
                       FROM pis_v2.frozen_round_specimen frs
                       JOIN pis_v2.specimen sp ON sp.id = frs.specimen_id AND sp.deleted_at IS NULL
                      WHERE frs.frozen_round_id = fr.id)
                    * COALESCE((SELECT SUM(sr.copies)
                                  FROM pis_v2.slide_rule sr
                                 WHERE sr.organization_reference = c.organization_reference
                                   AND sr.business_type_id = c.business_type_id
                                   AND sr.source_context_type = 'FROZEN_ROUND'
                                   AND sr.trigger_code = 'ON_GROSSING_COMPLETE'
                                   AND sr.active = TRUE), 1) AS total_count,
                    (SELECT COUNT(*)
                       FROM pis_v2.slide sl
                       JOIN pis_v2.frozen_round_specimen frs ON frs.frozen_round_id = fr.id
                                                              AND (frs.specimen_id = sl.specimen_id
                                                               OR EXISTS (SELECT 1 FROM pis_v2.block b
                                                                          WHERE b.id = sl.block_id
                                                                            AND b.specimen_id = frs.specimen_id
                                                                            AND b.deleted_at IS NULL))
                       JOIN pis_v2.specimen sp ON sp.id = frs.specimen_id AND sp.deleted_at IS NULL
                      WHERE sl.case_id = c.id
                        AND sl.source_context_type = 'FROZEN_ROUND'
                        AND sl.source_context_id = fr.id
                        AND sl.deleted_at IS NULL
                        AND sl.required = TRUE
                        AND sl.completed_at IS NOT NULL) AS completed_count
                FROM pis_v2.frozen_round fr
                JOIN pis_v2.pathology_case c ON c.id = fr.case_id
                WHERE fr.id = ?
                """, rs -> {
                    if (!rs.next()) return new Production(0, 0);
                    return new Production(rs.getInt("total_count"), rs.getInt("completed_count"));
                }, roundId);
    }

    public Optional<FrozenEnd> findEnd(UUID frozenCaseId, String organizationReference) {
        return jdbc.query("""
                SELECT e.id, e.frozen_case_id, e.routine_case_id, e.idempotency_key, e.ended_at, e.ended_by_ref
                FROM pis_v2.frozen_end e
                JOIN pis_v2.pathology_case c ON c.id = e.frozen_case_id
                WHERE e.frozen_case_id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new FrozenEnd(rs.getObject("id", UUID.class),
                rs.getObject("frozen_case_id", UUID.class), rs.getObject("routine_case_id", UUID.class),
                rs.getString("idempotency_key"), rs.getTimestamp("ended_at").toInstant(),
                rs.getString("ended_by_ref"))) : Optional.empty(), frozenCaseId, organizationReference);
    }

    public void insertEnd(FrozenEnd end) {
        jdbc.update("""
                INSERT INTO pis_v2.frozen_end
                    (id, frozen_case_id, routine_case_id, idempotency_key, ended_at, ended_by_ref)
                VALUES (?, ?, ?, ?, ?, ?)
                """, end.id(), end.frozenCaseId(), end.routineCaseId(), end.idempotencyKey(),
                Timestamp.from(end.endedAt()), end.endedBy());
    }

    public void insertEndSpecimen(UUID endId, UUID frozenSpecimenId, UUID routineSpecimenId, UUID roundId,
            String organizationReference, String actorRef, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.frozen_end_specimen
                    (id, frozen_end_id, frozen_specimen_id, routine_specimen_id, frozen_round_id,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), endId, frozenSpecimenId, routineSpecimenId, roundId,
                organizationReference, Timestamp.from(now), actorRef);
    }

    public Optional<TatPolicy> frozenTatPolicy() {
        return jdbc.query("""
                SELECT warning_threshold, overdue_threshold
                  FROM pis_v2.qc_rule
                 WHERE rule_code = 'FROZEN_TAT' AND active = TRUE
                """, rs -> rs.next() ? Optional.of(new TatPolicy(rs.getBigDecimal("warning_threshold"),
                rs.getBigDecimal("overdue_threshold"))) : Optional.empty());
    }

    /** Reads the hospital's canonical ROUTINE mapping; lightweight test schemas use the safe default. */
    public Optional<String> configuredRoutineBusinessType(String organizationReference) {
        try {
            return jdbc.query("""
                    SELECT cfg.core_business_type_code
                      FROM pis_v2.hospital_business_type_configuration cfg
                      JOIN pis_v2.hospital_profile hp ON hp.id = cfg.hospital_profile_id
                     WHERE hp.profile_code = ? AND cfg.canonical_business_type_code = 'ROUTINE'
                       AND cfg.enabled = TRUE
                    """, rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), organizationReference);
        } catch (DataAccessException ignored) {
            return Optional.empty();
        }
    }

    public Optional<Notification> latestNotification(UUID roundId, String organizationReference) {
        return jdbc.query("""
                SELECT id, status_code, retry_count, last_attempt_at, error_code, error_message
                  FROM pis_v2.integration_message_log
                 WHERE hospital_profile_code = ? AND capability_code = 'CLINICAL_RESULT_NOTIFICATION'
                   AND business_key = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, rs -> rs.next() ? Optional.of(new Notification(rs.getObject("id", UUID.class),
                rs.getString("status_code"), rs.getInt("retry_count"), instant(rs, "last_attempt_at"),
                rs.getString("error_code"), rs.getString("error_message"))) : Optional.empty(), organizationReference,
                "FROZEN_ROUND:" + roundId);
    }

    private String select() {
        return """
                SELECT r.id, r.case_id, r.round_no, r.status_code, r.arrival_time, r.registered_at,
                       r.grossing_start_time, r.slide_completed_time, r.diagnosis_signed_time,
                       r.ended_at, r.ended_by_ref, r.cancelled_at, r.cancelled_by_ref, r.cancellation_reason,
                       r.concurrency_version
                FROM pis_v2.frozen_round r
                """;
    }

    private FrozenRound toRound(java.sql.ResultSet rs) throws java.sql.SQLException {
        return FrozenRound.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getInt("round_no"), rs.getString("status_code"), rs.getTimestamp("arrival_time").toInstant(),
                rs.getTimestamp("registered_at").toInstant(), instant(rs, "grossing_start_time"),
                instant(rs, "slide_completed_time"), instant(rs, "diagnosis_signed_time"), instant(rs, "ended_at"),
                rs.getString("ended_by_ref"), instant(rs, "cancelled_at"), rs.getString("cancelled_by_ref"),
                rs.getString("cancellation_reason"), rs.getLong("concurrency_version"));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record Production(int totalRequired, int completedRequired) {
        public boolean complete() { return totalRequired > 0 && totalRequired == completedRequired; }
    }

    public record TatPolicy(BigDecimal warningHours, BigDecimal overdueHours) { }

    public record Notification(UUID messageLogId, String statusCode, int retryCount, Instant lastAttemptAt,
            String errorCode, String errorMessage) { }

    public record FrozenEnd(UUID id, UUID frozenCaseId, UUID routineCaseId, String idempotencyKey, Instant endedAt,
            String endedBy) { }
}

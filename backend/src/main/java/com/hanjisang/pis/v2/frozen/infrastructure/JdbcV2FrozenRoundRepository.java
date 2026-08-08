package com.hanjisang.pis.v2.frozen.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
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
                     slide_completed_time, diagnosis_signed_time, ended_at, ended_by_ref, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, round.id(), round.caseId(), round.roundNo(), round.status(), Timestamp.from(round.arrivalTime()),
                Timestamp.from(round.registeredAt()), timestamp(round.grossingStartTime()),
                timestamp(round.slideCompletedTime()), timestamp(round.diagnosisSignedTime()), timestamp(round.endedAt()),
                round.endedBy(), round.version(), organizationReference, Timestamp.from(now), actorRef);
    }

    public boolean update(FrozenRound round, String organizationReference, long expectedVersion) {
        return jdbc.update("""
                UPDATE pis_v2.frozen_round
                   SET status_code = ?, grossing_start_time = ?, slide_completed_time = ?,
                       diagnosis_signed_time = ?, ended_at = ?, ended_by_ref = ?, concurrency_version = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                """, round.status(), timestamp(round.grossingStartTime()), timestamp(round.slideCompletedTime()),
                timestamp(round.diagnosisSignedTime()), timestamp(round.endedAt()), round.endedBy(), round.version(),
                round.id(), organizationReference, expectedVersion) == 1;
    }

    public boolean hasSpecimen(UUID roundId, UUID specimenId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.frozen_round_specimen
                WHERE frozen_round_id = ? AND specimen_id = ?
                """, Integer.class, roundId, specimenId) > 0;
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

    public Production production(UUID roundId) {
        return jdbc.query("""
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS completed_count
                FROM pis_v2.slide
                WHERE source_context_type = 'FROZEN_ROUND' AND source_context_id = ? AND deleted_at IS NULL
                  AND required = TRUE
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

    private String select() {
        return """
                SELECT r.id, r.case_id, r.round_no, r.status_code, r.arrival_time, r.registered_at,
                       r.grossing_start_time, r.slide_completed_time, r.diagnosis_signed_time,
                       r.ended_at, r.ended_by_ref, r.concurrency_version
                FROM pis_v2.frozen_round r
                """;
    }

    private FrozenRound toRound(java.sql.ResultSet rs) throws java.sql.SQLException {
        return FrozenRound.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getInt("round_no"), rs.getString("status_code"), rs.getTimestamp("arrival_time").toInstant(),
                rs.getTimestamp("registered_at").toInstant(), instant(rs, "grossing_start_time"),
                instant(rs, "slide_completed_time"), instant(rs, "diagnosis_signed_time"), instant(rs, "ended_at"),
                rs.getString("ended_by_ref"), rs.getLong("concurrency_version"));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record Production(int totalRequired, int completedRequired) {
        public boolean complete() { return totalRequired > 0 && totalRequired == completedRequired; }
    }

    public record FrozenEnd(UUID id, UUID frozenCaseId, UUID routineCaseId, String idempotencyKey, Instant endedAt,
            String endedBy) { }
}

package com.hanjisang.pis.v2.material.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2MaterialReworkRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2MaterialReworkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<ReworkRow> findById(UUID id, String organizationReference) {
        return jdbc.query("""
                SELECT id, case_id, original_slide_id, rework_type_code, reason, status_code,
                       requested_at, requested_by_ref, replacement_slide_id, completed_at,
                       completed_by_ref, concurrency_version, idempotency_key
                  FROM pis_v2.material_rework
                 WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), id, organizationReference);
    }

    public Optional<ReworkRow> findByIdempotency(String idempotencyKey, String organizationReference) {
        return jdbc.query("""
                SELECT id, case_id, original_slide_id, rework_type_code, reason, status_code,
                       requested_at, requested_by_ref, replacement_slide_id, completed_at,
                       completed_by_ref, concurrency_version, idempotency_key
                  FROM pis_v2.material_rework
                 WHERE idempotency_key = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), idempotencyKey,
                organizationReference);
    }

    public List<ReworkRow> findByCase(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT id, case_id, original_slide_id, rework_type_code, reason, status_code,
                       requested_at, requested_by_ref, replacement_slide_id, completed_at,
                       completed_by_ref, concurrency_version, idempotency_key
                  FROM pis_v2.material_rework
                 WHERE case_id = ? AND organization_reference = ?
                 ORDER BY requested_at DESC, id DESC
                """, (rs, rowNum) -> row(rs), caseId, organizationReference);
    }

    public boolean insert(ReworkRow row, String organizationReference) {
        try {
            return jdbc.update("""
                    INSERT INTO pis_v2.material_rework
                        (id, case_id, original_slide_id, rework_type_code, reason, status_code,
                         requested_at, requested_by_ref, organization_reference, concurrency_version,
                         idempotency_key)
                    VALUES (?, ?, ?, ?, ?, 'REQUESTED', ?, ?, ?, 0, ?)
                    """, row.id(), row.caseId(), row.originalSlideId(), row.reworkTypeCode(), row.reason(),
                    Timestamp.from(row.requestedAt()), row.requestedByRef(), organizationReference,
                    row.idempotencyKey()) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<ReworkRow> complete(UUID id, UUID replacementSlideId, String organizationReference,
            String actorRef, Instant completedAt, long expectedVersion) {
        int changed = jdbc.update("""
                UPDATE pis_v2.material_rework
                   SET status_code = 'COMPLETED', replacement_slide_id = ?, completed_at = ?,
                       completed_by_ref = ?, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND status_code = 'REQUESTED'
                   AND concurrency_version = ?
                """, replacementSlideId, Timestamp.from(completedAt), actorRef, id, organizationReference,
                expectedVersion);
        return changed == 1 ? findById(id, organizationReference) : Optional.empty();
    }

    public void resolveOpenProcessExceptions(UUID originalSlideId, String organizationReference,
            String actorRef, Instant completedAt, String note) {
        jdbc.update("""
                UPDATE pis_v2.material_process_fact
                   SET exception_resolved_at = ?, exception_resolved_by_ref = ?, exception_resolution_note = ?,
                       updated_at = ?, concurrency_version = concurrency_version + 1
                 WHERE slide_id = ? AND organization_reference = ? AND exception_code IS NOT NULL
                   AND exception_resolved_at IS NULL
                """, Timestamp.from(completedAt), actorRef, note, Timestamp.from(completedAt), originalSlideId,
                organizationReference);
    }

    private static ReworkRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp requested = rs.getTimestamp("requested_at");
        Timestamp completed = rs.getTimestamp("completed_at");
        return new ReworkRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("original_slide_id", UUID.class), rs.getString("rework_type_code"),
                rs.getString("reason"), rs.getString("status_code"), requested == null ? null : requested.toInstant(),
                rs.getString("requested_by_ref"), rs.getObject("replacement_slide_id", UUID.class),
                completed == null ? null : completed.toInstant(), rs.getString("completed_by_ref"),
                rs.getLong("concurrency_version"), rs.getString("idempotency_key"));
    }

    public record ReworkRow(UUID id, UUID caseId, UUID originalSlideId, String reworkTypeCode, String reason,
            String statusCode, Instant requestedAt, String requestedByRef, UUID replacementSlideId,
            Instant completedAt, String completedByRef, long concurrencyVersion, String idempotencyKey) { }
}

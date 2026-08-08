package com.hanjisang.pis.v2.molecular.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.molecular.domain.MolecularResult;

@Repository
public class JdbcV2MolecularResultRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2MolecularResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(MolecularResult result, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.molecular_result
                    (id, case_id, specimen_id, result_code, result_data, status_code, completed_at,
                     completed_by_ref, concurrency_version, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?, ?)
                """, result.id(), result.caseId(), result.specimenId(), result.resultCode(), result.resultData(),
                result.statusCode(), Timestamp.from(result.completedAt()), result.completedBy(),
                result.concurrencyVersion(), organizationReference, Timestamp.from(now), actorRef);
    }

    public Optional<MolecularResult> find(UUID resultId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, specimen_id, result_code, CAST(result_data AS VARCHAR) AS result_data,
                       status_code, completed_at, completed_by_ref, concurrency_version
                FROM pis_v2.molecular_result
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new MolecularResult(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getObject("specimen_id", UUID.class),
                rs.getString("result_code"), rs.getString("result_data"), rs.getString("status_code"),
                rs.getTimestamp("completed_at").toInstant(), rs.getString("completed_by_ref"),
                rs.getLong("concurrency_version"))) : Optional.empty(), resultId, organizationReference);
    }

    public boolean hasCompletedResult(UUID caseId, String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.molecular_result
                WHERE case_id = ? AND organization_reference = ? AND status_code = 'COMPLETED'
                """, Integer.class, caseId, organizationReference);
        return count != null && count > 0;
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String key) {
        return jdbcTemplate.query("""
                SELECT payload_digest, result_id
                FROM pis_v2.molecular_result_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getString("payload_digest"),
                rs.getObject("result_id", UUID.class))) : Optional.empty(), operationCode, key);
    }

    public boolean insertIdempotency(String operationCode, String key, String digest, UUID resultId,
            String actorRef, Instant now) {
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.molecular_result_idempotency
                    (id, operation_code, idempotency_key, payload_digest, result_id, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), operationCode, key, digest, resultId, Timestamp.from(now), actorRef) == 1;
    }

    public record IdempotencyResult(String payloadDigest, UUID resultId) { }
}

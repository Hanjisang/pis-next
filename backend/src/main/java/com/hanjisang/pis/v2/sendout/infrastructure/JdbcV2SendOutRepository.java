package com.hanjisang.pis.v2.sendout.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.sendout.domain.SendOut;

@Repository
public class JdbcV2SendOutRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2SendOutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(SendOut sendOut, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.send_out
                    (id, case_id, external_reference, destination_name, status_code, requested_at, requested_by_ref,
                     result_data, result_received_at, result_received_by_ref, organization_reference, created_at,
                     created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?)
                """, sendOut.id(), sendOut.caseId(), sendOut.externalReference(), sendOut.destinationName(),
                sendOut.statusCode(), Timestamp.from(sendOut.requestedAt()), sendOut.requestedBy(), sendOut.resultData(),
                timestamp(sendOut.resultReceivedAt()), sendOut.resultReceivedBy(), organizationReference,
                Timestamp.from(now), actorRef);
    }

    public Optional<SendOut> find(UUID id, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, external_reference, destination_name, status_code, requested_at,
                       requested_by_ref, CAST(result_data AS VARCHAR) AS result_data, result_received_at,
                       result_received_by_ref
                FROM pis_v2.send_out WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new SendOut(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("external_reference"),
                rs.getString("destination_name"), rs.getString("status_code"),
                rs.getTimestamp("requested_at").toInstant(), rs.getString("requested_by_ref"),
                rs.getString("result_data"), instant(rs, "result_received_at"), rs.getString("result_received_by_ref")))
                : Optional.empty(), id, organizationReference);
    }

    public boolean updateResult(SendOut sendOut, String organizationReference) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.send_out
                   SET status_code = ?, result_data = CAST(? AS JSONB), result_received_at = ?,
                       result_received_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND status_code = 'REQUESTED'
                """, sendOut.statusCode(), sendOut.resultData(), Timestamp.from(sendOut.resultReceivedAt()),
                sendOut.resultReceivedBy(), sendOut.id(), organizationReference) == 1;
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String key) {
        return jdbcTemplate.query("""
                SELECT payload_digest, send_out_id FROM pis_v2.send_out_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getString(1),
                rs.getObject(2, UUID.class))) : Optional.empty(), operationCode, key);
    }

    public boolean insertIdempotency(String operationCode, String key, String digest, UUID sendOutId,
            String actorRef, Instant now) {
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.send_out_idempotency
                    (id, operation_code, idempotency_key, payload_digest, send_out_id, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), operationCode, key, digest, sendOutId, Timestamp.from(now), actorRef) == 1;
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record IdempotencyResult(String payloadDigest, UUID sendOutId) { }
}

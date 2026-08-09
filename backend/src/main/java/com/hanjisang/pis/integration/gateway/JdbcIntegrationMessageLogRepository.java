package com.hanjisang.pis.integration.gateway;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter.AdapterResult;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope.Direction;
import com.hanjisang.pis.integration.gateway.IntegrationMessageLog.Status;

@Repository
public class JdbcIntegrationMessageLogRepository implements IntegrationMessageLogStore {

    private final JdbcTemplate jdbc;

    public JdbcIntegrationMessageLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IntegrationMessageLog> findByMessageIdentity(IntegrationEnvelope envelope) {
        return queryOne("""
                SELECT * FROM pis_v2.integration_message_log
                WHERE hospital_profile_code = ? AND source_system_code = ?
                  AND target_system_code = ? AND message_id = ?
                """, envelope.hospitalProfileCode(), envelope.sourceSystemCode(), envelope.targetSystemCode(),
                envelope.messageId());
    }

    @Override
    public Optional<IntegrationMessageLog> findById(UUID id) {
        return queryOne("SELECT * FROM pis_v2.integration_message_log WHERE id = ?", id);
    }

    @Override
    public IntegrationMessageLog createPending(IntegrationEnvelope envelope, int maxRetries, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.integration_message_log
                    (id, hospital_profile_code, direction_code, source_system_code, target_system_code,
                     message_id, capability_code, business_key, request_reference, request_digest,
                     status_code, retry_count, max_retries, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                """, id, envelope.hospitalProfileCode(), envelope.direction().name(), envelope.sourceSystemCode(),
                envelope.targetSystemCode(), envelope.messageId(), envelope.capability().name(),
                envelope.businessKey(), envelope.requestReference(), envelope.requestDigest(), maxRetries,
                Timestamp.from(now), Timestamp.from(now));
        return findById(id).orElseThrow();
    }

    @Override
    public IntegrationMessageLog recordAttempt(IntegrationMessageLog current, String adapterCode,
            AdapterResult result, Instant startedAt, Instant completedAt) {
        int attemptNo = current.retryCount() + 1;
        Status status = result.succeeded() ? Status.SUCCEEDED
                : result.retryable() && attemptNo < current.maxRetries() ? Status.RETRY_PENDING : Status.DEAD_LETTER;
        Instant nextRetryAt = status == Status.RETRY_PENDING
                ? completedAt.plus(Duration.ofMinutes(Math.min(60, 1L << Math.min(attemptNo, 6)))) : null;
        String response = truncate(result.responseSummary());
        String errorCode = truncate(result.errorCode());
        String errorMessage = truncate(result.errorMessage());
        jdbc.update("""
                INSERT INTO pis_v2.integration_attempt
                    (id, message_log_id, attempt_no, adapter_code, started_at, completed_at, result_code,
                     response_summary, error_code, error_message, retryable)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), current.id(), attemptNo, adapterCode, Timestamp.from(startedAt),
                Timestamp.from(completedAt), result.succeeded() ? "SUCCEEDED" : "FAILED", response, errorCode,
                errorMessage, result.retryable());
        jdbc.update("""
                UPDATE pis_v2.integration_message_log
                   SET status_code = ?, response_summary = ?, error_code = ?, error_message = ?,
                       retry_count = ?, next_retry_at = ?, last_attempt_at = ?, updated_at = ?
                 WHERE id = ?
                """, status.name(), response, errorCode, errorMessage, attemptNo,
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt), Timestamp.from(completedAt),
                Timestamp.from(completedAt), current.id());
        if (status == Status.DEAD_LETTER) {
            jdbc.update("""
                    INSERT INTO pis_v2.integration_dead_letter (id, message_log_id, reason, created_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (message_log_id) DO NOTHING
                    """, UUID.randomUUID(), current.id(), errorMessage == null ? "外部接口不可用" : errorMessage,
                    Timestamp.from(completedAt));
        }
        return findById(current.id()).orElseThrow();
    }

    @Override
    public void requestReplay(UUID messageLogId, String requestedByRef, String reason, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.integration_replay_request
                    (id, message_log_id, requested_by_ref, reason, requested_at, replay_status_code)
                VALUES (?, ?, ?, ?, ?, 'REQUESTED')
                """, UUID.randomUUID(), messageLogId, requestedByRef, reason, Timestamp.from(now));
    }

    private Optional<IntegrationMessageLog> queryOne(String sql, Object... arguments) {
        return jdbc.query(sql, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), arguments);
    }

    private static IntegrationMessageLog map(ResultSet rs) throws SQLException {
        IntegrationEnvelope envelope = new IntegrationEnvelope(rs.getString("hospital_profile_code"),
                Direction.valueOf(rs.getString("direction_code")), rs.getString("source_system_code"),
                rs.getString("target_system_code"), rs.getString("message_id"),
                IntegrationCapability.valueOf(rs.getString("capability_code")), rs.getString("business_key"),
                rs.getString("request_reference"), rs.getString("request_digest"),
                rs.getTimestamp("created_at").toInstant());
        return new IntegrationMessageLog(rs.getObject("id", UUID.class), envelope,
                Status.valueOf(rs.getString("status_code")), rs.getString("response_summary"),
                rs.getString("error_code"), rs.getString("error_message"), rs.getInt("retry_count"),
                rs.getInt("max_retries"), instant(rs, "next_retry_at"), instant(rs, "last_attempt_at"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}

package com.hanjisang.pis.integration;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxRepository implements OutboxPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(String eventTypeCode, UUID subjectId, String subjectKindCode, long aggregateVersion,
            String correlationId, String payloadDigest, String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis.outbox_event
                (id, event_identity, event_type_code, subject_id, subject_kind_code, aggregate_version,
                 correlation_id, payload_digest, publish_state_code, occurred_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID().toString(), eventTypeCode, subjectId, subjectKindCode,
                aggregateVersion, correlationId, payloadDigest, Timestamp.from(Instant.now()), actorRef);
    }
}

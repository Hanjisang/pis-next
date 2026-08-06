package com.hanjisang.pis.security;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(String operationCode, String permissionCode, ActorContext actor, String outcome,
            String processingOutcome, UUID targetObjectId, String targetKind, String correlationId, String reason) {
        jdbcTemplate.update("""
                INSERT INTO pis.audit_event
                (id, operation_code, permission_code, actor_ref, subject_type_code, target_object_id,
                 target_object_kind_code, authorization_outcome, processing_outcome, correlation_id, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ALLOWED', ?, ?, ?, ?)
                """, UUID.randomUUID(), operationCode, permissionCode, actor.actorId(), actor.subjectTypeCode(),
                targetObjectId, targetKind, processingOutcome, correlationId, reason, Timestamp.from(Instant.now()));
    }

    public void appendDenied(String operationCode, String permissionCode, ActorContext actor, String correlationId,
            String reason) {
        jdbcTemplate.update("""
                INSERT INTO pis.audit_event
                (id, operation_code, permission_code, actor_ref, subject_type_code, authorization_outcome,
                 processing_outcome, correlation_id, reason, created_at)
                VALUES (?, ?, ?, ?, ?, 'DENIED', 'REJECTED', ?, ?, ?)
                """, UUID.randomUUID(), operationCode, permissionCode, actor.actorId(), actor.subjectTypeCode(),
                correlationId, reason, Timestamp.from(Instant.now()));
    }
}

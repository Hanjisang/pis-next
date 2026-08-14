package com.hanjisang.pis.security;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuditEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcAuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(String operationCode, String permissionCode, ActorContext actor, String outcome,
            String processingOutcome, UUID targetObjectId, String targetKind, String correlationId, String reason) {
        appendInternal(operationCode, permissionCode, actor, processingOutcome, targetObjectId, targetKind,
                correlationId, reason, List.of());
    }

    public void appendWithChanges(String operationCode, String permissionCode, ActorContext actor,
            String processingOutcome, UUID targetObjectId, String targetKind, String correlationId, String reason,
            List<AuditChange> changes) {
        appendInternal(operationCode, permissionCode, actor, processingOutcome, targetObjectId, targetKind,
                correlationId, reason, changes == null ? List.of() : changes);
    }

    private void appendInternal(String operationCode, String permissionCode, ActorContext actor,
            String processingOutcome, UUID targetObjectId, String targetKind, String correlationId, String reason,
            List<AuditChange> changes) {
        jdbcTemplate.update("""
                INSERT INTO pis.audit_event
                (id, operation_code, permission_code, actor_ref, subject_type_code, target_object_id,
                 target_object_kind_code, authorization_outcome, processing_outcome, correlation_id, reason,
                 category_code, changes_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ALLOWED', ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operationCode, permissionCode, actor.actorId(), actor.subjectTypeCode(),
                targetObjectId, targetKind, processingOutcome, correlationId, reason, category(operationCode),
                serialize(changes), Timestamp.from(Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendDenied(String operationCode, String permissionCode, ActorContext actor, String correlationId,
            String reason) {
        jdbcTemplate.update("""
                INSERT INTO pis.audit_event
                (id, operation_code, permission_code, actor_ref, subject_type_code, authorization_outcome,
                 processing_outcome, correlation_id, reason, category_code, changes_json, created_at)
                VALUES (?, ?, ?, ?, ?, 'DENIED', 'REJECTED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operationCode, permissionCode, actor.actorId(), actor.subjectTypeCode(),
                correlationId, reason, category(operationCode), null, Timestamp.from(Instant.now()));
    }

    private String serialize(List<AuditChange> changes) {
        if (changes == null || changes.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计变更记录序列化失败", exception);
        }
    }

    private static String category(String operationCode) {
        if (operationCode == null) return "SYSTEM";
        String code = operationCode.toUpperCase();
        if (code.contains("CASE") || code.contains("SPECIMEN") || code.contains("REGISTRATION")
                || code.contains("APPLICATION") || code.contains("INBOUND")) return "REGISTRATION";
        if (code.contains("ARCHIVE") || code.contains("LOAN") || code.contains("CUSTODY")
                || code.contains("DESTRUCTION")) return "ARCHIVE";
        if (code.contains("GROSS") || code.contains("BLOCK") || code.contains("SLIDE") || code.contains("PRINT")
                || code.contains("MATERIAL")) return "MATERIAL";
        if (code.contains("TECHNICAL") || code.contains("HISTOLOGY")) return "TECHNICAL";
        if (code.contains("DIAGNOSIS") || code.contains("RESPONSIBILITY")) return "DIAGNOSIS";
        if (code.contains("REPORT") || code.contains("SIGN") || code.contains("WITHDRAW")
                || code.contains("SUPPLEMENT")) return "REPORT";
        if (code.contains("HIS") || code.contains("LIS") || code.contains("EMR")
                || code.contains("INTEGRATION")) return "INTEGRATION";
        return "SYSTEM";
    }

    public record AuditChange(String fieldCode, String fieldLabel, String beforeValue, String afterValue) { }
}

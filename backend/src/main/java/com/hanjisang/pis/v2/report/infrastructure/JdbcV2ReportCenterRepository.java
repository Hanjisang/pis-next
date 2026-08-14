package com.hanjisang.pis.v2.report.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2ReportCenterRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2ReportCenterRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<QueueRow> find(String organizationReference) {
        List<QueueRow> rows = jdbc.query("""
                SELECT d.id AS id, d.case_id, c.case_no, COALESCE(s.patient_reference, '未填写') AS patient_reference,
                       bt.business_type_code, 'WAITING_SIGN' AS queue_code, NULL AS report_no,
                       NULL AS status_code, d.updated_at AS occurred_at, NULL AS report_id,
                       c.created_at AS tat_started_at, p.id AS policy_id, p.configuration_version AS policy_version,
                       p.warning_minutes, p.target_minutes,
                       dd.id AS delay_id, dd.reason_code AS delay_reason_code,
                       dd.reason_detail AS delay_reason_detail, dd.expected_sign_at, dd.declared_at AS delay_declared_at
                FROM pis_v2.diagnosis d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id
                    AND s.snapshot_version_no = (SELECT MAX(s2.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot s2 WHERE s2.case_id = c.id)
                LEFT JOIN pis_v2.report_tat_policy p ON p.business_type_id = c.business_type_id
                    AND p.organization_reference = c.organization_reference AND p.enabled = TRUE
                LEFT JOIN pis_v2.report_delay_declaration dd ON dd.diagnosis_id = d.id AND dd.resolved_at IS NULL
                JOIN pis_v2.responsibility_unit ru ON ru.diagnosis_id = d.id
                    AND ru.role_code = 'AUDIT' AND ru.completed_at IS NOT NULL AND ru.ended_at IS NULL
                WHERE c.organization_reference = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM pis_v2.report r
                      WHERE r.diagnosis_id = d.id AND r.report_nature_code = 'ORIGINAL'
                        AND r.status_code = 'EFFECTIVE'
                  )
                ORDER BY d.updated_at DESC, d.id DESC
                LIMIT 100
                """, (rs, rowNum) -> row(rs), organizationReference);
        rows.addAll(jdbc.query("""
                SELECT r.diagnosis_id AS id, r.case_id, c.case_no,
                       COALESCE(s.patient_reference, '未填写') AS patient_reference,
                       bt.business_type_code,
                       CASE WHEN r.status_code = 'WITHDRAWN' THEN 'WITHDRAWN'
                            WHEN r.report_nature_code = 'SUPPLEMENTAL' THEN 'SUPPLEMENTAL'
                            ELSE 'SIGNED' END AS queue_code,
                       r.report_no, r.status_code, r.signed_at AS occurred_at, r.id AS report_id,
                       c.created_at AS tat_started_at, p.id AS policy_id, p.configuration_version AS policy_version,
                       p.warning_minutes, p.target_minutes,
                       dd.id AS delay_id, dd.reason_code AS delay_reason_code,
                       dd.reason_detail AS delay_reason_detail, dd.expected_sign_at, dd.declared_at AS delay_declared_at
                FROM pis_v2.report r
                JOIN pis_v2.pathology_case c ON c.id = r.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id
                    AND s.snapshot_version_no = (SELECT MAX(s2.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot s2 WHERE s2.case_id = c.id)
                LEFT JOIN pis_v2.report_tat_policy p ON p.business_type_id = c.business_type_id
                    AND p.organization_reference = c.organization_reference AND p.enabled = TRUE
                LEFT JOIN pis_v2.report_delay_declaration dd ON dd.diagnosis_id = r.diagnosis_id
                    AND dd.resolved_at IS NULL
                WHERE r.organization_reference = ?
                ORDER BY r.signed_at DESC, r.id DESC
                LIMIT 200
                """, (rs, rowNum) -> row(rs), organizationReference));
        return rows;
    }

    public Optional<TatContext> findTatContextForUpdate(UUID diagnosisId, String organizationReference) {
        List<UUID> locked = jdbc.query("""
                SELECT d.id FROM pis_v2.diagnosis d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE d.id = ? AND c.organization_reference = ?
                FOR UPDATE
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), diagnosisId, organizationReference);
        if (locked.isEmpty()) return Optional.empty();
        return jdbc.query("""
                SELECT d.id AS diagnosis_id, d.case_id, c.created_at AS tat_started_at,
                       bt.business_type_code, p.id AS policy_id, p.configuration_version AS policy_version,
                       p.target_minutes,
                       EXISTS (SELECT 1 FROM pis_v2.responsibility_unit ru WHERE ru.diagnosis_id = d.id
                           AND ru.role_code = 'AUDIT' AND ru.completed_at IS NOT NULL
                           AND ru.ended_at IS NULL) AS ready_for_sign,
                       EXISTS (SELECT 1 FROM pis_v2.report r WHERE r.diagnosis_id = d.id
                           AND r.report_nature_code = 'ORIGINAL' AND r.status_code = 'EFFECTIVE') AS signed
                FROM pis_v2.diagnosis d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.report_tat_policy p ON p.business_type_id = c.business_type_id
                    AND p.organization_reference = c.organization_reference AND p.enabled = TRUE
                WHERE d.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new TatContext(rs.getObject("diagnosis_id", UUID.class),
                        rs.getObject("case_id", UUID.class), instant(rs, "tat_started_at"),
                        rs.getString("business_type_code"), rs.getObject("policy_id", UUID.class),
                        (Integer) rs.getObject("policy_version"), (Integer) rs.getObject("target_minutes"),
                        rs.getBoolean("ready_for_sign"), rs.getBoolean("signed"))) : Optional.empty(), diagnosisId,
                organizationReference);
    }

    public Optional<DelayRow> findDelayByIdempotency(String organizationReference, String idempotencyKey) {
        return findDelay("WHERE dd.organization_reference = ? AND dd.idempotency_key = ?", organizationReference,
                idempotencyKey);
    }

    public Optional<DelayRow> findActiveDelay(UUID diagnosisId, String organizationReference) {
        return findDelay("WHERE dd.diagnosis_id = ? AND dd.organization_reference = ? AND dd.resolved_at IS NULL",
                diagnosisId, organizationReference);
    }

    public Optional<DelayRow> findDelayForUpdate(UUID delayId, String organizationReference) {
        return findDelay("WHERE dd.id = ? AND dd.organization_reference = ? FOR UPDATE", delayId,
                organizationReference);
    }

    private Optional<DelayRow> findDelay(String where, Object... parameters) {
        return jdbc.query("""
                SELECT dd.id, dd.diagnosis_id, d.case_id, dd.policy_id, dd.policy_version, dd.tat_due_at,
                       dd.reason_code, dd.reason_detail, dd.expected_sign_at, dd.declared_at, dd.declared_by_ref,
                       dd.idempotency_key, dd.resolved_at, dd.resolved_by_ref, dd.resolution_note,
                       dd.resolution_idempotency_key, dd.concurrency_version
                FROM pis_v2.report_delay_declaration dd
                JOIN pis_v2.diagnosis d ON d.id = dd.diagnosis_id
                """ + where, rs -> rs.next() ? Optional.of(delay(rs)) : Optional.empty(), parameters);
    }

    public void insertDelay(UUID id, String organizationReference, TatContext context, Instant dueAt,
            String reasonCode, String reasonDetail, Instant expectedSignAt, String idempotencyKey,
            Instant now, String actorRef) {
        jdbc.update("""
                INSERT INTO pis_v2.report_delay_declaration
                    (id, organization_reference, diagnosis_id, policy_id, policy_version,
                     tat_due_at, reason_code, reason_detail, expected_sign_at, declared_at, declared_by_ref,
                     idempotency_key, concurrency_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, organizationReference, context.diagnosisId(), context.policyId(),
                context.policyVersion(), Timestamp.from(dueAt), reasonCode, reasonDetail,
                Timestamp.from(expectedSignAt), Timestamp.from(now), actorRef, idempotencyKey);
    }

    public boolean resolveDelay(UUID delayId, String organizationReference, long expectedVersion,
            String resolutionNote, String resolutionIdempotencyKey, Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.report_delay_declaration
                   SET resolved_at = ?, resolved_by_ref = ?, resolution_note = ?,
                       resolution_idempotency_key = ?, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND resolved_at IS NULL
                   AND concurrency_version = ?
                """, Timestamp.from(now), actorRef, resolutionNote, resolutionIdempotencyKey,
                delayId, organizationReference, expectedVersion) == 1;
    }

    public int resolveActiveDelayAfterSignOut(UUID diagnosisId, String organizationReference,
            Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.report_delay_declaration
                   SET resolved_at = ?, resolved_by_ref = ?, resolution_note = '报告签发自动关闭',
                       concurrency_version = concurrency_version + 1
                 WHERE diagnosis_id = ? AND organization_reference = ? AND resolved_at IS NULL
                """, Timestamp.from(now), actorRef, diagnosisId, organizationReference);
    }

    private static QueueRow row(ResultSet rs) throws SQLException {
        return new QueueRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("case_no"), rs.getString("patient_reference"), rs.getString("business_type_code"),
                rs.getString("queue_code"), rs.getString("report_no"), rs.getString("status_code"),
                instant(rs, "occurred_at"), rs.getObject("report_id", UUID.class), instant(rs, "tat_started_at"),
                rs.getObject("policy_id", UUID.class), (Integer) rs.getObject("policy_version"),
                (Integer) rs.getObject("warning_minutes"), (Integer) rs.getObject("target_minutes"),
                rs.getObject("delay_id", UUID.class), rs.getString("delay_reason_code"),
                rs.getString("delay_reason_detail"), instant(rs, "expected_sign_at"),
                instant(rs, "delay_declared_at"));
    }

    private static DelayRow delay(ResultSet rs) throws SQLException {
        return new DelayRow(rs.getObject("id", UUID.class), rs.getObject("diagnosis_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getObject("policy_id", UUID.class),
                rs.getInt("policy_version"), instant(rs, "tat_due_at"), rs.getString("reason_code"),
                rs.getString("reason_detail"), instant(rs, "expected_sign_at"), instant(rs, "declared_at"),
                rs.getString("declared_by_ref"), rs.getString("idempotency_key"), instant(rs, "resolved_at"),
                rs.getString("resolved_by_ref"), rs.getString("resolution_note"),
                rs.getString("resolution_idempotency_key"), rs.getLong("concurrency_version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record QueueRow(UUID diagnosisId, UUID caseId, String pathologyNo, String patientReference,
            String businessTypeCode, String queueCode, String reportNo, String statusCode, Instant occurredAt,
            UUID reportId, Instant tatStartedAt, UUID policyId, Integer policyVersion, Integer warningMinutes,
            Integer targetMinutes, UUID delayId, String delayReasonCode, String delayReasonDetail,
            Instant expectedSignAt, Instant delayDeclaredAt) { }

    public record TatContext(UUID diagnosisId, UUID caseId, Instant tatStartedAt, String businessTypeCode,
            UUID policyId, Integer policyVersion, Integer targetMinutes, boolean readyForSign, boolean signed) { }

    public record DelayRow(UUID id, UUID diagnosisId, UUID caseId, UUID policyId, int policyVersion,
            Instant tatDueAt, String reasonCode, String reasonDetail, Instant expectedSignAt, Instant declaredAt,
            String declaredByRef, String idempotencyKey, Instant resolvedAt, String resolvedByRef,
            String resolutionNote, String resolutionIdempotencyKey, long concurrencyVersion) { }
}

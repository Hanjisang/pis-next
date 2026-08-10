package com.hanjisang.pis.v2.report.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
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
                       'WAITING_SIGN' AS queue_code, NULL::varchar AS report_no, NULL::varchar AS status_code,
                       d.updated_at AS occurred_at, NULL::uuid AS report_id
                FROM pis_v2.diagnosis d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                LEFT JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id
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
                SELECT r.diagnosis_id AS id, r.case_id, c.case_no, COALESCE(s.patient_reference, '未填写') AS patient_reference,
                       CASE WHEN r.status_code = 'WITHDRAWN' THEN 'WITHDRAWN'
                            WHEN r.report_nature_code = 'SUPPLEMENTAL' THEN 'SUPPLEMENTAL'
                            ELSE 'SIGNED' END AS queue_code,
                       r.report_no, r.status_code, r.signed_at AS occurred_at, r.id AS report_id
                FROM pis_v2.report r
                JOIN pis_v2.pathology_case c ON c.id = r.case_id
                LEFT JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id
                WHERE r.organization_reference = ?
                ORDER BY r.signed_at DESC, r.id DESC
                LIMIT 200
                """, (rs, rowNum) -> row(rs), organizationReference));
        return rows;
    }

    private static QueueRow row(ResultSet rs) throws SQLException {
        return new QueueRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("case_no"), rs.getString("patient_reference"), rs.getString("queue_code"),
                rs.getString("report_no"), rs.getString("status_code"), instant(rs, "occurred_at"),
                rs.getObject("report_id", UUID.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record QueueRow(UUID diagnosisId, UUID caseId, String pathologyNo, String patientReference,
            String queueCode, String reportNo, String statusCode, Instant occurredAt, UUID reportId) { }
}

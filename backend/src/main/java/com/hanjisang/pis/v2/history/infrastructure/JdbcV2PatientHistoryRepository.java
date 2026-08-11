package com.hanjisang.pis.v2.history.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2PatientHistoryRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2PatientHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PatientHistoryRow> find(String organizationReference, String patientReference) {
        return find(organizationReference, patientReference, null);
    }

    public List<PatientHistoryRow> find(String organizationReference, String patientReference, UUID currentCaseId) {
        String sql = """
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name, c.created_at,
                       d.diagnosis_text, d.microscopic_description,
                       r.id AS report_id, r.report_no, r.status_code AS report_status,
                       r.signed_at, ds.id AS digital_slide_id, ds.slide_id AS physical_slide_id
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.case_context_snapshot ctx
                  ON ctx.case_id = c.id
                 AND ctx.snapshot_version_no = (
                     SELECT MAX(ctx2.snapshot_version_no)
                     FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                LEFT JOIN pis_v2.diagnosis d
                  ON d.id = (
                     SELECT d2.id FROM pis_v2.diagnosis d2
                     WHERE d2.case_id = c.id AND d2.context_type = 'CASE'
                     ORDER BY d2.updated_at DESC, d2.id DESC LIMIT 1)
                LEFT JOIN pis_v2.report r
                  ON r.id = (
                     SELECT r2.id FROM pis_v2.report r2
                     WHERE r2.case_id = c.id
                     ORDER BY r2.signed_at DESC, r2.created_at DESC, r2.id DESC LIMIT 1)
                LEFT JOIN pis_v2.digital_slide ds
                  ON ds.id = (
                     SELECT ds2.id FROM pis_v2.digital_slide ds2
                     WHERE ds2.case_id = c.id AND ds2.status_code = 'ACTIVE'
                     ORDER BY ds2.updated_at DESC, ds2.id DESC LIMIT 1)
                WHERE c.organization_reference = ?
                  AND c.lifecycle_state_code = 'ACTIVE'
                  AND ctx.patient_reference = ?
                """;
        if (currentCaseId != null) {
            sql += " AND c.id <> ?\n";
        }
        sql += """
                ORDER BY CASE WHEN r.signed_at IS NULL THEN 1 ELSE 0 END,
                         r.signed_at DESC, c.created_at DESC, c.id DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        Object[] parameters = currentCaseId == null
                ? new Object[] { organizationReference, patientReference }
                : new Object[] { organizationReference, patientReference, currentCaseId };
        return jdbc.query(sql, (rs, rowNum) -> new PatientHistoryRow(
                rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("display_name"),
                instant(rs, "created_at"), rs.getString("diagnosis_text"),
                rs.getString("microscopic_description"), rs.getObject("report_id", UUID.class),
                rs.getString("report_no"), rs.getString("report_status"), instant(rs, "signed_at"),
                rs.getObject("digital_slide_id", UUID.class), rs.getObject("physical_slide_id", UUID.class)), parameters);
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record PatientHistoryRow(UUID caseId, String pathologyNo, String businessTypeCode,
            String businessTypeName, Instant occurredAt, String diagnosisText,
            String microscopicDescription, UUID reportId, String reportNo, String reportStatus,
            Instant signedAt, UUID digitalSlideId, UUID physicalSlideId) { }
}

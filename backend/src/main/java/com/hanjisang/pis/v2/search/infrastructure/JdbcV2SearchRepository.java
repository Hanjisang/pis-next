package com.hanjisang.pis.v2.search.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2SearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2SearchRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<SearchRow> search(String query, String organizationReference) {
        String pattern = "%" + query.trim() + "%";
        List<SearchRow> rows = new ArrayList<>();
        rows.addAll(jdbcTemplate.query("""
                SELECT c.id, c.id AS case_id, 'CASE' AS result_kind, c.case_no AS display_code,
                       CONCAT(s.patient_reference, ' ', COALESCE(s.visit_reference, '')) AS summary
                FROM pis_v2.pathology_case c JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id
                WHERE c.organization_reference = ? AND (c.case_no ILIKE ? OR c.external_application_id ILIKE ?
                   OR s.patient_reference ILIKE ? OR COALESCE(s.visit_reference, '') ILIKE ?)
                ORDER BY c.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern, pattern, pattern, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT c.id, c.id AS case_id, 'PATIENT' AS result_kind, ctx.patient_reference AS display_code,
                       CONCAT(COUNT(*) OVER (PARTITION BY ctx.patient_reference), ' 个相关病例') AS summary
                FROM pis_v2.pathology_case c
                JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                 AND ctx.snapshot_version_no = (SELECT MAX(ctx2.snapshot_version_no)
                     FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND ctx.patient_reference ILIKE ?
                ORDER BY c.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT s.id, s.case_id, 'SPECIMEN', s.specimen_no, s.specimen_code
                FROM pis_v2.specimen s WHERE s.organization_reference = ? AND s.deleted_at IS NULL
                  AND (s.specimen_no ILIKE ? OR s.specimen_code ILIKE ? OR s.source_reference ILIKE ?)
                ORDER BY s.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern, pattern, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT b.id, b.case_id, 'BLOCK', b.block_code, b.block_type
                FROM pis_v2.block b WHERE b.organization_reference = ? AND b.deleted_at IS NULL
                  AND (b.block_code ILIKE ? OR b.external_source_reference ILIKE ?)
                ORDER BY b.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT s.id, s.case_id, 'SLIDE', s.slide_code, s.slide_type
                FROM pis_v2.slide s WHERE s.organization_reference = ? AND s.deleted_at IS NULL
                  AND s.slide_code ILIKE ? ORDER BY s.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT o.id, o.case_id, 'TECHNICAL_ORDER', o.order_no, o.status_code
                FROM pis_v2.technical_order o WHERE o.organization_reference = ? AND o.order_no ILIKE ?
                ORDER BY o.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT d.id, d.case_id, 'DIAGNOSIS', CAST(d.id AS VARCHAR), d.diagnosis_text
                FROM pis_v2.diagnosis d JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE c.organization_reference = ? AND (d.diagnosis_text ILIKE ? OR d.microscopic_description ILIKE ?)
                ORDER BY d.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern, pattern));
        rows.addAll(jdbcTemplate.query("""
                SELECT r.id, r.case_id, 'REPORT', r.report_no, r.status_code
                FROM pis_v2.report r WHERE r.organization_reference = ? AND r.report_no ILIKE ?
                ORDER BY r.created_at DESC LIMIT 20
                """, (rs, rowNum) -> row(rs), organizationReference, pattern));
        return rows.stream().limit(100).toList();
    }

    private static SearchRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SearchRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5));
    }

    public record SearchRow(UUID id, UUID caseId, String resultKind, String displayCode, String summary) { }
}

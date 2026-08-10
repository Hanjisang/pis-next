package com.hanjisang.pis.v2.workbench.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2WorkbenchRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2WorkbenchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkbenchRow> findPersonal(String organizationReference, String actorReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       r.role_code AS work_code,
                       CASE r.role_code
                           WHEN 'INITIAL' THEN '待初诊'
                           WHEN 'REVIEW' THEN '待复诊'
                           WHEN 'AUDIT' THEN '待审核'
                           ELSE r.role_code
                       END AS work_label,
                       COALESCE(di.display_name, r.doctor_id) AS responsibility_name,
                       r.accepted_at AS occurred_at,
                       c.created_at AS case_created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(ctx2.snapshot_version_no) FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                JOIN pis_v2.diagnosis d ON d.case_id = c.id AND d.organization_reference = c.organization_reference
                JOIN pis_v2.responsibility_unit r ON r.diagnosis_id = d.id
                LEFT JOIN pis_v2.doctor_identity di ON CAST(di.id AS VARCHAR) = r.doctor_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND r.doctor_id = ? AND r.completed_at IS NULL AND r.ended_at IS NULL
                ORDER BY r.accepted_at, c.created_at, c.id
                """, (rs, rowNum) -> row(rs), organizationReference, actorReference);
    }

    public List<WorkbenchRow> findTechnicalAttention(String organizationReference, String actorReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       'TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION' AS work_code,
                       '技术结果待处理' AS work_label,
                       COALESCE(di.display_name, current_r.doctor_id) AS responsibility_name,
                       MAX(r.entered_at) AS occurred_at,
                       c.created_at AS case_created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(ctx2.snapshot_version_no) FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                JOIN pis_v2.technical_order o ON o.case_id = c.id AND o.organization_reference = c.organization_reference
                JOIN pis_v2.technical_order_item i ON i.order_id = o.id
                JOIN pis_v2.technical_order_item_result r ON r.item_id = i.id
                LEFT JOIN pis_v2.diagnosis d ON d.case_id = c.id AND d.organization_reference = c.organization_reference
                JOIN pis_v2.responsibility_unit current_r
                  ON current_r.diagnosis_id = d.id
                 AND current_r.doctor_id = ?
                 AND current_r.completed_at IS NULL
                 AND current_r.ended_at IS NULL
                LEFT JOIN pis_v2.doctor_identity di ON CAST(di.id AS VARCHAR) = current_r.doctor_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND o.status_code <> 'CANCELLED'
                  AND NOT EXISTS (
                      SELECT 1 FROM pis.audit_event acknowledged
                      WHERE acknowledged.operation_code = 'PIS-V2-PX02B-TECHNICAL-RESULT-ACK'
                        AND acknowledged.target_object_id = i.id
                        AND acknowledged.created_at >= r.entered_at
                  )
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name, ctx.patient_reference,
                         di.display_name, current_r.doctor_id, c.created_at
                ORDER BY MAX(r.entered_at) DESC, c.id
                """, (rs, rowNum) -> row(rs), actorReference, organizationReference);
    }

    public List<WorkbenchRow> findWithdrawnReports(String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       'WITHDRAWN_REPORT_REQUIRES_ATTENTION' AS work_code,
                       '撤回报告待处理' AS work_label,
                       COALESCE(di.display_name, r.withdrawn_by_ref) AS responsibility_name,
                       MAX(r.withdrawn_at) AS occurred_at,
                       c.created_at AS case_created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(ctx2.snapshot_version_no) FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                JOIN pis_v2.report r ON r.case_id = c.id
                LEFT JOIN pis_v2.doctor_identity di ON CAST(di.id AS VARCHAR) = r.withdrawn_by_ref
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND r.status_code = 'WITHDRAWN'
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name, ctx.patient_reference,
                         di.display_name, r.withdrawn_by_ref, c.created_at
                ORDER BY MAX(r.withdrawn_at) DESC, c.id
                """, (rs, rowNum) -> row(rs), organizationReference);
    }

    public List<WorkbenchRow> findPublicPool(String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       'PUBLIC_POOL' AS work_code,
                       '公共病例池' AS work_label,
                       NULL AS responsibility_name,
                       c.created_at AS occurred_at,
                       c.created_at AS case_created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(ctx2.snapshot_version_no) FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND (
                      (bt.business_type_code = 'MOLECULAR' AND EXISTS (
                          SELECT 1 FROM pis_v2.molecular_result mr
                          WHERE mr.case_id = c.id AND mr.organization_reference = ? AND mr.status_code = 'COMPLETED'
                      ))
                      OR
                      (bt.business_type_code NOT IN ('MOLECULAR', 'FROZEN')
                       AND EXISTS (
                          SELECT 1 FROM pis_v2.slide s
                          WHERE s.case_id = c.id AND s.required = TRUE AND s.completed_at IS NOT NULL
                            AND s.deleted_at IS NULL
                       )
                       AND NOT EXISTS (
                          SELECT 1 FROM pis_v2.slide s
                          WHERE s.case_id = c.id AND s.required = TRUE AND s.completed_at IS NULL
                            AND s.deleted_at IS NULL
                       ))
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM pis_v2.diagnosis d
                      JOIN pis_v2.responsibility_unit r ON r.diagnosis_id = d.id
                      WHERE d.case_id = c.id AND d.organization_reference = ? AND r.role_code = 'INITIAL'
                  )
                ORDER BY c.created_at, c.id
                """, (rs, rowNum) -> row(rs), organizationReference, organizationReference, organizationReference);
    }

    public QueueCounts findQueueCounts(String organizationReference) {
        int histology = count("""
                SELECT COUNT(*) FROM pis_v2.slide s JOIN pis_v2.pathology_case c ON c.id = s.case_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND s.deleted_at IS NULL AND s.completed_at IS NULL
                """, organizationReference);
        int dehydration = phaseCount(organizationReference, "DEHYDRATION");
        int embedding = phaseCount(organizationReference, "EMBEDDING");
        int cutting = phaseCount(organizationReference, "SECTIONING");
        int staining = phaseCount(organizationReference, "STAINING");
        int coverslipping = phaseCount(organizationReference, "MOUNTING");
        int technical = count("""
                SELECT COUNT(*) FROM pis_v2.technical_order o
                WHERE o.organization_reference = ? AND o.status_code NOT IN ('COMPLETED', 'CANCELLED')
                """, organizationReference);
        int frozen = count("""
                SELECT COUNT(*) FROM pis_v2.pathology_case c JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE' AND bt.business_type_code = 'FROZEN'
                """, organizationReference);
        int withdrawn = count("""
                SELECT COUNT(DISTINCT r.case_id) FROM pis_v2.report r
                WHERE r.organization_reference = ? AND r.status_code = 'WITHDRAWN'
                """, organizationReference);
        return new QueueCounts(histology, dehydration, embedding, cutting, staining, coverslipping,
                technical, frozen, withdrawn);
    }

    private int phaseCount(String organizationReference, String phaseCode) {
        return count("""
                SELECT COUNT(*) FROM pis_v2.slide s JOIN pis_v2.pathology_case c ON c.id = s.case_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE' AND s.deleted_at IS NULL
                  AND EXISTS (SELECT 1 FROM pis_v2.material_process_fact f
                              WHERE f.slide_id = s.id AND f.phase_code = ? AND f.completed_at IS NULL)
                """, organizationReference, phaseCode);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static WorkbenchRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp occurred = rs.getTimestamp("occurred_at");
        return new WorkbenchRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getString("work_code"), rs.getString("work_label"),
                rs.getString("responsibility_name"), occurred == null ? null : occurred.toInstant(),
                rs.getTimestamp("case_created_at").toInstant());
    }

    public record WorkbenchRow(UUID caseId, String pathologyNo, String businessTypeCode,
            String businessTypeName, String patientReference, String workCode, String workLabel,
            String responsibilityName, Instant occurredAt, Instant caseCreatedAt) { }

    public record QueueCounts(int histology, int dehydration, int embedding, int cutting, int staining,
            int coverslipping, int technical, int frozen, int withdrawn) { }
}

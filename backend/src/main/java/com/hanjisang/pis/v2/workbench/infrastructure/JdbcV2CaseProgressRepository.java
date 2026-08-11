package com.hanjisang.pis.v2.workbench.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Query-only projection of case facts for cross-role tracking. */
@Repository
public class JdbcV2CaseProgressRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2CaseProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProgressRow find(UUID caseId, String organizationReference) {
        List<ProgressRow> rows = query(organizationReference, null, caseId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ProgressRow> findRegistered(String organizationReference, String actorReference) {
        return query(organizationReference, actorReference, null);
    }

    private List<ProgressRow> query(String organizationReference, String actorReference, UUID caseId) {
        String ownerFilter = actorReference == null ? "" : " AND c.created_by_ref = ? ";
        String caseFilter = caseId == null ? "" : " AND c.id = ? ";
        String sql = """
                WITH material AS (
                    SELECT c.id,
                           (SELECT COUNT(*) FROM pis_v2.specimen s
                            WHERE s.case_id = c.id AND s.deleted_at IS NULL) AS specimen_count,
                           (SELECT COUNT(*) FROM pis_v2.slide s
                            WHERE s.case_id = c.id AND s.required = TRUE AND s.deleted_at IS NULL) AS required_slide_count,
                           (SELECT COUNT(*) FROM pis_v2.slide s
                            WHERE s.case_id = c.id AND s.required = TRUE AND s.completed_at IS NOT NULL
                              AND s.deleted_at IS NULL) AS completed_slide_count,
                           (SELECT COUNT(*) FROM pis_v2.specimen specimen
                            WHERE specimen.case_id = c.id AND specimen.deleted_at IS NULL
                              AND EXISTS (
                                  SELECT 1 FROM pis_v2.slide direct_slide
                                  WHERE direct_slide.case_id = c.id
                                    AND direct_slide.specimen_id = specimen.id
                                    AND direct_slide.source_context_type = 'CYTOLOGY'
                                    AND direct_slide.required = TRUE
                                    AND direct_slide.completed_at IS NOT NULL
                                    AND direct_slide.deleted_at IS NULL
                              )) AS completed_direct_specimen_count,
                           COALESCE(
                               (SELECT MAX(s.created_at) FROM pis_v2.slide s
                                WHERE s.case_id = c.id AND s.deleted_at IS NULL),
                               (SELECT MAX(s.created_at) FROM pis_v2.specimen s
                                WHERE s.case_id = c.id AND s.deleted_at IS NULL),
                               c.created_at) AS material_at
                    FROM pis_v2.pathology_case c
                    WHERE c.organization_reference = ?
                ), frozen_summary AS (
                    SELECT fr.case_id,
                           COUNT(DISTINCT frs.specimen_id) AS specimen_count,
                           COUNT(DISTINCT CASE WHEN sl.required THEN sl.id END) AS required_slide_count,
                           COUNT(DISTINCT CASE WHEN sl.required AND sl.completed_at IS NOT NULL THEN sl.id END)
                               AS completed_slide_count
                    FROM pis_v2.frozen_round fr
                    LEFT JOIN pis_v2.frozen_round_specimen frs ON frs.frozen_round_id = fr.id
                    LEFT JOIN pis_v2.slide sl ON sl.case_id = fr.case_id
                        AND sl.source_context_type = 'FROZEN_ROUND'
                        AND sl.source_context_id = fr.id
                        AND sl.deleted_at IS NULL
                    WHERE fr.organization_reference = ? AND fr.status_code = 'OPEN'
                    GROUP BY fr.case_id
                ), report_summary AS (
                    SELECT r.case_id,
                           CASE WHEN MAX(CASE WHEN r.status_code = 'EFFECTIVE' THEN 1 ELSE 0 END) = 1
                                THEN 'EFFECTIVE'
                                WHEN MAX(CASE WHEN r.status_code = 'WITHDRAWN' THEN 1 ELSE 0 END) = 1
                                THEN 'WITHDRAWN' ELSE 'NOT_STARTED' END AS report_status,
                           MAX(r.signed_at) AS signed_at
                    FROM pis_v2.report r
                    WHERE r.organization_reference = ?
                    GROUP BY r.case_id
                )
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name, bt.modality_code,
                       c.lifecycle_state_code, c.created_at,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       m.specimen_count, m.required_slide_count, m.completed_slide_count,
                       m.completed_direct_specimen_count, m.material_at,
                       current_r.role_code AS responsibility_role,
                       current_r.responsibility_name,
                       current_r.accepted_at AS responsibility_entered_at,
                       COALESCE(report_summary.report_status, 'NOT_STARTED') AS report_status,
                       report_summary.signed_at,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM pis_v2.molecular_result mr
                           WHERE mr.case_id = c.id AND mr.organization_reference = ? AND mr.status_code = 'COMPLETED'
                       ) THEN TRUE ELSE FALSE END AS molecular_complete,
                       CASE
                           WHEN c.lifecycle_state_code <> 'ACTIVE' THEN 'CANCELLED'
                           WHEN bt.modality_code = 'CYTOLOGY' AND m.specimen_count = 0 THEN 'WAITING_SPECIMEN'
                           WHEN bt.modality_code = 'CYTOLOGY'
                                AND m.completed_direct_specimen_count < m.specimen_count THEN 'CYTOLOGY_PREPARATION'
                           WHEN bt.modality_code = 'FROZEN' AND COALESCE(frozen_summary.specimen_count, 0) = 0
                                THEN 'WAITING_SPECIMEN'
                           WHEN bt.modality_code = 'FROZEN' AND COALESCE(frozen_summary.required_slide_count, 0) = 0
                                THEN 'WAITING_GROSSING'
                           WHEN bt.modality_code = 'FROZEN'
                                AND COALESCE(frozen_summary.completed_slide_count, 0)
                                    < COALESCE(frozen_summary.required_slide_count, 0)
                                THEN 'FROZEN_PRODUCTION'
                           WHEN report_summary.report_status = 'EFFECTIVE' THEN 'SIGNED'
                           WHEN current_r.role_code = 'INITIAL' THEN 'INITIAL_DIAGNOSIS'
                           WHEN current_r.role_code = 'REVIEW' THEN 'REVIEW_DIAGNOSIS'
                           WHEN current_r.role_code = 'AUDIT' THEN 'AUDIT'
                           WHEN bt.modality_code = 'TISSUE' AND m.required_slide_count = 0 THEN 'WAITING_GROSSING'
                           WHEN bt.modality_code = 'TISSUE'
                                AND m.completed_slide_count < m.required_slide_count THEN 'HISTOLOGY_PREPARATION'
                           WHEN bt.modality_code = 'MOLECULAR'
                                AND NOT EXISTS (
                                    SELECT 1 FROM pis_v2.molecular_result mr
                                    WHERE mr.case_id = c.id AND mr.organization_reference = ?
                                      AND mr.status_code = 'COMPLETED'
                                ) THEN 'WAITING_TECHNICAL_RESULT'
                           ELSE 'WAITING_DIAGNOSIS'
                       END AS current_stage_code,
                       CASE
                           WHEN c.lifecycle_state_code <> 'ACTIVE' THEN '已取消'
                           WHEN bt.modality_code = 'CYTOLOGY' AND m.specimen_count = 0 THEN '待标本接收'
                           WHEN bt.modality_code = 'CYTOLOGY'
                                AND m.completed_direct_specimen_count < m.specimen_count THEN '待细胞制片'
                           WHEN bt.modality_code = 'FROZEN' AND COALESCE(frozen_summary.specimen_count, 0) = 0
                                THEN '待冰冻标本'
                           WHEN bt.modality_code = 'FROZEN' AND COALESCE(frozen_summary.required_slide_count, 0) = 0
                                THEN '待冰冻取材'
                           WHEN bt.modality_code = 'FROZEN'
                                AND COALESCE(frozen_summary.completed_slide_count, 0)
                                    < COALESCE(frozen_summary.required_slide_count, 0)
                                THEN '冰冻制片中'
                           WHEN report_summary.report_status = 'EFFECTIVE' THEN '已签发'
                           WHEN current_r.role_code = 'INITIAL' THEN '待初诊'
                           WHEN current_r.role_code = 'REVIEW' THEN '待复诊'
                           WHEN current_r.role_code = 'AUDIT' THEN '待审核'
                           WHEN bt.modality_code = 'TISSUE' AND m.required_slide_count = 0 THEN '待取材'
                           WHEN bt.modality_code = 'TISSUE'
                                AND m.completed_slide_count < m.required_slide_count THEN '组织制片中'
                           WHEN bt.modality_code = 'MOLECULAR'
                                AND NOT EXISTS (
                                    SELECT 1 FROM pis_v2.molecular_result mr
                                    WHERE mr.case_id = c.id AND mr.organization_reference = ?
                                      AND mr.status_code = 'COMPLETED'
                                ) THEN '待技术结果'
                           ELSE '待诊断'
                       END AS current_stage_label
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN material m ON m.id = c.id
                LEFT JOIN pis_v2.case_context_snapshot ctx
                  ON ctx.case_id = c.id
                 AND ctx.snapshot_version_no = (
                     SELECT MAX(ctx2.snapshot_version_no)
                     FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                LEFT JOIN report_summary ON report_summary.case_id = c.id
                LEFT JOIN frozen_summary ON frozen_summary.case_id = c.id
                LEFT JOIN LATERAL (
                    SELECT r.role_code, COALESCE(di.display_name, r.doctor_id) AS responsibility_name,
                           r.accepted_at
                    FROM pis_v2.diagnosis d
                    JOIN pis_v2.responsibility_unit r ON r.diagnosis_id = d.id
                    LEFT JOIN pis_v2.doctor_identity di ON CAST(di.id AS VARCHAR) = r.doctor_id
                    WHERE d.case_id = c.id AND d.organization_reference = ?
                      AND r.completed_at IS NULL AND r.ended_at IS NULL
                    ORDER BY r.sequence_no, r.accepted_at, r.id
                    FETCH FIRST 1 ROW ONLY
                ) current_r ON TRUE
                WHERE c.organization_reference = ?
                """ + ownerFilter + caseFilter + " ORDER BY c.created_at DESC, c.id DESC";

        Object[] arguments;
        if (actorReference == null && caseId == null) {
            arguments = new Object[] { organizationReference, organizationReference, organizationReference,
                    organizationReference, organizationReference, organizationReference, organizationReference,
                    organizationReference };
        } else if (actorReference != null && caseId == null) {
            arguments = new Object[] { organizationReference, organizationReference, organizationReference,
                    organizationReference, organizationReference, organizationReference, organizationReference,
                    organizationReference, actorReference };
        } else if (actorReference == null) {
            arguments = new Object[] { organizationReference, organizationReference, organizationReference,
                    organizationReference, organizationReference, organizationReference, organizationReference,
                    organizationReference, caseId };
        } else {
            arguments = new Object[] { organizationReference, organizationReference, organizationReference,
                    organizationReference, organizationReference, organizationReference, organizationReference,
                    organizationReference, actorReference, caseId };
        }
        return jdbc.query(sql, (rs, rowNum) -> row(rs), arguments);
    }

    private static ProgressRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp materialAt = rs.getTimestamp("material_at");
        Timestamp responsibilityAt = rs.getTimestamp("responsibility_entered_at");
        Timestamp signedAt = rs.getTimestamp("signed_at");
        return new ProgressRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("display_name"), rs.getString("modality_code"),
                rs.getString("lifecycle_state_code"), rs.getString("patient_reference"), created.toInstant(),
                rs.getInt("specimen_count"), rs.getInt("required_slide_count"), rs.getInt("completed_slide_count"),
                rs.getInt("completed_direct_specimen_count"), materialAt == null ? null : materialAt.toInstant(),
                rs.getString("responsibility_role"), rs.getString("responsibility_name"),
                responsibilityAt == null ? null : responsibilityAt.toInstant(), rs.getString("report_status"),
                signedAt == null ? null : signedAt.toInstant(), rs.getBoolean("molecular_complete"),
                rs.getString("current_stage_code"), rs.getString("current_stage_label"));
    }

    public record ProgressRow(UUID caseId, String pathologyNo, String businessTypeCode, String businessTypeName,
            String modalityCode, String lifecycle, String patientReference, Instant createdAt, int specimenCount,
            int requiredSlideCount, int completedSlideCount, int completedDirectSpecimenCount, Instant materialAt,
            String responsibilityRole, String responsibilityName, Instant responsibilityEnteredAt,
            String reportStatus, Instant signedAt, boolean molecularComplete, String currentStageCode,
            String currentStageLabel) { }
}

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

    public List<WorkbenchRow> findWithdrawnReports(String organizationReference, String actorReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       'WITHDRAWN_REPORT_REQUIRES_ATTENTION' AS work_code,
                       '撤回报告待处理' AS work_label,
                       COALESCE(di.display_name, MAX(r.withdrawn_by_ref)) AS responsibility_name,
                       MAX(r.withdrawn_at) AS occurred_at,
                       c.created_at AS case_created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(ctx2.snapshot_version_no) FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                JOIN pis_v2.report r ON r.case_id = c.id
                JOIN pis_v2.diagnosis d ON d.case_id = c.id AND d.organization_reference = c.organization_reference
                JOIN pis_v2.responsibility_unit current_r ON current_r.diagnosis_id = d.id
                    AND current_r.doctor_id = ? AND current_r.completed_at IS NULL AND current_r.ended_at IS NULL
                LEFT JOIN pis_v2.doctor_identity di ON CAST(di.id AS VARCHAR) = current_r.doctor_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND r.status_code = 'WITHDRAWN'
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name, ctx.patient_reference,
                         di.display_name, current_r.doctor_id, c.created_at
                ORDER BY MAX(r.withdrawn_at) DESC, c.id
                """, (rs, rowNum) -> row(rs), actorReference, organizationReference);
    }

    /** Frozen diagnosis is a round projection, not a second diagnosis task entity. */
    public List<FrozenDiagnosisRow> findFrozenDiagnosis(String organizationReference, String actorReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       fr.id AS round_id, fr.round_no, fr.arrival_time,
                       CASE WHEN d.id IS NULL THEN 'UNASSIGNED' ELSE 'ASSIGNED' END AS assignment_state
                FROM pis_v2.frozen_round fr
                JOIN pis_v2.pathology_case c ON c.id = fr.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                  AND ctx.snapshot_version_no = (SELECT MAX(ctx2.snapshot_version_no)
                      FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                LEFT JOIN pis_v2.diagnosis d ON d.context_type = 'FROZEN_ROUND'
                  AND d.context_id = fr.id AND d.organization_reference = c.organization_reference
                LEFT JOIN pis_v2.responsibility_unit r ON r.diagnosis_id = d.id
                  AND r.role_code = 'INITIAL' AND r.doctor_id = ?
                  AND r.completed_at IS NULL AND r.ended_at IS NULL
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'FROZEN'
                  AND fr.status_code IN ('OPEN', 'PRODUCTION_COMPLETE')
                  AND EXISTS (SELECT 1
                              FROM pis_v2.frozen_round_specimen frs
                              JOIN pis_v2.specimen sp ON sp.id = frs.specimen_id AND sp.deleted_at IS NULL
                              WHERE frs.frozen_round_id = fr.id)
                  AND NOT EXISTS (SELECT 1
                                  FROM pis_v2.frozen_round_specimen pending_frs
                                  JOIN pis_v2.specimen pending_sp ON pending_sp.id = pending_frs.specimen_id
                                      AND pending_sp.deleted_at IS NULL
                                  WHERE pending_frs.frozen_round_id = fr.id
                                    AND (SELECT COUNT(*)
                                           FROM pis_v2.slide pending
                                          WHERE pending.case_id = c.id
                                            AND (pending.specimen_id = pending_frs.specimen_id
                                             OR EXISTS (SELECT 1 FROM pis_v2.block pending_block
                                                        WHERE pending_block.id = pending.block_id
                                                          AND pending_block.specimen_id = pending_frs.specimen_id
                                                          AND pending_block.deleted_at IS NULL))
                                            AND pending.source_context_type = 'FROZEN_ROUND'
                                            AND pending.source_context_id = fr.id
                                            AND pending.required = TRUE
                                            AND pending.completed_at IS NOT NULL
                                            AND pending.deleted_at IS NULL)
                                      < COALESCE((SELECT SUM(sr.copies)
                                                    FROM pis_v2.slide_rule sr
                                                   WHERE sr.organization_reference = c.organization_reference
                                                     AND sr.business_type_id = c.business_type_id
                                                     AND sr.source_context_type = 'FROZEN_ROUND'
                                                     AND sr.trigger_code = 'ON_GROSSING_COMPLETE'
                                                     AND sr.active = TRUE), 1))
                  AND (d.id IS NULL OR r.id IS NOT NULL)
                ORDER BY fr.arrival_time ASC, fr.id
                """, (rs, rowNum) -> new FrozenDiagnosisRow(rs.getObject("id", UUID.class),
                rs.getString("case_no"), rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getObject("round_id", UUID.class), rs.getInt("round_no"),
                rs.getTimestamp("arrival_time").toInstant(), rs.getString("assignment_state")), actorReference,
                organizationReference);
    }

    public List<GrossingRow> findPendingGrossing(String organizationReference, boolean frozen) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       COUNT(DISTINCT s.id) AS specimen_count,
                       COALESCE(string_agg(DISTINCT NULLIF(s.description, ''), '；'), '标本信息待补充') AS specimen_summary,
                       c.created_at AS entered_at,
                       pa.application_department,
                       fr.id AS round_id, fr.round_no, fr.arrival_time
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.specimen s ON s.case_id = c.id AND s.deleted_at IS NULL
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(latest.snapshot_version_no) FROM pis_v2.case_context_snapshot latest
                    WHERE latest.case_id = c.id)
                LEFT JOIN pis_v2.pathology_application_case pac ON pac.case_id = c.id
                LEFT JOIN pis_v2.pathology_application pa ON pa.id = pac.application_id
                LEFT JOIN pis_v2.frozen_round fr ON fr.case_id = c.id AND fr.status_code = 'OPEN'
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND ((? = TRUE AND bt.modality_code = 'FROZEN' AND fr.id IS NOT NULL)
                    OR (? = FALSE AND bt.modality_code = 'TISSUE'))
                  AND NOT EXISTS (
                    SELECT 1 FROM pis_v2.grossing g
                    WHERE g.case_id = c.id AND g.deleted_at IS NULL
                      AND ((? = TRUE AND g.source_type = 'FROZEN_CONTEXT' AND g.source_reference_id = fr.id)
                        OR (? = FALSE AND g.source_type = 'INITIAL'))
                      AND g.completed_at IS NOT NULL)
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name, ctx.patient_reference,
                         c.created_at, pa.application_department, fr.id, fr.round_no, fr.arrival_time
                ORDER BY COALESCE(fr.arrival_time, c.created_at), c.id
                """, (rs, rowNum) -> new GrossingRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("patient_reference"), rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getInt("specimen_count"), rs.getString("specimen_summary"),
                rs.getTimestamp("entered_at").toInstant(), rs.getString("application_department"),
                rs.getObject("round_id", UUID.class), rs.getObject("round_no", Integer.class),
                rs.getTimestamp("arrival_time") == null ? null : rs.getTimestamp("arrival_time").toInstant()),
                organizationReference, frozen, frozen, frozen, frozen);
    }

    public List<GrossingRow> findGrossedToday(String organizationReference, String actorReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       COUNT(DISTINCT gs.specimen_id) AS specimen_count,
                       COALESCE(string_agg(DISTINCT NULLIF(s.description, ''), '；'), '标本信息待补充') AS specimen_summary,
                       g.completed_at AS entered_at, NULL AS application_department,
                       NULL AS round_id, NULL AS round_no, NULL AS arrival_time
                FROM pis_v2.grossing g
                JOIN pis_v2.pathology_case c ON c.id = g.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.grossing_specimen gs ON gs.grossing_id = g.id AND gs.deleted_at IS NULL
                LEFT JOIN pis_v2.specimen s ON s.id = gs.specimen_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(latest.snapshot_version_no) FROM pis_v2.case_context_snapshot latest
                    WHERE latest.case_id = c.id)
                WHERE g.organization_reference = ? AND g.completed_by_ref = ?
                  AND g.completed_at >= CURRENT_DATE AND g.deleted_at IS NULL
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name, ctx.patient_reference,
                         g.id, g.completed_at
                ORDER BY g.completed_at DESC, g.id
                """, (rs, rowNum) -> new GrossingRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("patient_reference"), rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getInt("specimen_count"), rs.getString("specimen_summary"),
                rs.getTimestamp("entered_at").toInstant(), null, null, null, null),
                organizationReference, actorReference);
    }

    public List<RegisteredRow> findRegisteredToday(String organizationReference, String actorReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       c.created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(latest.snapshot_version_no) FROM pis_v2.case_context_snapshot latest
                    WHERE latest.case_id = c.id)
                WHERE c.organization_reference = ? AND c.created_by_ref = ?
                  AND c.created_at >= CURRENT_DATE
                ORDER BY c.created_at DESC, c.id
                """, (rs, rowNum) -> new RegisteredRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("patient_reference"), rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant()), organizationReference, actorReference);
    }

    public List<WorkbenchRow> findCytologyPreparation(String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       'CYTOLOGY_PREPARATION' AS work_code,
                       '待细胞制片' AS work_label,
                       '制片人员' AS responsibility_name,
                       COALESCE(MAX(s.created_at), c.created_at) AS occurred_at,
                       c.created_at AS case_created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.specimen s ON s.case_id = c.id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id AND ctx.snapshot_version_no = (
                    SELECT MAX(ctx2.snapshot_version_no) FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'CYTOLOGY' AND s.deleted_at IS NULL
                  AND EXISTS (
                      SELECT 1 FROM pis_v2.specimen candidate
                      WHERE candidate.case_id = c.id AND candidate.deleted_at IS NULL
                        AND NOT EXISTS (
                            SELECT 1 FROM pis_v2.slide direct_slide
                            WHERE direct_slide.case_id = c.id
                              AND direct_slide.specimen_id = candidate.id
                              AND direct_slide.source_context_type = 'CYTOLOGY'
                              AND direct_slide.required = TRUE
                              AND direct_slide.completed_at IS NOT NULL
                              AND direct_slide.deleted_at IS NULL
                        )
                  )
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name,
                         ctx.patient_reference, c.created_at
                ORDER BY occurred_at, c.id
                """, (rs, rowNum) -> row(rs), organizationReference);
    }

    public List<WorkbenchRow> findPublicPool(String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       'PUBLIC_POOL' AS work_code,
                       '待接诊' AS work_label,
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
                      (bt.modality_code NOT IN ('MOLECULAR', 'FROZEN')
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
                SELECT COUNT(*) FROM pis_v2.slide s
                JOIN pis_v2.pathology_case c ON c.id = s.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'TISSUE' AND s.block_id IS NOT NULL
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
                SELECT COUNT(*) FROM pis_v2.frozen_round fr
                JOIN pis_v2.pathology_case c ON c.id = fr.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'FROZEN' AND fr.status_code IN ('OPEN', 'PRODUCTION_COMPLETE')
                  AND EXISTS (SELECT 1
                              FROM pis_v2.frozen_round_specimen frs
                              JOIN pis_v2.specimen sp ON sp.id = frs.specimen_id AND sp.deleted_at IS NULL
                              WHERE frs.frozen_round_id = fr.id)
                  AND EXISTS (SELECT 1
                              FROM pis_v2.frozen_round_specimen pending_frs
                              JOIN pis_v2.specimen pending_sp ON pending_sp.id = pending_frs.specimen_id
                                  AND pending_sp.deleted_at IS NULL
                              WHERE pending_frs.frozen_round_id = fr.id
                                AND (SELECT COUNT(*)
                                       FROM pis_v2.slide pending
                                      WHERE pending.case_id = c.id
                                        AND (pending.specimen_id = pending_frs.specimen_id
                                         OR EXISTS (SELECT 1 FROM pis_v2.block pending_block
                                                    WHERE pending_block.id = pending.block_id
                                                      AND pending_block.specimen_id = pending_frs.specimen_id
                                                      AND pending_block.deleted_at IS NULL))
                                        AND pending.source_context_type = 'FROZEN_ROUND'
                                        AND pending.source_context_id = fr.id
                                        AND pending.required = TRUE
                                        AND pending.completed_at IS NOT NULL
                                        AND pending.deleted_at IS NULL)
                                  < COALESCE((SELECT SUM(sr.copies)
                                                FROM pis_v2.slide_rule sr
                                               WHERE sr.organization_reference = c.organization_reference
                                                 AND sr.business_type_id = c.business_type_id
                                                 AND sr.source_context_type = 'FROZEN_ROUND'
                                                 AND sr.trigger_code = 'ON_GROSSING_COMPLETE'
                                                 AND sr.active = TRUE), 1))
                """, organizationReference);
        int withdrawn = count("""
                SELECT COUNT(DISTINCT r.case_id) FROM pis_v2.report r
                WHERE r.organization_reference = ? AND r.status_code = 'WITHDRAWN'
                """, organizationReference);
        int cytologyPreparation = count("""
                SELECT COUNT(*) FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'CYTOLOGY'
                  AND EXISTS (
                      SELECT 1 FROM pis_v2.specimen specimen
                      WHERE specimen.case_id = c.id AND specimen.deleted_at IS NULL
                        AND NOT EXISTS (
                            SELECT 1 FROM pis_v2.slide direct_slide
                            WHERE direct_slide.case_id = c.id
                              AND direct_slide.specimen_id = specimen.id
                              AND direct_slide.source_context_type = 'CYTOLOGY'
                              AND direct_slide.required = TRUE
                              AND direct_slide.completed_at IS NOT NULL
                              AND direct_slide.deleted_at IS NULL
                        )
                  )
                """, organizationReference);
        return new QueueCounts(histology, dehydration, embedding, cutting, staining, coverslipping,
                technical, frozen, withdrawn, cytologyPreparation);
    }

    private int phaseCount(String organizationReference, String phaseCode) {
        return count("""
                SELECT COUNT(*) FROM pis_v2.slide s
                JOIN pis_v2.pathology_case c ON c.id = s.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'TISSUE' AND s.block_id IS NOT NULL AND s.deleted_at IS NULL
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

    public record FrozenDiagnosisRow(UUID caseId, String pathologyNo, String businessTypeCode,
            String businessTypeName, String patientReference, UUID roundId, int roundNo, Instant arrivalTime,
            String assignmentState) { }

    public record QueueCounts(int histology, int dehydration, int embedding, int cutting, int staining,
            int coverslipping, int technical, int frozen, int withdrawn, int cytologyPreparation) { }

    public record GrossingRow(UUID caseId, String pathologyNo, String patientReference, String businessTypeCode,
            String businessTypeName, int specimenCount, String specimenSummary, Instant enteredAt,
            String sourceDepartment, UUID roundId, Integer roundNo, Instant roundStartedAt) { }

    public record RegisteredRow(UUID caseId, String pathologyNo, String patientReference, String businessTypeCode,
            String businessTypeName, Instant registeredAt) { }
}

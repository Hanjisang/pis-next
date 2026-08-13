package com.hanjisang.pis.v2.workspace.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository.MaterialTreeRow;

/** Query-only repository for the shared Case Workspace context. */
@Repository
public class JdbcV2CaseWorkspaceRepository {

    private final JdbcTemplate jdbc;
    private final JdbcV2MaterialRepository materialRepository;

    public JdbcV2CaseWorkspaceRepository(JdbcTemplate jdbc, JdbcV2MaterialRepository materialRepository) {
        this.jdbc = jdbc;
        this.materialRepository = materialRepository;
    }

    public Optional<CaseHeaderRow> findCase(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name AS business_type_name,
                       c.lifecycle_state_code, c.application_item_code, c.source_system_code,
                       c.external_application_id, c.created_at,
                       (SELECT snapshot.patient_reference FROM pis_v2.case_context_snapshot snapshot
                        WHERE snapshot.case_id = c.id ORDER BY snapshot.snapshot_version_no DESC LIMIT 1) AS patient_reference,
                       (SELECT snapshot.visit_reference FROM pis_v2.case_context_snapshot snapshot
                        WHERE snapshot.case_id = c.id ORDER BY snapshot.snapshot_version_no DESC LIMIT 1) AS visit_reference
                       ,(SELECT source.case_no FROM pis_v2.pathology_case source
                         WHERE source.id = c.frozen_source_case_id) AS frozen_source_pathology_no
                       ,(SELECT target.case_no FROM pis_v2.pathology_case target
                         WHERE target.frozen_source_case_id = c.id ORDER BY target.created_at LIMIT 1)
                         AS routine_target_pathology_no
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(caseHeader(rs)) : Optional.empty(), caseId,
                organizationReference);
    }

    public List<FrozenRoundRow> findFrozenRounds(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT fr.id, fr.round_no, fr.status_code, fr.arrival_time, fr.diagnosis_signed_time,
                       (SELECT COUNT(*) FROM pis_v2.frozen_round_specimen frs
                        WHERE frs.frozen_round_id = fr.id) AS specimen_count,
                       (SELECT COUNT(*) FROM pis_v2.slide s
                        WHERE s.source_context_type = 'FROZEN_ROUND' AND s.source_context_id = fr.id
                          AND s.deleted_at IS NULL) AS slide_count,
                       (SELECT COUNT(*) FROM pis_v2.slide s
                        WHERE s.source_context_type = 'FROZEN_ROUND' AND s.source_context_id = fr.id
                          AND s.completed_at IS NOT NULL AND s.deleted_at IS NULL) AS completed_slide_count,
                       (SELECT COUNT(*) FROM pis_v2.report r
                        JOIN pis_v2.diagnosis d ON d.id = r.diagnosis_id
                        WHERE d.context_type = 'FROZEN_ROUND' AND d.context_id = fr.id
                          AND r.organization_reference = fr.organization_reference) AS report_count
                FROM pis_v2.frozen_round fr
                WHERE fr.case_id = ? AND fr.organization_reference = ?
                ORDER BY fr.round_no
                """, (rs, rowNum) -> new FrozenRoundRow(rs.getObject("id", UUID.class), rs.getInt("round_no"),
                rs.getString("status_code"), instant(rs, "arrival_time"), instant(rs, "diagnosis_signed_time"),
                rs.getInt("specimen_count"), rs.getInt("slide_count"), rs.getInt("completed_slide_count"),
                rs.getInt("report_count")), caseId, organizationReference);
    }

    public List<MaterialTreeRow> findMaterials(UUID caseId, String organizationReference) {
        return materialRepository.findMaterialTree(caseId, organizationReference);
    }

    public List<GrossingRow> findGrossings(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT id, grossing_no, source_type, gross_description, grossing_doctor_id, recorder_id,
                       started_at, completed_at, completed_by_ref
                FROM pis_v2.grossing
                WHERE case_id = ? AND organization_reference = ? AND deleted_at IS NULL
                ORDER BY started_at, id
                """, (rs, rowNum) -> new GrossingRow(rs.getObject("id", UUID.class), rs.getString("grossing_no"),
                rs.getString("source_type"), rs.getString("gross_description"), rs.getString("grossing_doctor_id"),
                rs.getString("recorder_id"), instant(rs, "started_at"), instant(rs, "completed_at"),
                rs.getString("completed_by_ref")), caseId, organizationReference);
    }

    public List<ResponsibilityRow> findResponsibilities(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT r.id, r.diagnosis_id, r.role_code, r.doctor_id,
                       COALESCE(d.display_name, u.display_name, r.doctor_id) AS doctor_name,
                       r.sequence_no, r.accepted_at, r.completed_at, r.ended_at,
                       r.assignment_source_code, r.assignment_reason
                FROM pis_v2.responsibility_unit r
                JOIN pis_v2.diagnosis diagnosis ON diagnosis.id = r.diagnosis_id
                    AND diagnosis.organization_reference = ?
                LEFT JOIN pis_v2.doctor_identity d ON CAST(d.id AS VARCHAR) = r.doctor_id
                LEFT JOIN pis_v2.auth_user u ON u.id = d.user_id
                WHERE diagnosis.case_id = ?
                ORDER BY diagnosis.context_type, diagnosis.context_id, r.sequence_no, r.created_at
                """, (rs, rowNum) -> new ResponsibilityRow(rs.getObject("id", UUID.class),
                rs.getObject("diagnosis_id", UUID.class), rs.getString("role_code"), rs.getString("doctor_id"),
                rs.getString("doctor_name"), rs.getInt("sequence_no"), instant(rs, "accepted_at"),
                instant(rs, "completed_at"), instant(rs, "ended_at"), rs.getString("assignment_source_code"),
                rs.getString("assignment_reason")), organizationReference, caseId);
    }

    public List<TechnicalOrderRow> findTechnicalOrders(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT o.id, o.order_no, o.status_code, o.required_before_sign_out, o.created_at,
                       o.created_by_ref, COUNT(i.id) AS item_count,
                       COUNT(result.id) AS result_count
                FROM pis_v2.technical_order o
                LEFT JOIN pis_v2.technical_order_item i ON i.order_id = o.id
                LEFT JOIN pis_v2.technical_order_item_result result ON result.item_id = i.id
                WHERE o.case_id = ? AND o.organization_reference = ?
                GROUP BY o.id, o.order_no, o.status_code, o.required_before_sign_out, o.created_at, o.created_by_ref
                ORDER BY o.created_at DESC, o.id
                """, (rs, rowNum) -> new TechnicalOrderRow(rs.getObject("id", UUID.class),
                rs.getString("order_no"), rs.getString("status_code"), rs.getBoolean("required_before_sign_out"),
                instant(rs, "created_at"), rs.getString("created_by_ref"), rs.getInt("item_count"),
                rs.getInt("result_count")), caseId, organizationReference);
    }

    public List<DigitalSlideRow> findDigitalSlides(UUID caseId) {
        return jdbc.query("""
                SELECT id, block_id, slide_id, binding_mode_code, status_code, viewer_reference,
                       source_platform, updated_at
                FROM pis_v2.digital_slide
                WHERE case_id = ?
                ORDER BY updated_at DESC, id
                """, (rs, rowNum) -> new DigitalSlideRow(rs.getObject("id", UUID.class),
                rs.getObject("block_id", UUID.class), rs.getObject("slide_id", UUID.class),
                rs.getString("binding_mode_code"), rs.getString("status_code"), rs.getString("viewer_reference"),
                rs.getString("source_platform"), instant(rs, "updated_at")), caseId);
    }

    public List<ReportRow> findReports(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT id, report_no, report_nature_code, prior_report_id, status_code, signed_by_ref,
                       signed_at, withdrawn_by_ref, withdrawn_at, withdrawal_reason, pdf_file_reference
                FROM pis_v2.report
                WHERE case_id = ? AND organization_reference = ?
                ORDER BY signed_at DESC, id
                """, (rs, rowNum) -> new ReportRow(rs.getObject("id", UUID.class), rs.getString("report_no"),
                rs.getString("report_nature_code"), rs.getObject("prior_report_id", UUID.class),
                rs.getString("status_code"), rs.getString("signed_by_ref"), instant(rs, "signed_at"),
                rs.getString("withdrawn_by_ref"), instant(rs, "withdrawn_at"), rs.getString("withdrawal_reason"),
                rs.getString("pdf_file_reference")), caseId, organizationReference);
    }

    public List<AuditRow> findTimeline(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT ae.id, ae.created_at, ae.actor_ref,
                       COALESCE(doctor.display_name, user_account.display_name, ae.actor_ref) AS actor_name,
                       ae.operation_code, ae.target_object_kind_code, ae.target_object_id, ae.reason,
                       ae.category_code, ae.changes_json,
                       CASE ae.target_object_kind_code
                           WHEN 'V2-CASE' THEN target_case.case_no
                           WHEN 'V2-SPECIMEN' THEN target_specimen.specimen_code
                           WHEN 'V2-GROSSING' THEN target_grossing.grossing_no
                           WHEN 'V2-BLOCK' THEN target_block.block_code
                           WHEN 'V2-SLIDE' THEN target_slide.slide_code
                           WHEN 'V2-DIGITAL-SLIDE' THEN NULL
                           WHEN 'V2-TECHNICAL-ORDER' THEN target_order.order_no
                           WHEN 'V2-TECHNICAL-ORDER-ITEM' THEN target_item_order.order_no
                           WHEN 'V2-REPORT' THEN target_report.report_no
                           WHEN 'V2-FROZEN-ROUND' THEN ('冰冻第 ' || target_round.round_no || ' 轮')
                           ELSE NULL
                       END AS target_display_code
                FROM pis.audit_event ae
                LEFT JOIN pis_v2.doctor_identity doctor ON CAST(doctor.id AS VARCHAR) = ae.actor_ref
                LEFT JOIN pis_v2.auth_user user_account ON CAST(user_account.id AS VARCHAR) = ae.actor_ref
                LEFT JOIN pis_v2.pathology_case target_case
                    ON ae.target_object_kind_code = 'V2-CASE' AND target_case.id = ae.target_object_id
                LEFT JOIN pis_v2.specimen target_specimen
                    ON ae.target_object_kind_code = 'V2-SPECIMEN' AND target_specimen.id = ae.target_object_id
                LEFT JOIN pis_v2.grossing target_grossing
                    ON ae.target_object_kind_code = 'V2-GROSSING' AND target_grossing.id = ae.target_object_id
                LEFT JOIN pis_v2.block target_block
                    ON ae.target_object_kind_code = 'V2-BLOCK' AND target_block.id = ae.target_object_id
                LEFT JOIN pis_v2.slide target_slide
                    ON ae.target_object_kind_code = 'V2-SLIDE' AND target_slide.id = ae.target_object_id
                LEFT JOIN pis_v2.technical_order target_order
                    ON ae.target_object_kind_code = 'V2-TECHNICAL-ORDER' AND target_order.id = ae.target_object_id
                LEFT JOIN pis_v2.technical_order_item target_item
                    ON ae.target_object_kind_code = 'V2-TECHNICAL-ORDER-ITEM' AND target_item.id = ae.target_object_id
                LEFT JOIN pis_v2.technical_order target_item_order ON target_item_order.id = target_item.order_id
                LEFT JOIN pis_v2.report target_report
                    ON ae.target_object_kind_code = 'V2-REPORT' AND target_report.id = ae.target_object_id
                LEFT JOIN pis_v2.frozen_round target_round
                    ON ae.target_object_kind_code = 'V2-FROZEN-ROUND' AND target_round.id = ae.target_object_id
                WHERE ae.authorization_outcome = 'ALLOWED'
                  AND (
                    ae.target_object_id = ?
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.specimen WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.grossing WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.block WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.slide WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.digital_slide WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.diagnosis WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT r.id FROM pis_v2.responsibility_unit r
                        JOIN pis_v2.diagnosis d ON d.id = r.diagnosis_id WHERE d.case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.technical_order WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT i.id FROM pis_v2.technical_order_item i
                        JOIN pis_v2.technical_order o ON o.id = i.order_id WHERE o.case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.report WHERE case_id = ?)
                    OR ae.target_object_id IN (SELECT id FROM pis_v2.frozen_round WHERE case_id = ?)
                  )
                ORDER BY ae.created_at DESC, ae.id DESC
                LIMIT 300
                """, (rs, rowNum) -> new AuditRow(rs.getObject("id", UUID.class), instant(rs, "created_at"),
                rs.getString("actor_ref"), rs.getString("actor_name"), rs.getString("operation_code"),
                rs.getString("target_object_kind_code"), rs.getObject("target_object_id", UUID.class),
                rs.getString("reason"), rs.getString("category_code"), rs.getString("changes_json"),
                rs.getString("target_display_code")), caseId,
                caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId, caseId);
    }

    private static CaseHeaderRow caseHeader(ResultSet rs) throws SQLException {
        return new CaseHeaderRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("business_type_name"),
                rs.getString("lifecycle_state_code"), rs.getString("application_item_code"),
                rs.getString("source_system_code"), rs.getString("external_application_id"),
                rs.getString("patient_reference"), rs.getString("visit_reference"), instant(rs, "created_at"),
                rs.getString("frozen_source_pathology_no"), rs.getString("routine_target_pathology_no"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record CaseHeaderRow(UUID caseId, String pathologyNo, String businessTypeCode, String businessTypeName,
            String lifecycle, String applicationItemCode, String sourceSystemCode, String applicationNo,
            String patientReference, String visitReference, Instant createdAt, String frozenSourcePathologyNo,
            String routineTargetPathologyNo) { }

    public record FrozenRoundRow(UUID roundId, int roundNo, String statusCode, Instant arrivalTime,
            Instant diagnosisSignedTime, int specimenCount, int slideCount, int completedSlideCount,
            int reportCount) { }

    public record GrossingRow(UUID grossingId, String grossingNo, String sourceType, String grossDescription,
            String grossingDoctor, String recorder, Instant startedAt, Instant completedAt, String completedBy) { }

    public record ResponsibilityRow(UUID responsibilityId, UUID diagnosisId, String roleCode, String doctorId,
            String doctorName, int sequenceNo, Instant acceptedAt, Instant completedAt, Instant endedAt,
            String assignmentSource, String assignmentReason) { }

    public record TechnicalOrderRow(UUID orderId, String orderNo, String statusCode, boolean requiredBeforeSignOut,
            Instant createdAt, String createdBy, int itemCount, int resultCount) { }

    public record DigitalSlideRow(UUID digitalSlideId, UUID blockId, UUID slideId, String bindingMode,
            String statusCode, String viewerReference, String sourcePlatform, Instant updatedAt) { }

    public record ReportRow(UUID reportId, String reportNo, String natureCode, UUID priorReportId, String statusCode,
            String signedBy, Instant signedAt, String withdrawnBy, Instant withdrawnAt, String withdrawalReason,
            String pdfFileReference) { }

    public record AuditRow(UUID eventId, Instant occurredAt, String actorRef, String actorName, String operationCode,
            String targetKind, UUID targetId, String reason, String categoryCode, String changesJson,
            String targetDisplayCode) { }
}

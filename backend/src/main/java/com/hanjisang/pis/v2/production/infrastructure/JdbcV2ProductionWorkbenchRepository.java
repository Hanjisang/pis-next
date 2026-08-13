package com.hanjisang.pis.v2.production.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Query-only production projection backed by existing business facts. */
@Repository
public class JdbcV2ProductionWorkbenchRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2ProductionWorkbenchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoutineRow> findRoutine(String organizationReference) {
        return jdbc.query("""
                SELECT routine.*
                FROM (
                    SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                           COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                           COUNT(DISTINCT gs.specimen_id) AS specimen_count,
                           COUNT(DISTINCT b.id) AS block_count,
                           (SELECT COALESCE(SUM(sr.copies), 0)
                              FROM pis_v2.block required_block
                              JOIN pis_v2.grossing required_grossing ON required_grossing.id = required_block.grossing_id
                              JOIN pis_v2.slide_rule sr ON sr.organization_reference = c.organization_reference
                                AND sr.business_type_id = c.business_type_id AND sr.active = TRUE
                                AND sr.source_context_type = 'INITIAL'
                                AND sr.trigger_code = 'ON_GROSSING_COMPLETE'
                             WHERE required_block.case_id = c.id AND required_block.deleted_at IS NULL
                               AND required_grossing.source_type = 'INITIAL'
                               AND required_grossing.completed_at IS NOT NULL
                               AND required_grossing.deleted_at IS NULL) AS required_count,
                           (SELECT COUNT(*) FROM pis_v2.slide completed_slide
                             JOIN pis_v2.block completed_block ON completed_block.id = completed_slide.block_id
                             JOIN pis_v2.grossing completed_grossing ON completed_grossing.id = completed_block.grossing_id
                             WHERE completed_slide.case_id = c.id
                               AND completed_slide.source_context_type = 'INITIAL'
                               AND completed_slide.required = TRUE
                               AND completed_slide.completed_at IS NOT NULL
                               AND completed_slide.deleted_at IS NULL
                               AND completed_block.deleted_at IS NULL
                               AND completed_grossing.source_type = 'INITIAL'
                               AND completed_grossing.completed_at IS NOT NULL
                               AND completed_grossing.deleted_at IS NULL) AS completed_count,
                           MAX(g.completed_at) AS entered_at,
                           COALESCE((SELECT mp.operator_ref
                                      FROM pis_v2.material_process_fact mp
                                      WHERE mp.case_id = c.id AND mp.organization_reference = ?
                                        AND mp.operator_ref IS NOT NULL
                                      ORDER BY mp.updated_at DESC, mp.id DESC
                                      FETCH FIRST 1 ROW ONLY), '制片人员') AS current_operator
                    FROM pis_v2.pathology_case c
                    JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                    JOIN pis_v2.grossing g ON g.case_id = c.id
                        AND g.source_type = 'INITIAL' AND g.completed_at IS NOT NULL AND g.deleted_at IS NULL
                    JOIN pis_v2.grossing_specimen gs ON gs.grossing_id = g.id AND gs.deleted_at IS NULL
                    JOIN pis_v2.block b ON b.case_id = c.id AND b.grossing_id = g.id AND b.deleted_at IS NULL
                    LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                        AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                            FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                    WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                      AND bt.modality_code = 'TISSUE'
                    GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name,
                             ctx.patient_reference, c.created_at
                ) routine
                WHERE routine.completed_count < routine.required_count
                ORDER BY routine.entered_at, routine.id
                """, (rs, rowNum) -> routine(rs), organizationReference, organizationReference);
    }

    public List<CytologyRow> findCytology(String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       COUNT(DISTINCT s.id) AS specimen_count,
                       COUNT(DISTINCT CASE WHEN EXISTS (
                           SELECT 1 FROM pis_v2.slide direct_slide
                           WHERE direct_slide.case_id = c.id AND direct_slide.specimen_id = s.id
                             AND direct_slide.source_context_type = 'CYTOLOGY'
                             AND direct_slide.required = TRUE AND direct_slide.completed_at IS NOT NULL
                             AND direct_slide.deleted_at IS NULL
                       ) THEN s.id END) AS completed_count,
                       MAX(s.created_at) AS entered_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.specimen s ON s.case_id = c.id AND s.deleted_at IS NULL
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                    AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'CYTOLOGY'
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name,
                         ctx.patient_reference, c.created_at
                HAVING COUNT(DISTINCT CASE WHEN EXISTS (
                           SELECT 1 FROM pis_v2.slide direct_slide
                           WHERE direct_slide.case_id = c.id AND direct_slide.specimen_id = s.id
                             AND direct_slide.source_context_type = 'CYTOLOGY'
                             AND direct_slide.required = TRUE AND direct_slide.completed_at IS NOT NULL
                             AND direct_slide.deleted_at IS NULL
                       ) THEN s.id END) < COUNT(DISTINCT s.id)
                ORDER BY entered_at, c.id
                """, (rs, rowNum) -> cytology(rs), organizationReference);
    }

    public List<FrozenRow> findFrozen(String organizationReference) {
        return jdbc.query("""
                SELECT c.id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       fr.id AS round_id, fr.round_no, COUNT(DISTINCT frs.specimen_id) AS specimen_count,
                       COUNT(DISTINCT CASE WHEN sl.required THEN sl.id END) AS required_count,
                       COUNT(DISTINCT CASE WHEN sl.required AND sl.completed_at IS NOT NULL THEN sl.id END)
                           AS completed_count,
                       fr.arrival_time AS entered_at
                FROM pis_v2.frozen_round fr
                JOIN pis_v2.pathology_case c ON c.id = fr.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.frozen_round_specimen frs ON frs.frozen_round_id = fr.id
                LEFT JOIN pis_v2.slide sl ON sl.case_id = c.id
                    AND sl.source_context_type = 'FROZEN_ROUND' AND sl.source_context_id = fr.id
                    AND sl.deleted_at IS NULL
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                    AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'FROZEN' AND fr.status_code = 'OPEN'
                GROUP BY c.id, c.case_no, bt.business_type_code, bt.display_name,
                         ctx.patient_reference, fr.id, fr.round_no, fr.arrival_time
                HAVING COUNT(DISTINCT CASE WHEN sl.required THEN sl.id END) = 0
                    OR COUNT(DISTINCT CASE WHEN sl.required AND sl.completed_at IS NOT NULL THEN sl.id END)
                       < COUNT(DISTINCT CASE WHEN sl.required THEN sl.id END)
                ORDER BY fr.arrival_time, fr.id
                """, (rs, rowNum) -> frozen(rs), organizationReference);
    }

    public List<TechnicalRow> findTechnical(String organizationReference) {
        return jdbc.query("""
                SELECT o.id AS order_id, o.order_no, o.case_id, c.case_no,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       bt.business_type_code, bt.display_name,
                       i.id AS item_id, i.project_name_snapshot, i.quantity,
                       o.created_at AS entered_at,
                       ((SELECT COUNT(*) FROM pis_v2.technical_order_target target WHERE target.item_id = i.id)
                         * i.quantity
                         * (CASE WHEN p.produces_slide THEN 1 ELSE 0 END
                            + CASE WHEN p.produces_block THEN 1 ELSE 0 END
                            + CASE WHEN p.produces_structured_result THEN 1 ELSE 0 END)) AS required_count,
                       (CASE WHEN p.produces_slide THEN (SELECT COUNT(*)
                              FROM pis_v2.technical_order_output output
                              JOIN pis_v2.slide output_slide ON output_slide.id = output.slide_output_id
                              WHERE output.item_id = i.id AND output.output_kind = 'SLIDE'
                                AND output_slide.completed_at IS NOT NULL AND output_slide.deleted_at IS NULL) ELSE 0 END
                        + CASE WHEN p.produces_block THEN (SELECT COUNT(*)
                              FROM pis_v2.technical_order_output output
                              WHERE output.item_id = i.id AND output.output_kind = 'BLOCK') ELSE 0 END
                        + CASE WHEN p.produces_structured_result THEN (SELECT COUNT(*)
                              FROM pis_v2.technical_order_item_result result
                              WHERE result.item_id = i.id) ELSE 0 END) AS completed_count
                FROM pis_v2.technical_order o
                JOIN pis_v2.pathology_case c ON c.id = o.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.technical_order_item i ON i.order_id = o.id
                JOIN pis_v2.technical_project p ON p.id = i.technical_project_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                    AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                WHERE o.organization_reference = ? AND o.status_code <> 'CANCELLED'
                ORDER BY o.created_at DESC, o.id, i.created_at, i.id
                """, (rs, rowNum) -> technical(rs), organizationReference);
    }

    public List<SlideRow> findIncompleteSlides(String organizationReference) {
        return jdbc.query("""
                SELECT sl.id, sl.case_id, c.case_no, bt.business_type_code, bt.display_name,
                       COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                       COALESCE(b.block_code, s.specimen_code, '—') AS material_code,
                       sl.slide_code, sl.slide_type, sl.source_context_type, sl.source_context_id,
                       sl.created_at, sl.concurrency_version,
                       (SELECT COUNT(*) FROM pis_v2.print_log pl
                        WHERE pl.entity_kind_code = 'SLIDE' AND pl.entity_id = sl.id) AS print_count
                FROM pis_v2.slide sl
                JOIN pis_v2.pathology_case c ON c.id = sl.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.block b ON b.id = sl.block_id
                LEFT JOIN pis_v2.specimen s ON s.id = sl.specimen_id
                LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                    AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                WHERE sl.organization_reference = ? AND sl.deleted_at IS NULL
                  AND c.lifecycle_state_code = 'ACTIVE' AND sl.required = TRUE AND sl.completed_at IS NULL
                ORDER BY sl.created_at, sl.id
                """, (rs, rowNum) -> slide(rs), organizationReference);
    }

    public List<ExceptionRow> findExceptions(String organizationReference) {
        return jdbc.query("""
                SELECT attention.* FROM (
                    SELECT mp.id AS exception_id, mp.case_id, c.case_no, bt.business_type_code, bt.display_name,
                           COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                           COALESCE(target_block.block_code, source_block.block_code, sl.slide_code, '—') AS material_code,
                           sl.slide_code, COALESCE(sl.source_context_type, 'INITIAL') AS source_context_type,
                           mp.exception_code, mp.exception_note, mp.updated_at AS occurred_at, mp.operator_ref
                    FROM pis_v2.material_process_fact mp
                    JOIN pis_v2.pathology_case c ON c.id = mp.case_id
                    JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                    LEFT JOIN pis_v2.slide sl ON sl.id = mp.slide_id AND sl.deleted_at IS NULL
                    LEFT JOIN pis_v2.block target_block ON target_block.id = mp.block_id
                    LEFT JOIN pis_v2.block source_block ON source_block.id = sl.block_id
                    LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                        AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                            FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                    WHERE mp.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                      AND mp.exception_code IS NOT NULL AND mp.exception_code <> ''
                      AND mp.exception_resolved_at IS NULL
                    UNION ALL
                    SELECT rw.id AS exception_id, rw.case_id, c.case_no, bt.business_type_code, bt.display_name,
                           COALESCE(ctx.patient_reference, '未填写') AS patient_reference,
                           COALESCE(b.block_code, sl.slide_code, '—') AS material_code,
                           sl.slide_code, sl.source_context_type, rw.rework_type_code AS exception_code,
                           rw.reason AS exception_note, rw.requested_at AS occurred_at, rw.requested_by_ref AS operator_ref
                    FROM pis_v2.material_rework rw
                    JOIN pis_v2.pathology_case c ON c.id = rw.case_id
                    JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                    JOIN pis_v2.slide sl ON sl.id = rw.original_slide_id
                    LEFT JOIN pis_v2.block b ON b.id = sl.block_id
                    LEFT JOIN pis_v2.case_context_snapshot ctx ON ctx.case_id = c.id
                        AND ctx.snapshot_version_no = (SELECT MAX(latest.snapshot_version_no)
                            FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                    WHERE rw.organization_reference = ? AND rw.status_code = 'REQUESTED'
                      AND c.lifecycle_state_code = 'ACTIVE'
                ) attention
                ORDER BY attention.occurred_at DESC, attention.exception_id
                """, (rs, rowNum) -> exception(rs), organizationReference, organizationReference);
    }

    private static RoutineRow routine(ResultSet rs) throws SQLException {
        return new RoutineRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getInt("specimen_count"), rs.getInt("block_count"),
                rs.getInt("required_count"), rs.getInt("completed_count"), instant(rs, "entered_at"),
                rs.getString("current_operator"));
    }

    private static CytologyRow cytology(ResultSet rs) throws SQLException {
        return new CytologyRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getInt("specimen_count"),
                rs.getInt("completed_count"), instant(rs, "entered_at"));
    }

    private static FrozenRow frozen(ResultSet rs) throws SQLException {
        return new FrozenRow(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getObject("round_id", UUID.class), rs.getInt("round_no"),
                rs.getInt("specimen_count"), rs.getInt("required_count"), rs.getInt("completed_count"),
                instant(rs, "entered_at"));
    }

    private static TechnicalRow technical(ResultSet rs) throws SQLException {
        return new TechnicalRow(rs.getObject("order_id", UUID.class), rs.getString("order_no"),
                rs.getObject("case_id", UUID.class), rs.getString("case_no"),
                rs.getString("patient_reference"), rs.getString("business_type_code"),
                rs.getString("display_name"), rs.getObject("item_id", UUID.class),
                rs.getString("project_name_snapshot"), rs.getInt("quantity"), rs.getInt("required_count"),
                rs.getInt("completed_count"), instant(rs, "entered_at"));
    }

    private static SlideRow slide(ResultSet rs) throws SQLException {
        return new SlideRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("case_no"), rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getString("material_code"), rs.getString("slide_code"),
                rs.getString("slide_type"), rs.getString("source_context_type"),
                rs.getObject("source_context_id", UUID.class), instant(rs, "created_at"),
                rs.getLong("concurrency_version"), rs.getInt("print_count"));
    }

    private static ExceptionRow exception(ResultSet rs) throws SQLException {
        return new ExceptionRow(rs.getObject("exception_id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("case_no"), rs.getString("business_type_code"), rs.getString("display_name"),
                rs.getString("patient_reference"), rs.getString("material_code"), rs.getString("slide_code"),
                rs.getString("source_context_type"), rs.getString("exception_code"),
                rs.getString("exception_note"), instant(rs, "occurred_at"), rs.getString("operator_ref"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record RoutineRow(UUID caseId, String pathologyNo, String businessTypeCode, String businessTypeName,
            String patientReference, int specimenCount, int blockCount, int requiredCount, int completedCount,
            Instant enteredAt, String currentOperator) { }

    public record CytologyRow(UUID caseId, String pathologyNo, String businessTypeCode, String businessTypeName,
            String patientReference, int specimenCount, int completedCount, Instant enteredAt) { }

    public record FrozenRow(UUID caseId, String pathologyNo, String businessTypeCode, String businessTypeName,
            String patientReference, UUID roundId, int roundNo, int specimenCount, int requiredCount,
            int completedCount, Instant enteredAt) { }

    public record TechnicalRow(UUID orderId, String orderNo, UUID caseId, String pathologyNo,
            String patientReference, String businessTypeCode, String businessTypeName, UUID itemId,
            String projectName, int quantity, int requiredCount, int completedCount, Instant enteredAt) { }

    public record SlideRow(UUID slideId, UUID caseId, String pathologyNo, String businessTypeCode,
            String businessTypeName, String patientReference, String materialCode, String slideCode,
            String slideType, String productionContext, UUID productionContextId, Instant enteredAt,
            long concurrencyVersion, int printCount) { }

    public record ExceptionRow(UUID exceptionId, UUID caseId, String pathologyNo, String businessTypeCode,
            String businessTypeName, String patientReference, String materialCode, String slideCode,
            String productionContext, String exceptionType, String exceptionNote, Instant occurredAt,
            String operatorReference) { }
}

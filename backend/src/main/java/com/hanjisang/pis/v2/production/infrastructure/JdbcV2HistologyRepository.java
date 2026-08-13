package com.hanjisang.pis.v2.production.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores optional technical trace facts; it deliberately has no workflow transition logic. */
@Repository
public class JdbcV2HistologyRepository {

    private static final List<String> STAGES = List.of(
            "DEHYDRATION", "EMBEDDING", "SECTIONING", "PREPARATION", "STAINING", "COVERSLIPPING");
    private final JdbcTemplate jdbc;

    public JdbcV2HistologyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ProcessRow> findWorkbench(String organizationReference, UUID caseId) {
        return findWorkbench(organizationReference, caseId, null);
    }

    public List<ProcessRow> findWorkbench(String organizationReference, UUID caseId, UUID frozenRoundId) {
        String caseFilter = caseId == null ? "" : " AND c.id = ? ";
        String roundFilter = frozenRoundId == null ? ""
                : " AND sl.source_context_type = 'FROZEN_ROUND' AND sl.source_context_id = ? ";
        String sql = """
                SELECT sl.id AS slide_id, sl.case_id, c.case_no, context.patient_reference,
                       bt.business_type_code, s.specimen_code, b.block_code, sl.source_context_type,
                       sl.slide_code, sl.slide_type, sl.completed_at AS slide_completed_at,
                       sl.concurrency_version,
                       (SELECT COUNT(*) FROM pis_v2.print_log pl
                         WHERE pl.entity_kind_code = 'SLIDE' AND pl.entity_id = sl.id) AS print_count,
                       stage_codes.stage_code AS phase_code, p.id AS process_fact_id,
                       p.target_kind_code, COALESCE(p.block_id, p.slide_id) AS target_id,
                       p.started_at, p.completed_at, p.operator_ref, p.device_reference,
                       p.equipment_id, p.batch_reference, p.stain_code, p.exception_code,
                       p.exception_note, p.exception_resolved_at
                FROM pis_v2.slide sl
                JOIN pis_v2.pathology_case c ON c.id = sl.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.block b ON b.id = sl.block_id
                LEFT JOIN pis_v2.specimen s ON s.id = COALESCE(sl.specimen_id, b.specimen_id)
                LEFT JOIN pis_v2.case_context_snapshot context
                    ON context.case_id = c.id
                    AND context.snapshot_version_no = (SELECT MAX(snapshot_version_no)
                        FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                CROSS JOIN (VALUES ('DEHYDRATION'), ('EMBEDDING'), ('SECTIONING'), ('STAINING'),
                    ('COVERSLIPPING')) AS stage_codes(stage_code)
                LEFT JOIN pis_v2.material_process_fact p ON p.phase_code = stage_codes.stage_code
                    AND ((stage_codes.stage_code IN ('DEHYDRATION', 'EMBEDDING') AND p.block_id = b.id)
                      OR (stage_codes.stage_code IN ('SECTIONING', 'STAINING', 'COVERSLIPPING')
                          AND p.slide_id = sl.id))
                WHERE sl.organization_reference = ? AND sl.deleted_at IS NULL
                  AND c.lifecycle_state_code = 'ACTIVE' AND bt.modality_code IN ('TISSUE', 'FROZEN')
                  AND sl.block_id IS NOT NULL
                """ + caseFilter + roundFilter + """
                ORDER BY CASE WHEN sl.completed_at IS NULL THEN 0 ELSE 1 END,
                    c.case_no, sl.slide_code, stage_codes.stage_code""";
        if (caseId == null && frozenRoundId == null) {
            return jdbc.query(sql, (rs, rowNum) -> row(rs), organizationReference);
        }
        if (frozenRoundId == null) {
            return jdbc.query(sql, (rs, rowNum) -> row(rs), organizationReference, caseId);
        }
        if (caseId == null) {
            return jdbc.query(sql, (rs, rowNum) -> row(rs), organizationReference, frozenRoundId);
        }
        return jdbc.query(sql, (rs, rowNum) -> row(rs), organizationReference, caseId, frozenRoundId);
    }

    public Optional<TargetScope> findTarget(String targetKind, UUID targetId, String organizationReference) {
        if ("BLOCK".equals(targetKind)) {
            return jdbc.query("""
                    SELECT b.id AS target_id, b.case_id, b.block_code AS display_code
                    FROM pis_v2.block b
                    JOIN pis_v2.grossing g ON g.id = b.grossing_id
                    JOIN pis_v2.pathology_case c ON c.id = b.case_id
                    JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                    WHERE b.id = ? AND b.organization_reference = ? AND b.deleted_at IS NULL
                      AND c.lifecycle_state_code = 'ACTIVE' AND bt.modality_code = 'TISSUE'
                      AND g.source_type = 'INITIAL' AND g.completed_at IS NOT NULL AND g.deleted_at IS NULL
                    """, rs -> rs.next() ? Optional.of(new TargetScope("BLOCK", rs.getObject("target_id", UUID.class),
                    rs.getObject("case_id", UUID.class), rs.getString("display_code"))) : Optional.empty(),
                    targetId, organizationReference);
        }
        if ("SLIDE".equals(targetKind)) {
            return jdbc.query("""
                    SELECT sl.id AS target_id, sl.case_id, sl.slide_code AS display_code
                    FROM pis_v2.slide sl
                    JOIN pis_v2.pathology_case c ON c.id = sl.case_id
                    JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                    LEFT JOIN pis_v2.block b ON b.id = sl.block_id
                    WHERE sl.id = ? AND sl.organization_reference = ? AND sl.deleted_at IS NULL
                      AND c.lifecycle_state_code = 'ACTIVE'
                      AND ((bt.modality_code = 'TISSUE' AND sl.source_context_type = 'INITIAL')
                        OR (bt.modality_code = 'CYTOLOGY' AND sl.source_context_type = 'CYTOLOGY'))
                      AND b.deleted_at IS NULL
                    """, rs -> rs.next() ? Optional.of(new TargetScope("SLIDE", rs.getObject("target_id", UUID.class),
                    rs.getObject("case_id", UUID.class), rs.getString("display_code"))) : Optional.empty(),
                    targetId, organizationReference);
        }
        return Optional.empty();
    }

    public Optional<TargetScope> findSlide(UUID slideId, String organizationReference) {
        return findTarget("SLIDE", slideId, organizationReference);
    }

    public Optional<TargetScope> findBlockForSlide(UUID slideId, String organizationReference) {
        return jdbc.query("""
                SELECT b.id AS target_id, b.case_id, b.block_code AS display_code
                FROM pis_v2.slide sl
                JOIN pis_v2.block b ON b.id = sl.block_id
                JOIN pis_v2.pathology_case c ON c.id = sl.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE sl.id = ? AND sl.organization_reference = ? AND sl.deleted_at IS NULL
                  AND b.deleted_at IS NULL AND c.lifecycle_state_code = 'ACTIVE' AND bt.modality_code = 'TISSUE'
                  AND sl.source_context_type = 'INITIAL'
                """, rs -> rs.next() ? Optional.of(new TargetScope("BLOCK", rs.getObject("target_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("display_code"))) : Optional.empty(),
                slideId, organizationReference);
    }

    public Optional<EquipmentScope> findEquipment(String reference, String organizationReference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        UUID id = null;
        try { id = UUID.fromString(reference.trim()); } catch (IllegalArgumentException ignored) { }
        if (id != null) {
            Optional<EquipmentScope> byId = equipment("id = ?", id, organizationReference);
            if (byId.isPresent()) return byId;
        }
        return equipment("equipment_code = ?", reference.trim(), organizationReference);
    }

    private Optional<EquipmentScope> equipment(String predicate, Object value, String organizationReference) {
        return jdbc.query("SELECT id, equipment_code, status_code FROM pis_v2.equipment WHERE " + predicate
                        + " AND organization_reference = ?",
                rs -> rs.next() ? Optional.of(new EquipmentScope(rs.getObject("id", UUID.class),
                        rs.getString("equipment_code"), rs.getString("status_code"))) : Optional.empty(),
                value, organizationReference);
    }

    public ProcessRow saveStart(TargetScope target, String organizationReference, String stageCode,
            String operatorRef, EquipmentScope equipment, String batchReference, String note, Instant now) {
        Timestamp at = Timestamp.from(now);
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET started_at = COALESCE(started_at, ?), operator_ref = COALESCE(operator_ref, ?),
                    equipment_id = COALESCE(?, equipment_id),
                    device_reference = COALESCE(?, device_reference),
                    batch_reference = COALESCE(?, batch_reference),
                    exception_note = COALESCE(?, exception_note), updated_at = ?,
                    concurrency_version = concurrency_version + 1
                WHERE target_kind_code = ? AND COALESCE(block_id, slide_id) = ?
                  AND phase_code = ? AND organization_reference = ?
                """, at, operatorRef, equipment == null ? null : equipment.id(),
                equipment == null ? null : equipment.equipmentCode(), blankToNull(batchReference),
                blankToNull(note), at, target.targetKind(), target.targetId(), stageCode, organizationReference);
        if (changed == 0) insertFact(target, organizationReference, stageCode, at, null, operatorRef, equipment,
                batchReference, null, null, note, now);
        return findFact(target.targetKind(), target.targetId(), stageCode, organizationReference).orElseThrow();
    }

    public ProcessRow saveComplete(TargetScope target, String organizationReference, String stageCode,
            String operatorRef, EquipmentScope equipment, String stainCode, String note, Instant now) {
        Timestamp at = Timestamp.from(now);
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET started_at = COALESCE(started_at, ?), completed_at = ?,
                    operator_ref = COALESCE(operator_ref, ?), equipment_id = COALESCE(?, equipment_id),
                    device_reference = COALESCE(?, device_reference), stain_code = COALESCE(?, stain_code),
                    exception_note = COALESCE(?, exception_note), updated_at = ?,
                    concurrency_version = concurrency_version + 1
                WHERE target_kind_code = ? AND COALESCE(block_id, slide_id) = ?
                  AND phase_code = ? AND organization_reference = ?
                """, at, at, operatorRef, equipment == null ? null : equipment.id(),
                equipment == null ? null : equipment.equipmentCode(), blankToNull(stainCode), blankToNull(note),
                at, target.targetKind(), target.targetId(), stageCode, organizationReference);
        if (changed == 0) insertFact(target, organizationReference, stageCode, at, at, operatorRef, equipment,
                null, stainCode, null, note, now);
        return findFact(target.targetKind(), target.targetId(), stageCode, organizationReference).orElseThrow();
    }

    public ProcessRow saveException(TargetScope target, String organizationReference, String stageCode,
            String operatorRef, String exceptionCode, String note, Instant now) {
        Timestamp at = Timestamp.from(now);
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET started_at = COALESCE(started_at, ?), operator_ref = COALESCE(operator_ref, ?),
                    exception_code = ?, exception_note = ?, exception_resolved_at = NULL,
                    exception_resolved_by_ref = NULL, exception_resolution_note = NULL,
                    updated_at = ?, concurrency_version = concurrency_version + 1
                WHERE target_kind_code = ? AND COALESCE(block_id, slide_id) = ?
                  AND phase_code = ? AND organization_reference = ?
                """, at, operatorRef, exceptionCode, note, at, target.targetKind(), target.targetId(),
                stageCode, organizationReference);
        if (changed == 0) insertFact(target, organizationReference, stageCode, at, null, operatorRef, null,
                null, null, exceptionCode, note, now);
        return findFact(target.targetKind(), target.targetId(), stageCode, organizationReference).orElseThrow();
    }

    private void insertFact(TargetScope target, String organizationReference, String stageCode,
            Timestamp startedAt, Timestamp completedAt, String operatorRef, EquipmentScope equipment,
            String batchReference, String stainCode, String exceptionCode, String note, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO pis_v2.material_process_fact
                        (id, case_id, slide_id, block_id, target_kind_code, phase_code, started_at, completed_at,
                         operator_ref, equipment_id, device_reference, batch_reference, stain_code,
                         exception_code, exception_note, concurrency_version, organization_reference,
                         created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                    """, UUID.randomUUID(), target.caseId(), "SLIDE".equals(target.targetKind()) ? target.targetId() : null,
                    "BLOCK".equals(target.targetKind()) ? target.targetId() : null, target.targetKind(), stageCode,
                    startedAt, completedAt, operatorRef, equipment == null ? null : equipment.id(),
                    equipment == null ? null : equipment.equipmentCode(), blankToNull(batchReference),
                    blankToNull(stainCode), exceptionCode, blankToNull(note), organizationReference,
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }
    }

    public Optional<ProcessRow> findFact(String targetKind, UUID targetId, String stageCode,
            String organizationReference) {
        return jdbc.query("""
                SELECT p.id AS process_fact_id, p.case_id, p.slide_id, p.block_id, p.target_kind_code,
                       COALESCE(p.block_id, p.slide_id) AS target_id, p.phase_code, p.started_at,
                       p.completed_at, p.operator_ref, p.device_reference, p.equipment_id,
                       p.batch_reference, p.stain_code, p.exception_code, p.exception_note,
                       p.exception_resolved_at
                FROM pis_v2.material_process_fact p
                WHERE p.target_kind_code = ? AND COALESCE(p.block_id, p.slide_id) = ?
                  AND p.phase_code = ? AND p.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(fact(rs)) : Optional.empty(), targetKind, targetId,
                stageCode, organizationReference);
    }

    public Optional<ProcessRow> resolveException(UUID factId, String organizationReference, String actorRef,
            String note, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET exception_resolved_at = ?, exception_resolved_by_ref = ?, exception_resolution_note = ?,
                    updated_at = ?, concurrency_version = concurrency_version + 1
                WHERE id = ? AND organization_reference = ? AND exception_code IS NOT NULL
                  AND exception_resolved_at IS NULL
                """, Timestamp.from(now), actorRef, note, Timestamp.from(now), factId, organizationReference);
        return changed == 1 ? findById(factId, organizationReference) : Optional.empty();
    }

    public Optional<ProcessRow> findById(UUID factId, String organizationReference) {
        return jdbc.query("""
                SELECT p.id AS process_fact_id, p.case_id, p.slide_id, p.block_id, p.target_kind_code,
                       COALESCE(p.block_id, p.slide_id) AS target_id, p.phase_code, p.started_at,
                       p.completed_at, p.operator_ref, p.device_reference, p.equipment_id,
                       p.batch_reference, p.stain_code, p.exception_code, p.exception_note,
                       p.exception_resolved_at
                FROM pis_v2.material_process_fact p
                JOIN pis_v2.pathology_case c ON c.id = p.case_id
                WHERE p.id = ? AND p.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                """, rs -> rs.next() ? Optional.of(fact(rs)) : Optional.empty(), factId, organizationReference);
    }

    public ProcessRow correct(UUID factId, String organizationReference, String actorRef, Instant completedAt,
            UUID equipmentId, String note, String reason, Instant now) {
        ProcessRow prior = findById(factId, organizationReference).orElseThrow();
        jdbc.update("""
                INSERT INTO pis_v2.material_process_fact_correction
                    (id, process_fact_id, prior_completed_at, prior_operator_ref, prior_equipment_id, prior_note,
                     corrected_completed_at, corrected_operator_ref, corrected_equipment_id, corrected_note,
                     reason, corrected_at, corrected_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), factId, timestamp(prior.completedAt()), prior.operatorRef(),
                prior.equipmentId(), prior.exceptionNote(), timestamp(completedAt), actorRef, equipmentId, note,
                reason, Timestamp.from(now), actorRef, organizationReference);
        jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET completed_at = ?, operator_ref = ?, equipment_id = ?, exception_note = ?, updated_at = ?,
                    concurrency_version = concurrency_version + 1
                WHERE id = ? AND organization_reference = ?
                """, timestamp(completedAt), actorRef, equipmentId, blankToNull(note), Timestamp.from(now),
                factId, organizationReference);
        return findById(factId, organizationReference).orElseThrow();
    }

    private static ProcessRow row(ResultSet rs) throws SQLException {
        return new ProcessRow(rs.getObject("process_fact_id", UUID.class), rs.getString("target_kind_code"),
                rs.getObject("target_id", UUID.class), rs.getObject("slide_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("case_no"), rs.getString("patient_reference"),
                rs.getString("business_type_code"), rs.getString("specimen_code"), rs.getString("block_code"),
                rs.getString("source_context_type"), rs.getString("slide_code"), rs.getString("slide_type"),
                instant(rs, "slide_completed_at"), rs.getLong("concurrency_version"), rs.getInt("print_count"),
                rs.getString("phase_code"), instant(rs, "started_at"), instant(rs, "completed_at"),
                rs.getString("operator_ref"), rs.getString("device_reference"),
                rs.getObject("equipment_id", UUID.class), rs.getString("batch_reference"),
                rs.getString("stain_code"), rs.getString("exception_code"), rs.getString("exception_note"),
                instant(rs, "exception_resolved_at"));
    }

    private static ProcessRow fact(ResultSet rs) throws SQLException {
        return new ProcessRow(rs.getObject("process_fact_id", UUID.class), rs.getString("target_kind_code"),
                rs.getObject("target_id", UUID.class), rs.getObject("slide_id", UUID.class),
                rs.getObject("case_id", UUID.class), null, null, null, null, null, null, null, null,
                null, 0, 0, rs.getString("phase_code"), instant(rs, "started_at"), instant(rs, "completed_at"),
                rs.getString("operator_ref"), rs.getString("device_reference"),
                rs.getObject("equipment_id", UUID.class), rs.getString("batch_reference"),
                rs.getString("stain_code"), rs.getString("exception_code"), rs.getString("exception_note"),
                instant(rs, "exception_resolved_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public static boolean supported(String stageCode) { return STAGES.contains(normalize(stageCode)); }
    public static String normalize(String stageCode) {
        return "MOUNTING".equals(stageCode) ? "COVERSLIPPING" : stageCode;
    }
    public static String requiredTarget(String stageCode) {
        String normalized = normalize(stageCode);
        return List.of("DEHYDRATION", "EMBEDDING").contains(normalized) ? "BLOCK" : "SLIDE";
    }

    public record TargetScope(String targetKind, UUID targetId, UUID caseId, String displayCode) { }
    public record EquipmentScope(UUID id, String equipmentCode, String statusCode) { }
    public record ProcessRow(UUID factId, String targetKind, UUID targetId, UUID slideId, UUID caseId,
            String caseNo, String patientReference, String businessTypeCode, String specimenCode, String blockCode,
            String sourceContextType, String slideCode, String slideType, Instant slideCompletedAt,
            long concurrencyVersion, int printCount, String phaseCode, Instant startedAt, Instant completedAt,
            String operatorRef, String deviceReference, UUID equipmentId, String batchReference, String stainCode,
            String exceptionCode, String exceptionNote, Instant exceptionResolvedAt) { }
}

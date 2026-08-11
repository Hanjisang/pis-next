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

@Repository
public class JdbcV2HistologyRepository {

    private static final String[] PHASES = { "DEHYDRATION", "EMBEDDING", "SECTIONING", "STAINING", "MOUNTING" };
    private final JdbcTemplate jdbc;

    public JdbcV2HistologyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ProcessRow> findWorkbench(String organizationReference, UUID caseId) {
        String caseFilter = caseId == null ? "" : " AND c.id = ? ";
        String sql = """
                SELECT sl.id AS slide_id, sl.case_id, c.case_no, context.patient_reference,
                       bt.business_type_code, s.specimen_code, b.block_code, sl.source_context_type,
                       sl.slide_code, sl.slide_type, sl.completed_at AS slide_completed_at,
                       sl.concurrency_version,
                       (SELECT COUNT(*) FROM pis_v2.print_log pl
                         WHERE pl.entity_kind_code = 'SLIDE' AND pl.entity_id = sl.id) AS print_count,
                       p.phase_code, p.started_at,
                       p.completed_at, p.operator_ref, p.device_reference, p.batch_reference,
                       p.exception_code, p.exception_note
                FROM pis_v2.slide sl
                JOIN pis_v2.pathology_case c ON c.id = sl.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.block b ON b.id = sl.block_id
                LEFT JOIN pis_v2.specimen s ON s.id = COALESCE(sl.specimen_id, b.specimen_id)
                LEFT JOIN pis_v2.case_context_snapshot context
                    ON context.case_id = c.id
                    AND context.snapshot_version_no = (SELECT MAX(snapshot_version_no)
                        FROM pis_v2.case_context_snapshot latest WHERE latest.case_id = c.id)
                CROSS JOIN (VALUES ('DEHYDRATION'), ('EMBEDDING'), ('SECTIONING'), ('STAINING'), ('MOUNTING'))
                    AS phase_codes(phase_code)
                LEFT JOIN pis_v2.material_process_fact p
                    ON p.slide_id = sl.id AND p.phase_code = phase_codes.phase_code
                WHERE sl.organization_reference = ? AND sl.deleted_at IS NULL AND c.lifecycle_state_code = 'ACTIVE'
                  AND bt.modality_code = 'TISSUE' AND sl.block_id IS NOT NULL
                """ + caseFilter + """
                ORDER BY CASE WHEN sl.completed_at IS NULL THEN 0 ELSE 1 END,
                    c.case_no, sl.slide_code, phase_codes.phase_code""";
        if (caseId == null) {
            return jdbc.query(sql, (rs, rowNum) -> row(rs), organizationReference);
        }
        return jdbc.query(sql, (rs, rowNum) -> row(rs), organizationReference, caseId);
    }

    public Optional<SlideScope> findSlide(UUID slideId, String organizationReference) {
        return jdbc.query("""
                SELECT sl.id, sl.case_id, c.case_no, sl.slide_code
                FROM pis_v2.slide sl JOIN pis_v2.pathology_case c ON c.id = sl.case_id
                WHERE sl.id = ? AND sl.organization_reference = ? AND sl.deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(new SlideScope(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("case_no"), rs.getString("slide_code")))
                : Optional.empty(), slideId, organizationReference);
    }

    public ProcessRow saveStart(UUID slideId, String organizationReference, String phaseCode, String operatorRef,
            String deviceReference, String batchReference, Instant now) {
        SlideScope scope = findSlide(slideId, organizationReference).orElseThrow();
        Timestamp startedAt = Timestamp.from(now);
        Timestamp updatedAt = Timestamp.from(now);
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET started_at = COALESCE(started_at, ?),
                    operator_ref = COALESCE(operator_ref, ?),
                    device_reference = COALESCE(?, device_reference),
                    batch_reference = COALESCE(?, batch_reference),
                    updated_at = ?, concurrency_version = concurrency_version + 1
                WHERE slide_id = ? AND phase_code = ? AND organization_reference = ?
                """, startedAt, operatorRef, blankToNull(deviceReference), blankToNull(batchReference), updatedAt,
                slideId, phaseCode, organizationReference);
        if (changed == 0) {
            try {
                jdbc.update("""
                INSERT INTO pis_v2.material_process_fact
                    (id, case_id, slide_id, phase_code, started_at, operator_ref, device_reference,
                     batch_reference, concurrency_version, organization_reference, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """, UUID.randomUUID(), scope.caseId(), slideId, phaseCode, startedAt, operatorRef,
                blankToNull(deviceReference), blankToNull(batchReference), organizationReference, Timestamp.from(now),
                Timestamp.from(now));
            } catch (DataIntegrityViolationException ignored) {
                saveStart(slideId, organizationReference, phaseCode, operatorRef, deviceReference, batchReference, now);
            }
        }
        return findFact(slideId, phaseCode, organizationReference).orElseThrow();
    }

    public ProcessRow saveComplete(UUID slideId, String organizationReference, String phaseCode, String operatorRef,
            Instant now) {
        SlideScope scope = findSlide(slideId, organizationReference).orElseThrow();
        Timestamp nowValue = Timestamp.from(now);
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET started_at = COALESCE(started_at, ?), completed_at = ?,
                    operator_ref = COALESCE(operator_ref, ?), updated_at = ?, concurrency_version = concurrency_version + 1
                WHERE slide_id = ? AND phase_code = ? AND organization_reference = ?
                """, nowValue, nowValue, operatorRef, nowValue, slideId, phaseCode, organizationReference);
        if (changed == 0) {
            try {
                jdbc.update("""
                INSERT INTO pis_v2.material_process_fact
                    (id, case_id, slide_id, phase_code, started_at, completed_at, operator_ref,
                     concurrency_version, organization_reference, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """, UUID.randomUUID(), scope.caseId(), slideId, phaseCode, nowValue, nowValue, operatorRef,
                organizationReference, nowValue, nowValue);
            } catch (DataIntegrityViolationException ignored) {
                saveComplete(slideId, organizationReference, phaseCode, operatorRef, now);
            }
        }
        return findFact(slideId, phaseCode, organizationReference).orElseThrow();
    }

    public ProcessRow saveException(UUID slideId, String organizationReference, String phaseCode, String operatorRef,
            String exceptionCode, String exceptionNote, Instant now) {
        SlideScope scope = findSlide(slideId, organizationReference).orElseThrow();
        Timestamp nowValue = Timestamp.from(now);
        int changed = jdbc.update("""
                UPDATE pis_v2.material_process_fact
                SET exception_code = ?, exception_note = ?, updated_at = ?,
                    concurrency_version = concurrency_version + 1
                WHERE slide_id = ? AND phase_code = ? AND organization_reference = ?
                """, exceptionCode, exceptionNote, nowValue, slideId, phaseCode, organizationReference);
        if (changed == 0) {
            try {
                jdbc.update("""
                INSERT INTO pis_v2.material_process_fact
                    (id, case_id, slide_id, phase_code, started_at, operator_ref, exception_code,
                     exception_note, concurrency_version, organization_reference, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """, UUID.randomUUID(), scope.caseId(), slideId, phaseCode, nowValue, operatorRef,
                exceptionCode, exceptionNote, organizationReference, nowValue, nowValue);
            } catch (DataIntegrityViolationException ignored) {
                saveException(slideId, organizationReference, phaseCode, operatorRef, exceptionCode, exceptionNote, now);
            }
        }
        return findFact(slideId, phaseCode, organizationReference).orElseThrow();
    }

    private Optional<ProcessRow> findFact(UUID slideId, String phaseCode, String organizationReference) {
        return jdbc.query("""
                SELECT p.id, p.case_id, p.slide_id, p.phase_code, p.started_at, p.completed_at, p.operator_ref,
                       p.device_reference, p.batch_reference, p.exception_code, p.exception_note
                FROM pis_v2.material_process_fact p
                WHERE p.slide_id = ? AND p.phase_code = ? AND p.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(fact(rs)) : Optional.empty(), slideId, phaseCode,
                organizationReference);
    }

    private static ProcessRow row(ResultSet rs) throws SQLException {
        return new ProcessRow(rs.getObject("slide_id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("case_no"), rs.getString("patient_reference"), rs.getString("business_type_code"),
                rs.getString("specimen_code"), rs.getString("block_code"), rs.getString("source_context_type"),
                rs.getString("slide_code"), rs.getString("slide_type"), instant(rs, "slide_completed_at"),
                rs.getLong("concurrency_version"), rs.getInt("print_count"), rs.getString("phase_code"),
                instant(rs, "started_at"), instant(rs, "completed_at"), rs.getString("operator_ref"),
                rs.getString("device_reference"), rs.getString("batch_reference"), rs.getString("exception_code"),
                rs.getString("exception_note"));
    }

    private static ProcessRow fact(ResultSet rs) throws SQLException {
        return new ProcessRow(rs.getObject("slide_id", UUID.class), rs.getObject("case_id", UUID.class), null,
                null, null, null, null, null, null, null, null, 0L, 0, rs.getString("phase_code"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"), rs.getString("operator_ref"), rs.getString("device_reference"),
                rs.getString("batch_reference"), rs.getString("exception_code"), rs.getString("exception_note"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    public static boolean supported(String phaseCode) { for (String phase : PHASES) if (phase.equals(phaseCode)) return true; return false; }

    public record SlideScope(UUID slideId, UUID caseId, String caseNo, String slideCode) { }
    public record ProcessRow(UUID slideId, UUID caseId, String caseNo, String patientReference, String businessTypeCode,
            String specimenCode, String blockCode, String sourceContextType, String slideCode, String slideType,
            Instant slideCompletedAt, long concurrencyVersion, int printCount, String phaseCode, Instant startedAt,
            Instant completedAt,
            String operatorRef, String deviceReference, String batchReference, String exceptionCode,
            String exceptionNote) { }
}

package com.hanjisang.pis.v2.custody.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2CustodyRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2CustodyRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void insertLocation(UUID id, UUID parentId, String code, String name, String kind,
            String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.archive_location
                    (id, parent_id, location_code, location_name, location_kind_code, active,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?)
                """, id, parentId, code, name, kind, organizationReference, Timestamp.from(now), actorRef);
    }

    public boolean locationActive(UUID locationId, String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.archive_location
                WHERE id = ? AND organization_reference = ? AND active = TRUE
                """, Integer.class, locationId, organizationReference);
        return count != null && count == 1;
    }

    public List<LocationRow> findLocations(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, parent_id, location_code, location_name, location_kind_code
                FROM pis_v2.archive_location
                WHERE organization_reference = ? AND active = TRUE
                ORDER BY location_code, id
                """, (rs, rowNum) -> new LocationRow(rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class), rs.getString("location_code"),
                rs.getString("location_name"), rs.getString("location_kind_code")), organizationReference);
    }

    public void archiveBlock(UUID blockId, UUID locationId, String eventCode, String reason,
            String actorRef, Instant now) {
        jdbcTemplate.update("DELETE FROM pis_v2.block_archive_current WHERE block_id = ?", blockId);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.block_archive_current (block_id, location_id, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?)
                """, blockId, locationId, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.material_archive_history
                    (id, block_id, slide_id, location_id, event_code, occurred_at, occurred_by_ref, reason)
                VALUES (?, ?, NULL, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), blockId, locationId, eventCode, Timestamp.from(now), actorRef, reason);
    }

    public void archiveSlide(UUID slideId, UUID locationId, String eventCode, String reason,
            String actorRef, Instant now) {
        jdbcTemplate.update("DELETE FROM pis_v2.slide_archive_current WHERE slide_id = ?", slideId);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.slide_archive_current (slide_id, location_id, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?)
                """, slideId, locationId, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.material_archive_history
                    (id, block_id, slide_id, location_id, event_code, occurred_at, occurred_by_ref, reason)
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), slideId, locationId, eventCode, Timestamp.from(now), actorRef, reason);
    }

    public void insertLoan(UUID loanId, String borrower, String borrowerDepartment, String purpose,
            Instant expectedReturnAt, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.loan
                    (id, borrower_reference, borrower_department, purpose, expected_return_at, status_code,
                     borrowed_at, borrowed_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, 'BORROWED', ?, ?, ?)
                """, loanId, borrower, borrowerDepartment, purpose, Timestamp.from(expectedReturnAt),
                Timestamp.from(now), actorRef, organizationReference);
    }

    public void insertLoanBlockItem(UUID loanId, UUID blockId) {
        jdbcTemplate.update("INSERT INTO pis_v2.loan_item (id, loan_id, block_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), loanId, blockId);
    }

    public void insertLoanSlideItem(UUID loanId, UUID slideId) {
        jdbcTemplate.update("INSERT INTO pis_v2.loan_item (id, loan_id, block_id, slide_id) VALUES (?, ?, NULL, ?)",
                UUID.randomUUID(), loanId, slideId);
    }

    public boolean loanExists(UUID loanId, String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.loan WHERE id = ? AND organization_reference = ?",
                Integer.class, loanId, organizationReference);
        return count != null && count == 1;
    }

    public boolean returnLoan(UUID loanId, String actorRef, Instant now, String organizationReference) {
        int loan = jdbcTemplate.update("""
                UPDATE pis_v2.loan SET status_code = 'RETURNED', returned_at = ?, returned_by_ref = ?
                WHERE id = ? AND organization_reference = ? AND status_code = 'BORROWED'
                """, Timestamp.from(now), actorRef, loanId, organizationReference);
        if (loan != 1) return false;
        jdbcTemplate.update("""
                UPDATE pis_v2.loan_item SET returned_at = ?, returned_by_ref = ?
                WHERE loan_id = ? AND returned_at IS NULL
                """, Timestamp.from(now), actorRef, loanId);
        return true;
    }

    public List<LoanRow> findLoans(String organizationReference) {
        List<LoanBaseRow> loans = jdbcTemplate.query("""
                SELECT id, borrower_reference, borrower_department, purpose, borrowed_at, expected_return_at,
                       returned_at, returned_by_ref
                FROM pis_v2.loan
                WHERE organization_reference = ?
                ORDER BY CASE WHEN returned_at IS NULL THEN 0 ELSE 1 END,
                         expected_return_at NULLS LAST, borrowed_at DESC, id DESC
                FETCH FIRST 200 ROWS ONLY
                """, (rs, rowNum) -> new LoanBaseRow(rs.getObject("id", UUID.class),
                rs.getString("borrower_reference"), rs.getString("borrower_department"), rs.getString("purpose"),
                instant(rs, "borrowed_at"), instant(rs, "expected_return_at"), instant(rs, "returned_at"),
                rs.getString("returned_by_ref")), organizationReference);
        return loans.stream().map(loan -> new LoanRow(loan.loanId(), loan.borrowerReference(),
                loan.borrowerDepartment(), loan.purpose(), loan.borrowedAt(), loan.expectedReturnAt(),
                loan.returnedAt(), loan.returnedByRef(), jdbcTemplate.query("""
                        SELECT CASE WHEN li.block_id IS NOT NULL THEN 'BLOCK' ELSE 'SLIDE' END AS material_kind,
                               COALESCE(b.id, sl.id) AS material_id,
                               COALESCE(b.block_code, sl.slide_code) AS material_code,
                               c.id AS case_id, c.case_no, li.returned_at
                        FROM pis_v2.loan_item li
                        LEFT JOIN pis_v2.block b ON b.id = li.block_id
                        LEFT JOIN pis_v2.slide sl ON sl.id = li.slide_id
                        JOIN pis_v2.pathology_case c ON c.id = COALESCE(b.case_id, sl.case_id)
                        WHERE li.loan_id = ?
                        ORDER BY material_kind, material_code
                        """, (rs, rowNum) -> new LoanItemRow(rs.getString("material_kind"),
                        rs.getObject("material_id", UUID.class), rs.getString("material_code"),
                        rs.getObject("case_id", UUID.class), rs.getString("case_no"),
                        instant(rs, "returned_at")), loan.loanId()))).toList();
    }

    public boolean isDestroyedBlock(UUID blockId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE id = ? AND destroyed_at IS NOT NULL",
                Integer.class, blockId);
        return count != null && count > 0;
    }

    public boolean isDestroyedSlide(UUID slideId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE id = ? AND destroyed_at IS NOT NULL",
                Integer.class, slideId);
        return count != null && count > 0;
    }

    public void destroyBlock(UUID blockId, String reason, String batchReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                UPDATE pis_v2.block SET destroyed_at = ?, destroyed_by_ref = ?, destruction_reason = ?,
                    destruction_batch_reference = ? WHERE id = ? AND destroyed_at IS NULL
                """, Timestamp.from(now), actorRef, reason, batchReference, blockId);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.material_destruction
                    (id, block_id, slide_id, destroyed_at, destroyed_by_ref, reason, batch_reference)
                VALUES (?, ?, NULL, ?, ?, ?, ?)
                """, UUID.randomUUID(), blockId, Timestamp.from(now), actorRef, reason, batchReference);
    }

    public void destroySlide(UUID slideId, String reason, String batchReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                UPDATE pis_v2.slide SET destroyed_at = ?, destroyed_by_ref = ?, destruction_reason = ?,
                    destruction_batch_reference = ? WHERE id = ? AND destroyed_at IS NULL
                """, Timestamp.from(now), actorRef, reason, batchReference, slideId);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.material_destruction
                    (id, block_id, slide_id, destroyed_at, destroyed_by_ref, reason, batch_reference)
                VALUES (?, NULL, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), slideId, Timestamp.from(now), actorRef, reason, batchReference);
    }

    public List<CustodyMaterialRow> findCaseMaterials(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT material_kind, material_id, material_code, location_id, location_code, location_name,
                       loan_id, borrower_reference, destroyed_at
                FROM (
                    SELECT 'BLOCK' AS material_kind, b.id AS material_id, b.block_code AS material_code,
                           loc.id AS location_id, loc.location_code, loc.location_name,
                           (SELECT l.id FROM pis_v2.loan_item li JOIN pis_v2.loan l ON l.id = li.loan_id
                             WHERE li.block_id = b.id AND li.returned_at IS NULL AND l.status_code = 'BORROWED'
                             ORDER BY l.borrowed_at DESC LIMIT 1) AS loan_id,
                           (SELECT l.borrower_reference FROM pis_v2.loan_item li JOIN pis_v2.loan l ON l.id = li.loan_id
                             WHERE li.block_id = b.id AND li.returned_at IS NULL AND l.status_code = 'BORROWED'
                             ORDER BY l.borrowed_at DESC LIMIT 1) AS borrower_reference,
                           b.destroyed_at
                    FROM pis_v2.block b
                    LEFT JOIN pis_v2.block_archive_current current_archive ON current_archive.block_id = b.id
                    LEFT JOIN pis_v2.archive_location loc ON loc.id = current_archive.location_id
                    WHERE b.case_id = ? AND b.organization_reference = ? AND b.deleted_at IS NULL
                    UNION ALL
                    SELECT 'SLIDE' AS material_kind, sl.id AS material_id, sl.slide_code AS material_code,
                           loc.id AS location_id, loc.location_code, loc.location_name,
                           (SELECT l.id FROM pis_v2.loan_item li JOIN pis_v2.loan l ON l.id = li.loan_id
                             WHERE li.slide_id = sl.id AND li.returned_at IS NULL AND l.status_code = 'BORROWED'
                             ORDER BY l.borrowed_at DESC LIMIT 1) AS loan_id,
                           (SELECT l.borrower_reference FROM pis_v2.loan_item li JOIN pis_v2.loan l ON l.id = li.loan_id
                             WHERE li.slide_id = sl.id AND li.returned_at IS NULL AND l.status_code = 'BORROWED'
                             ORDER BY l.borrowed_at DESC LIMIT 1) AS borrower_reference,
                           sl.destroyed_at
                    FROM pis_v2.slide sl
                    LEFT JOIN pis_v2.slide_archive_current current_archive ON current_archive.slide_id = sl.id
                    LEFT JOIN pis_v2.archive_location loc ON loc.id = current_archive.location_id
                    WHERE sl.case_id = ? AND sl.organization_reference = ? AND sl.deleted_at IS NULL
                ) material
                ORDER BY material_kind, material_code
                """, (rs, rowNum) -> new CustodyMaterialRow(rs.getString("material_kind"),
                rs.getObject("material_id", UUID.class), rs.getString("material_code"),
                rs.getObject("location_id", UUID.class), rs.getString("location_code"),
                rs.getString("location_name"), rs.getObject("loan_id", UUID.class),
                rs.getString("borrower_reference"), instant(rs, "destroyed_at")), caseId, organizationReference,
                caseId, organizationReference);
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String key) {
        return jdbcTemplate.query("""
                SELECT payload_digest, result_entity_id FROM pis_v2.custody_command_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getString(1),
                rs.getObject(2, UUID.class))) : Optional.empty(), operationCode, key);
    }

    public boolean insertIdempotency(String operationCode, String key, String digest, UUID resultEntityId,
            String actorRef, Instant now) {
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.custody_command_idempotency
                    (id, operation_code, idempotency_key, payload_digest, result_entity_id, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), operationCode, key, digest, resultEntityId, Timestamp.from(now), actorRef) == 1;
    }

    public record IdempotencyResult(String payloadDigest, UUID resultEntityId) { }
    public record LoanRow(UUID loanId, String borrowerReference, String borrowerDepartment, String purpose,
            Instant borrowedAt, Instant expectedReturnAt, Instant returnedAt, String returnedByRef,
            List<LoanItemRow> items) { }
    public record LoanItemRow(String materialKind, UUID materialId, String materialCode, UUID caseId,
            String pathologyNo, Instant returnedAt) { }
    private record LoanBaseRow(UUID loanId, String borrowerReference, String borrowerDepartment, String purpose,
            Instant borrowedAt, Instant expectedReturnAt, Instant returnedAt, String returnedByRef) { }
    public record LocationRow(UUID locationId, UUID parentId, String locationCode, String locationName,
            String locationKindCode) { }
    public record CustodyMaterialRow(String materialKind, UUID materialId, String materialCode, UUID locationId,
            String locationCode, String locationName, UUID loanId, String borrowerReference, Instant destroyedAt) { }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}

package com.hanjisang.pis.v2.custody.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
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

    public void insertLoan(UUID loanId, String borrower, String purpose, String organizationReference,
            String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.loan
                    (id, borrower_reference, purpose, status_code, borrowed_at, borrowed_by_ref, organization_reference)
                VALUES (?, ?, ?, 'BORROWED', ?, ?, ?)
                """, loanId, borrower, purpose, Timestamp.from(now), actorRef, organizationReference);
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
}

package com.hanjisang.pis.v2.material.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.material.domain.Block;
import com.hanjisang.pis.v2.material.domain.Grossing;
import com.hanjisang.pis.v2.material.domain.PrintRule;
import com.hanjisang.pis.v2.material.domain.Slide;
import com.hanjisang.pis.v2.material.domain.SlideRule;

@Repository
public class JdbcV2MaterialRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2MaterialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String allocateGrossingNo(String organizationReference, UUID caseId) {
        jdbcTemplate.update("""
                MERGE INTO pis_v2.grossing_sequence AS target
                USING (VALUES (?, CAST(? AS UUID), ?)) AS incoming
                    (organization_reference, case_id, next_serial)
                ON target.organization_reference = incoming.organization_reference
                   AND target.case_id = incoming.case_id
                WHEN NOT MATCHED THEN INSERT (organization_reference, case_id, next_serial)
                    VALUES (incoming.organization_reference, incoming.case_id, incoming.next_serial)
                """, organizationReference, caseId, 1L);
        Long serial = jdbcTemplate.query("""
                SELECT next_serial FROM pis_v2.grossing_sequence
                WHERE organization_reference = ? AND case_id = ?
                FOR UPDATE
                """, rs -> rs.next() ? rs.getLong(1) : null, organizationReference, caseId);
        if (serial == null) {
            throw new IllegalStateException("无法分配取材业务编号");
        }
        int changed = jdbcTemplate.update("""
                UPDATE pis_v2.grossing_sequence SET next_serial = next_serial + 1
                WHERE organization_reference = ? AND case_id = ? AND next_serial = ?
                """, organizationReference, caseId, serial);
        if (changed != 1) {
            throw new IllegalStateException("取材业务编号并发更新失败");
        }
        return "G" + String.format("%03d", serial);
    }

    public Optional<UUID> findCaseBusinessTypeId(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT c.business_type_id
                FROM pis_v2.pathology_case c
                WHERE c.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), caseId,
                organizationReference);
    }

    public Optional<String> findCaseBusinessTypeCode(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT bt.business_type_code
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), caseId,
                organizationReference);
    }

    public void insertGrossing(Grossing grossing, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing
                    (id, case_id, grossing_no, source_type, source_reference_id, gross_description,
                     grossing_instruction, grossing_doctor_id, recorder_id, started_at, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, grossing.id(), grossing.caseId(), grossing.grossingNo(), grossing.sourceType(),
                grossing.sourceReferenceId(), grossing.grossDescription(), grossing.grossingInstruction(),
                grossing.grossingDoctorId(), grossing.recorderId(), Timestamp.from(grossing.startedAt()),
                grossing.concurrencyVersion(), organizationReference, Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef);
    }

    public Optional<Grossing> findGrossing(UUID grossingId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_no, source_type, source_reference_id, gross_description,
                       grossing_instruction, grossing_doctor_id, recorder_id, started_at, completed_at,
                       completed_by_ref, deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.grossing
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toGrossing(rs)) : Optional.empty(), grossingId,
                organizationReference);
    }

    public void lockGrossing(UUID grossingId, String organizationReference) {
        jdbcTemplate.query("""
                SELECT id FROM pis_v2.grossing
                WHERE id = ? AND organization_reference = ? AND deleted_at IS NULL
                FOR UPDATE
                """, rs -> null, grossingId, organizationReference);
    }

    public boolean saveGrossing(Grossing grossing, String organizationReference, long expectedVersion,
            String actorRef, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.grossing
                   SET gross_description = ?, grossing_instruction = ?, grossing_doctor_id = ?, recorder_id = ?,
                       completed_at = ?, completed_by_ref = ?, concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ? AND deleted_at IS NULL
                """, grossing.grossDescription(), grossing.grossingInstruction(), grossing.grossingDoctorId(),
                grossing.recorderId(), timestamp(grossing.completedAt()), grossing.completedBy(),
                grossing.concurrencyVersion(), Timestamp.from(now), actorRef, grossing.id(), organizationReference,
                expectedVersion) == 1;
    }

    public boolean reopenGrossing(Grossing grossing, String organizationReference, long expectedVersion,
            String actorRef, Instant now) {
        return saveGrossing(grossing, organizationReference, expectedVersion, actorRef, now);
    }

    public boolean insertGrossingSpecimen(UUID grossingId, UUID specimenId, int sequenceNo,
            String materialDescription) {
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing_specimen
                    (grossing_id, specimen_id, sequence_no, material_description, concurrency_version)
                VALUES (?, ?, ?, ?, 0)
                """, grossingId, specimenId, sequenceNo, materialDescription) == 1;
    }

    public boolean hasGrossingSpecimen(UUID grossingId, UUID specimenId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.grossing_specimen
                WHERE grossing_id = ? AND specimen_id = ? AND deleted_at IS NULL
                """, Integer.class, grossingId, specimenId) > 0;
    }

    public int nextGrossingSpecimenSequence(UUID grossingId) {
        Integer sequence = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM pis_v2.grossing_specimen
                WHERE grossing_id = ?
                """, Integer.class, grossingId);
        return sequence == null ? 1 : sequence;
    }

    public void insertBlock(Block block, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.block
                    (id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                     external_source_reference, concurrency_version, organization_reference, created_at,
                     created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, block.id(), block.caseId(), block.grossingId(), block.specimenId(), block.blockCode(),
                block.blockType(), block.externalSource(), block.externalSourceReference(),
                block.concurrencyVersion(), organizationReference, Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef);
    }

    public Optional<Block> findBlock(UUID blockId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                       external_source_reference, deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.block
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toBlock(rs)) : Optional.empty(), blockId, organizationReference);
    }

    public List<Block> findActiveBlocksByGrossing(UUID grossingId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                       external_source_reference, deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.block
                WHERE grossing_id = ? AND organization_reference = ? AND deleted_at IS NULL
                ORDER BY block_code, id
                """, (rs, rowNum) -> toBlock(rs), grossingId, organizationReference);
    }

    public Optional<UUID> findActiveBlockIdByCode(UUID caseId, String blockCode, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.block
                WHERE case_id = ? AND block_code = ? AND organization_reference = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), caseId,
                blockCode, organizationReference);
    }

    public boolean saveBlock(Block block, String organizationReference, long expectedVersion,
            String actorRef, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.block
                   SET block_code = ?, block_type = ?, concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ? AND deleted_at IS NULL
                """, block.blockCode(), block.blockType(), block.concurrencyVersion(), Timestamp.from(now), actorRef,
                block.id(), organizationReference, expectedVersion) == 1;
    }

    public boolean softDeleteBlock(UUID blockId, String organizationReference, long expectedVersion,
            String reason, String actorRef, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.block
                   SET deleted_at = ?, deleted_by_ref = ?, deletion_reason = ?, concurrency_version = ?,
                       updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ? AND deleted_at IS NULL
                """, Timestamp.from(now), actorRef, reason, expectedVersion + 1, Timestamp.from(now), actorRef,
                blockId, organizationReference, expectedVersion) == 1;
    }

    public List<SlideRule> findSlideRules(String organizationReference, UUID businessTypeId,
            String sourceContextType, String triggerCode) {
        return jdbcTemplate.query("""
                SELECT id, business_type_id, rule_code, source_context_type, trigger_code, slide_type,
                       stain_code, copies, active
                FROM pis_v2.slide_rule
                WHERE organization_reference = ? AND business_type_id = ? AND source_context_type = ?
                  AND trigger_code = ? AND active = TRUE
                ORDER BY rule_code
                """, (rs, rowNum) -> new SlideRule(rs.getObject("id", UUID.class),
                rs.getObject("business_type_id", UUID.class), rs.getString("rule_code"),
                rs.getString("source_context_type"), rs.getString("trigger_code"), rs.getString("slide_type"),
                rs.getString("stain_code"), rs.getInt("copies"), rs.getBoolean("active")), organizationReference,
                businessTypeId, sourceContextType, triggerCode);
    }

    public Optional<PrintRule> findPrintRule(String organizationReference, UUID businessTypeId,
            String entityKindCode, String triggerCode) {
        return jdbcTemplate.query("""
                SELECT id, business_type_id, entity_kind_code, trigger_code, printer_profile_code, active
                FROM pis_v2.print_rule
                WHERE organization_reference = ? AND entity_kind_code = ? AND trigger_code = ? AND active = TRUE
                  AND (business_type_id = ? OR business_type_id IS NULL)
                ORDER BY CASE WHEN business_type_id IS NULL THEN 1 ELSE 0 END
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(new PrintRule(rs.getObject("id", UUID.class),
                rs.getObject("business_type_id", UUID.class), rs.getString("entity_kind_code"),
                rs.getString("trigger_code"), rs.getString("printer_profile_code"), rs.getBoolean("active")))
                : Optional.empty(), organizationReference, entityKindCode, triggerCode, businessTypeId);
    }

    public boolean slideOutputExists(UUID blockId, String sourceContextType, String ruleCode, int occurrenceNo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.slide
                WHERE block_id = ? AND source_context_type = ? AND rule_code = ? AND occurrence_no = ?
                  AND deleted_at IS NULL
                """, Integer.class, blockId, sourceContextType, ruleCode, occurrenceNo) > 0;
    }

    public boolean slideOutputExists(UUID blockId, String sourceContextType, UUID sourceContextId,
            String ruleCode, int occurrenceNo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.slide
                WHERE block_id = ? AND source_context_type = ? AND source_context_id = ?
                  AND rule_code = ? AND occurrence_no = ? AND deleted_at IS NULL
                """, Integer.class, blockId, sourceContextType, sourceContextId, ruleCode, occurrenceNo) > 0;
    }

    public void insertSlide(Slide slide, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.slide
                    (id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                     source_context_id, rule_code, occurrence_no, required, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, slide.id(), slide.caseId(), slide.blockId(), slide.specimenId(), slide.slideCode(),
                slide.slideType(), slide.sourceContextType(), slide.sourceContextId(), slide.ruleCode(),
                slide.occurrenceNo(), slide.required(), slide.concurrencyVersion(), organizationReference,
                Timestamp.from(now), actorRef, Timestamp.from(now), actorRef);
    }

    public Optional<Slide> findSlide(UUID slideId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                       source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.slide
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toSlide(rs)) : Optional.empty(), slideId, organizationReference);
    }

    public List<Slide> findActiveSlidesByBlock(UUID blockId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                       source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.slide
                WHERE block_id = ? AND organization_reference = ? AND deleted_at IS NULL
                ORDER BY slide_code, id
                """, (rs, rowNum) -> toSlide(rs), blockId, organizationReference);
    }

    public List<Slide> findActiveSlidesByCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                       source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.slide
                WHERE case_id = ? AND organization_reference = ? AND deleted_at IS NULL
                ORDER BY slide_code, id
                """, (rs, rowNum) -> toSlide(rs), caseId, organizationReference);
    }

    public Optional<UUID> findActiveSlideIdByCode(UUID caseId, String slideCode, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.slide
                WHERE case_id = ? AND slide_code = ? AND organization_reference = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), caseId, slideCode,
                organizationReference);
    }

    public boolean saveSlide(Slide slide, String organizationReference, long expectedVersion,
            String actorRef, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.slide
                   SET slide_code = ?, completed_at = ?, completed_by_ref = ?, concurrency_version = ?,
                       updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ? AND deleted_at IS NULL
                """, slide.slideCode(), timestamp(slide.completedAt()), slide.completedBy(),
                slide.concurrencyVersion(), Timestamp.from(now), actorRef, slide.id(), organizationReference,
                expectedVersion) == 1;
    }

    public boolean softDeleteSlide(UUID slideId, String organizationReference, long expectedVersion,
            String reason, String actorRef, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.slide
                   SET deleted_at = ?, deleted_by_ref = ?, deletion_reason = ?, concurrency_version = ?,
                       updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ? AND deleted_at IS NULL
                """, Timestamp.from(now), actorRef, reason, expectedVersion + 1, Timestamp.from(now), actorRef,
                slideId, organizationReference, expectedVersion) == 1;
    }

    public Optional<UUID> findSpecimenIdByCase(UUID caseId, UUID specimenId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.specimen
                WHERE id = ? AND case_id = ? AND organization_reference = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), specimenId, caseId,
                organizationReference);
    }

    public List<MaterialTreeRow> findMaterialTree(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT material.specimen_id, material.specimen_no, material.specimen_code,
                       material.specimen_kind_code, material.block_id, material.block_code,
                       material.block_type, material.slide_id, material.slide_code, material.slide_type,
                       material.source_context_type, material.completed_at, material.completed_by_ref,
                       material.required, material.concurrency_version
                FROM (
                    SELECT s.id AS specimen_id, s.specimen_no, s.specimen_code, s.specimen_kind_code,
                           b.id AS block_id, b.block_code, b.block_type,
                           sl.id AS slide_id, sl.slide_code, sl.slide_type, sl.source_context_type,
                           sl.completed_at, sl.completed_by_ref, sl.required, sl.concurrency_version
                    FROM pis_v2.specimen s
                    JOIN pis_v2.block b ON b.specimen_id = s.id AND b.deleted_at IS NULL
                    LEFT JOIN pis_v2.slide sl ON sl.block_id = b.id AND sl.deleted_at IS NULL
                    WHERE s.case_id = ? AND s.organization_reference = ? AND s.deleted_at IS NULL
                    UNION ALL
                    SELECT s.id AS specimen_id, s.specimen_no, s.specimen_code, s.specimen_kind_code,
                           NULL AS block_id, NULL AS block_code, NULL AS block_type,
                           sl.id AS slide_id, sl.slide_code, sl.slide_type, sl.source_context_type,
                           sl.completed_at, sl.completed_by_ref, sl.required, sl.concurrency_version
                    FROM pis_v2.specimen s
                    LEFT JOIN pis_v2.slide sl
                        ON sl.specimen_id = s.id AND sl.block_id IS NULL AND sl.deleted_at IS NULL
                    WHERE s.case_id = ? AND s.organization_reference = ? AND s.deleted_at IS NULL
                ) material
                ORDER BY material.specimen_code, material.block_code, material.slide_code
                """, (rs, rowNum) -> new MaterialTreeRow(rs.getObject("specimen_id", UUID.class),
                rs.getString("specimen_no"), rs.getString("specimen_code"), rs.getString("specimen_kind_code"),
                rs.getObject("block_id", UUID.class), rs.getString("block_code"), rs.getString("block_type"),
                rs.getObject("slide_id", UUID.class), rs.getString("slide_code"), rs.getString("slide_type"),
                rs.getString("source_context_type"), instant(rs, "completed_at"), rs.getString("completed_by_ref"),
                rs.getObject("required", Boolean.class), rs.getLong("concurrency_version")), caseId,
                organizationReference, caseId, organizationReference);
    }

    public boolean insertMaterialIdempotency(String operationCode, String idempotencyKey, String payloadDigest,
            String resultKindCode, UUID resultEntityId, Integer resultCount, String actorRef, Instant now) {
        return jdbcTemplate.update("""
                MERGE INTO pis_v2.material_command_idempotency AS target
                USING (VALUES (?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS INTEGER), CAST(? AS TIMESTAMP WITH TIME ZONE), ?)) AS incoming
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code,
                     result_entity_id, result_count, created_at, created_by_ref)
                ON target.operation_code = incoming.operation_code
                   AND target.idempotency_key = incoming.idempotency_key
                WHEN NOT MATCHED THEN INSERT
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code,
                     result_entity_id, result_count, created_at, created_by_ref)
                VALUES (incoming.id, incoming.operation_code, incoming.idempotency_key, incoming.payload_digest,
                        incoming.result_kind_code, incoming.result_entity_id, incoming.result_count,
                        incoming.created_at, incoming.created_by_ref)
                """, UUID.randomUUID(), operationCode, idempotencyKey, payloadDigest, resultKindCode,
                resultEntityId, resultCount, Timestamp.from(now), actorRef) == 1;
    }

    public Optional<MaterialIdempotencyResult> findMaterialIdempotency(String operationCode, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT payload_digest, result_kind_code, result_entity_id, result_count
                FROM pis_v2.material_command_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new MaterialIdempotencyResult(rs.getString("payload_digest"),
                rs.getString("result_kind_code"), rs.getObject("result_entity_id", UUID.class),
                (Integer) rs.getObject("result_count"))) : Optional.empty(), operationCode, idempotencyKey);
    }

    public void updateMaterialIdempotencyResult(String operationCode, String idempotencyKey, Integer resultCount) {
        jdbcTemplate.update("""
                UPDATE pis_v2.material_command_idempotency
                   SET result_count = ?
                 WHERE operation_code = ? AND idempotency_key = ?
                """, resultCount, operationCode, idempotencyKey);
    }

    public void insertPrintLog(UUID caseId, String entityKindCode, UUID entityId, String businessCode,
            String printerProfileCode, String operatorRef, Instant requestedAt, PrintServiceResult result) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.print_log
                    (id, case_id, entity_kind_code, entity_id, business_code, printer_profile_code,
                     operator_ref, requested_at, result_code, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), caseId, entityKindCode, entityId, businessCode, printerProfileCode,
                operatorRef, Timestamp.from(requestedAt), result.resultCode(), result.failureReason());
    }

    public record MaterialIdempotencyResult(String payloadDigest, String resultKindCode, UUID resultEntityId,
            Integer resultCount) { }

    public record MaterialTreeRow(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            UUID blockId, String blockCode, String blockType, UUID slideId, String slideCode, String slideType,
            String sourceContextType, Instant completedAt, String completedByRef, Boolean required,
            long concurrencyVersion) { }

    public record PrintServiceResult(String resultCode, String failureReason) { }

    private Grossing toGrossing(java.sql.ResultSet rs) throws java.sql.SQLException {
        return Grossing.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("grossing_no"), rs.getString("source_type"), rs.getObject("source_reference_id", UUID.class),
                rs.getString("gross_description"), rs.getString("grossing_instruction"),
                rs.getString("grossing_doctor_id"), rs.getString("recorder_id"),
                rs.getTimestamp("started_at").toInstant(), instant(rs, "completed_at"),
                rs.getString("completed_by_ref"), instant(rs, "deleted_at"), rs.getString("deletion_reason"),
                rs.getLong("concurrency_version"));
    }

    private Block toBlock(java.sql.ResultSet rs) throws java.sql.SQLException {
        return Block.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("grossing_id", UUID.class), rs.getObject("specimen_id", UUID.class),
                rs.getString("block_code"), rs.getString("block_type"), rs.getBoolean("external_source_flag"),
                rs.getString("external_source_reference"), instant(rs, "deleted_at"),
                rs.getString("deletion_reason"), rs.getLong("concurrency_version"));
    }

    private Slide toSlide(java.sql.ResultSet rs) throws java.sql.SQLException {
        return Slide.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("block_id", UUID.class), rs.getObject("specimen_id", UUID.class),
                rs.getString("slide_code"), rs.getString("slide_type"), rs.getString("source_context_type"),
                rs.getObject("source_context_id", UUID.class), rs.getString("rule_code"), rs.getInt("occurrence_no"),
                rs.getBoolean("required"), instant(rs, "completed_at"), rs.getString("completed_by_ref"),
                instant(rs, "deleted_at"), rs.getString("deletion_reason"), rs.getLong("concurrency_version"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

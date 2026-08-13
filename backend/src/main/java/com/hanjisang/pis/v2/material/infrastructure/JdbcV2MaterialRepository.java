package com.hanjisang.pis.v2.material.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
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
        if (isPostgreSql()) {
            jdbcTemplate.update("""
                    INSERT INTO pis_v2.grossing_sequence (organization_reference, case_id, next_serial)
                    VALUES (?, ?, ?)
                    ON CONFLICT (organization_reference, case_id) DO NOTHING
                    """, organizationReference, caseId, 1L);
        } else {
            jdbcTemplate.update("""
                    MERGE INTO pis_v2.grossing_sequence AS target
                    USING (VALUES (?, CAST(? AS UUID), ?)) AS incoming
                        (organization_reference, case_id, next_serial)
                    ON target.organization_reference = incoming.organization_reference
                       AND target.case_id = incoming.case_id
                    WHEN NOT MATCHED THEN INSERT (organization_reference, case_id, next_serial)
                        VALUES (incoming.organization_reference, incoming.case_id, incoming.next_serial)
                    """, organizationReference, caseId, 1L);
        }
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

    private boolean isPostgreSql() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
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

    public boolean lockActiveCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.pathology_case
                WHERE id = ? AND organization_reference = ? AND lifecycle_state_code = 'ACTIVE'
                FOR UPDATE
                """, (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(),
                caseId, organizationReference);
    }

    public Optional<UUID> findCompletedInitialGrossingId(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.grossing
                WHERE case_id = ? AND organization_reference = ? AND source_type = 'INITIAL'
                  AND completed_at IS NOT NULL AND deleted_at IS NULL
                ORDER BY completed_at DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(),
                caseId, organizationReference);
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

    public Optional<Grossing> findLatestActiveGrossing(UUID caseId, String sourceType, UUID sourceReferenceId,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_no, source_type, source_reference_id, gross_description,
                       grossing_instruction, grossing_doctor_id, recorder_id, started_at, completed_at,
                       completed_by_ref, deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.grossing
                WHERE case_id = ? AND source_type = ? AND organization_reference = ? AND deleted_at IS NULL
                  AND ((CAST(? AS UUID) IS NULL AND source_reference_id IS NULL)
                       OR source_reference_id = CAST(? AS UUID))
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(toGrossing(rs)) : Optional.empty(), caseId, sourceType,
                organizationReference, sourceReferenceId, sourceReferenceId);
    }

    public boolean isSupplementaryGrossingItem(UUID itemId, UUID caseId, String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pis_v2.technical_order_item i
                JOIN pis_v2.technical_order o ON o.id = i.order_id
                JOIN pis_v2.technical_order_target t ON t.item_id = i.id
                WHERE i.id = ? AND t.case_id = ? AND o.organization_reference = ?
                  AND i.project_code_snapshot = 'SUPPLEMENTARY-GROSSING'
                  AND o.cancelled_at IS NULL
                """, Integer.class, itemId, caseId, organizationReference);
        return count != null && count > 0;
    }

    public SupplementaryTargetScope supplementaryTargetScope(UUID itemId, UUID caseId,
            String organizationReference) {
        List<TechnicalTargetRow> targets = technicalTargets(itemId, caseId, organizationReference);
        boolean caseTarget = targets.stream().anyMatch(target -> "CASE".equals(target.targetType()));
        List<UUID> specimenIds = targets.stream().map(TechnicalTargetRow::specimenId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        return new SupplementaryTargetScope(caseTarget, specimenIds);
    }

    public void linkSupplementaryGrossingOutputs(UUID itemId, UUID grossingId, List<Block> blocks,
            String organizationReference, String actorRef, Instant now) {
        if (itemId == null) return;
        UUID caseId = blocks.isEmpty() ? findGrossing(grossingId, organizationReference)
                .map(Grossing::caseId).orElse(null) : blocks.get(0).caseId();
        if (caseId == null || !isSupplementaryGrossingItem(itemId, caseId, organizationReference)) return;
        List<TechnicalTargetRow> targets = technicalTargets(itemId, caseId, organizationReference);
        if (targets.isEmpty()) return;
        TechnicalTargetRow grossingTarget = targets.get(0);
        insertTechnicalOutput(itemId, grossingTarget.targetId(), "GROSSING", grossingId, 1, actorRef, now);

        Map<UUID, Integer> occurrences = new HashMap<>();
        List<Block> sortedBlocks = new ArrayList<>(blocks);
        sortedBlocks.sort(Comparator.comparing(Block::blockCode).thenComparing(Block::id));
        for (Block block : sortedBlocks) {
            TechnicalTargetRow target = targets.stream()
                    .filter(candidate -> block.specimenId() != null && block.specimenId().equals(candidate.specimenId()))
                    .findFirst()
                    .orElseGet(() -> targets.stream().filter(candidate -> "CASE".equals(candidate.targetType()))
                            .findFirst().orElse(grossingTarget));
            int occurrence = occurrences.merge(target.targetId(), 1, Integer::sum);
            insertTechnicalOutput(itemId, target.targetId(), "BLOCK", block.id(), occurrence, actorRef, now);
        }
    }

    private List<TechnicalTargetRow> technicalTargets(UUID itemId, UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT t.id, t.target_type,
                       CASE WHEN t.target_type = 'SPECIMEN' THEN t.specimen_target_id
                            WHEN t.target_type = 'BLOCK' THEN source_block.specimen_id
                            ELSE NULL END AS specimen_id
                FROM pis_v2.technical_order_target t
                JOIN pis_v2.technical_order_item i ON i.id = t.item_id
                JOIN pis_v2.technical_order o ON o.id = i.order_id
                LEFT JOIN pis_v2.block source_block ON source_block.id = t.block_target_id
                WHERE t.item_id = ? AND t.case_id = ? AND o.organization_reference = ?
                ORDER BY CASE t.target_type WHEN 'SPECIMEN' THEN 0 WHEN 'BLOCK' THEN 1 ELSE 2 END, t.id
                """, (rs, rowNum) -> new TechnicalTargetRow(rs.getObject("id", UUID.class),
                rs.getString("target_type"), rs.getObject("specimen_id", UUID.class)), itemId, caseId,
                organizationReference);
    }

    private void insertTechnicalOutput(UUID itemId, UUID targetId, String kind, UUID outputId, int occurrence,
            String actorRef, Instant now) {
        String outputColumn = "GROSSING".equals(kind) ? "grossing_output_id" : "block_output_id";
        jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order_output
                    (id, item_id, target_id, output_kind, %s, occurrence_no, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """.formatted(outputColumn), UUID.randomUUID(), itemId, targetId, kind, outputId, occurrence,
                Timestamp.from(now), actorRef);
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

    public boolean updateGrossingSpecimenDescription(UUID grossingId, UUID specimenId, String materialDescription,
            long expectedVersion) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.grossing_specimen
                   SET material_description = ?, concurrency_version = concurrency_version + 1
                 WHERE grossing_id = ? AND specimen_id = ? AND deleted_at IS NULL
                   AND concurrency_version = ?
                """, materialDescription, grossingId, specimenId, expectedVersion) == 1;
    }

    public void insertGrossingSpecimenCorrection(UUID grossingId, UUID specimenId, String priorDescription,
            String correctedDescription, String reason, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing_specimen_correction_history
                    (id, grossing_id, specimen_id, prior_description, corrected_description, reason,
                     corrected_at, corrected_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), grossingId, specimenId, priorDescription, correctedDescription, reason,
                Timestamp.from(now), actorRef, organizationReference);
    }

    public List<GrossingSpecimenFact> findGrossingSpecimens(UUID grossingId) {
        return jdbcTemplate.query("""
                SELECT specimen_id, material_description, sequence_no, concurrency_version
                FROM pis_v2.grossing_specimen
                WHERE grossing_id = ? AND deleted_at IS NULL
                ORDER BY sequence_no, specimen_id
                """, (rs, rowNum) -> new GrossingSpecimenFact(rs.getObject("specimen_id", UUID.class),
                rs.getString("material_description"), rs.getInt("sequence_no"),
                rs.getLong("concurrency_version")), grossingId);
    }

    public void insertGrossingCorrection(UUID grossingId, String reason, String priorDescription,
            String correctedDescription, String priorInstruction, String correctedInstruction,
            String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing_correction_history
                    (id, grossing_id, reason, prior_gross_description, corrected_gross_description,
                     prior_instruction, corrected_instruction, corrected_at, corrected_by_ref,
                     organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), grossingId, reason, priorDescription, correctedDescription,
                priorInstruction, correctedInstruction, Timestamp.from(now), actorRef, organizationReference);
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
                     external_source_reference, sampling_description, quantity, note,
                     concurrency_version, organization_reference, created_at,
                     created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, block.id(), block.caseId(), block.grossingId(), block.specimenId(), block.blockCode(),
                block.blockType(), block.externalSource(), block.externalSourceReference(),
                block.samplingDescription(), block.quantity(), block.note(), block.concurrencyVersion(),
                organizationReference, Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef);
    }

    public Optional<Block> findBlock(UUID blockId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                       external_source_reference, sampling_description, quantity, note,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.block
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toBlock(rs)) : Optional.empty(), blockId, organizationReference);
    }

    public List<Block> findActiveBlocksByGrossing(UUID grossingId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                       external_source_reference, sampling_description, quantity, note,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.block
                WHERE grossing_id = ? AND organization_reference = ? AND deleted_at IS NULL
                ORDER BY block_code, id
                """, (rs, rowNum) -> toBlock(rs), grossingId, organizationReference);
    }

    public List<Block> findActiveInitialBlocksByCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT b.id, b.case_id, b.grossing_id, b.specimen_id, b.block_code, b.block_type,
                       b.external_source_flag, b.external_source_reference, b.sampling_description,
                       b.quantity, b.note, b.deleted_at, b.deletion_reason, b.concurrency_version
                FROM pis_v2.block b
                JOIN pis_v2.grossing g ON g.id = b.grossing_id
                WHERE b.case_id = ? AND b.organization_reference = ? AND b.deleted_at IS NULL
                  AND g.source_type = 'INITIAL' AND g.completed_at IS NOT NULL AND g.deleted_at IS NULL
                ORDER BY b.block_code, b.id
                """, (rs, rowNum) -> toBlock(rs), caseId, organizationReference);
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
                   SET block_code = ?, block_type = ?, sampling_description = ?, note = ?,
                       concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ? AND deleted_at IS NULL
                """, block.blockCode(), block.blockType(), block.samplingDescription(), block.note(),
                block.concurrencyVersion(), Timestamp.from(now), actorRef,
                block.id(), organizationReference, expectedVersion) == 1;
    }

    public void insertBlockCodeHistory(UUID blockId, String oldCode, String newCode, String reason,
            String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.block_code_history
                    (id, block_id, old_block_code, new_block_code, reason, changed_at,
                     changed_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), blockId, oldCode, newCode, reason, Timestamp.from(now), actorRef,
                organizationReference);
    }

    public int activeSlideCountForBlock(UUID blockId, String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.slide
                WHERE block_id = ? AND organization_reference = ? AND deleted_at IS NULL
                """, Integer.class, blockId, organizationReference);
        return count == null ? 0 : count;
    }

    public BlockVerificationPolicy findBlockVerificationPolicy(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT COALESCE(p.verification_required, FALSE) AS verification_required,
                       COALESCE(p.dual_check_required, FALSE) AS dual_check_required,
                       COALESCE(p.same_user_allowed, TRUE) AS same_user_allowed
                FROM pis_v2.pathology_case c
                LEFT JOIN pis_v2.block_verification_policy p
                  ON p.business_type_id = c.business_type_id AND p.organization_reference = ?
                WHERE c.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? new BlockVerificationPolicy(rs.getBoolean("verification_required"),
                        rs.getBoolean("dual_check_required"), rs.getBoolean("same_user_allowed"))
                        : new BlockVerificationPolicy(false, false, true),
                organizationReference, caseId, organizationReference);
    }

    public void insertBlockVerification(UUID blockId, String resultCode, String verifiedCode,
            UUID verifiedSpecimenId, int verifiedQuantity, String reason, String organizationReference,
            String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.block_verification
                    (id, block_id, verification_result_code, verified_code, verified_specimen_id,
                     verified_quantity, reason, verified_at, verified_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), blockId, resultCode, verifiedCode, verifiedSpecimenId,
                verifiedQuantity, reason, Timestamp.from(now), actorRef, organizationReference);
    }

    public Optional<BlockVerificationFact> latestBlockVerification(UUID blockId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT verification_result_code, verified_code, verified_specimen_id, verified_quantity,
                       reason, verified_at, verified_by_ref
                FROM pis_v2.block_verification
                WHERE block_id = ? AND organization_reference = ?
                ORDER BY verified_at DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(new BlockVerificationFact(
                        rs.getString("verification_result_code"), rs.getString("verified_code"),
                        rs.getObject("verified_specimen_id", UUID.class), rs.getInt("verified_quantity"),
                        rs.getString("reason"), rs.getTimestamp("verified_at").toInstant(),
                        rs.getString("verified_by_ref"))) : Optional.empty(), blockId, organizationReference);
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

    public boolean slideOutputExistsForSpecimen(UUID specimenId, String sourceContextType, String ruleCode,
            int occurrenceNo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.slide
                WHERE specimen_id = ? AND source_context_type = ? AND rule_code = ? AND occurrence_no = ?
                  AND deleted_at IS NULL
                """, Integer.class, specimenId, sourceContextType, ruleCode, occurrenceNo) > 0;
    }

    public boolean slideOutputExistsForSpecimen(UUID specimenId, String sourceContextType, UUID sourceContextId,
            String ruleCode, int occurrenceNo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.slide
                WHERE specimen_id = ? AND source_context_type = ? AND source_context_id = ?
                  AND rule_code = ? AND occurrence_no = ? AND deleted_at IS NULL
                """, Integer.class, specimenId, sourceContextType, sourceContextId, ruleCode, occurrenceNo) > 0;
    }

    public void insertSlide(Slide slide, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.slide
                    (id, case_id, block_id, specimen_id, slide_code, slide_type, stain_code, source_context_type,
                     source_context_id, rule_code, occurrence_no, required, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, slide.id(), slide.caseId(), slide.blockId(), slide.specimenId(), slide.slideCode(),
                slide.slideType(), slide.stainCode(), slide.sourceContextType(), slide.sourceContextId(), slide.ruleCode(),
                slide.occurrenceNo(), slide.required(), slide.concurrencyVersion(), organizationReference,
                Timestamp.from(now), actorRef, Timestamp.from(now), actorRef);
    }

    public int nextSlideOccurrence(UUID blockId, String sourceContextType, String organizationReference) {
        Integer occurrence = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(occurrence_no), 0) + 1
                FROM pis_v2.slide
                WHERE block_id = ? AND source_context_type = ? AND organization_reference = ?
                """, Integer.class, blockId, sourceContextType, organizationReference);
        return occurrence == null ? 1 : occurrence;
    }

    public int nextSpecimenSlideOccurrence(UUID specimenId, String sourceContextType, String organizationReference) {
        Integer occurrence = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(occurrence_no), 0) + 1
                FROM pis_v2.slide
                WHERE specimen_id = ? AND source_context_type = ? AND organization_reference = ?
                """, Integer.class, specimenId, sourceContextType, organizationReference);
        return occurrence == null ? 1 : occurrence;
    }

    public Optional<Slide> findSlide(UUID slideId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, block_id, specimen_id, slide_code, slide_type, stain_code, source_context_type,
                       source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.slide
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toSlide(rs)) : Optional.empty(), slideId, organizationReference);
    }

    public List<Slide> findActiveSlidesByBlock(UUID blockId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, block_id, specimen_id, slide_code, slide_type, stain_code, source_context_type,
                       source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                       deleted_at, deletion_reason, concurrency_version
                FROM pis_v2.slide
                WHERE block_id = ? AND organization_reference = ? AND deleted_at IS NULL
                ORDER BY slide_code, id
                """, (rs, rowNum) -> toSlide(rs), blockId, organizationReference);
    }

    public List<Slide> findActiveSlidesByCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, block_id, specimen_id, slide_code, slide_type, stain_code, source_context_type,
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

    public Optional<MaterialLocateRow> locateMaterial(UUID caseId, String barcode, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT located.material_kind, located.material_id, located.case_id, located.business_code
                FROM (
                    SELECT 'BLOCK' AS material_kind, b.id AS material_id, b.case_id, b.block_code AS business_code
                    FROM pis_v2.block b
                    WHERE UPPER(b.block_code) = UPPER(?) AND b.organization_reference = ? AND b.deleted_at IS NULL
                    UNION ALL
                    SELECT 'SLIDE' AS material_kind, sl.id AS material_id, sl.case_id,
                           sl.slide_code AS business_code
                    FROM pis_v2.slide sl
                    WHERE UPPER(sl.slide_code) = UPPER(?) AND sl.organization_reference = ? AND sl.deleted_at IS NULL
                ) located
                ORDER BY CASE WHEN located.case_id = ? THEN 0 ELSE 1 END, located.material_kind
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(new MaterialLocateRow(rs.getString("material_kind"),
                rs.getObject("material_id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("business_code"))) : Optional.empty(), barcode, organizationReference,
                barcode, organizationReference, caseId);
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

    public void insertSlideCodeHistory(UUID slideId, String oldCode, String newCode, String reason,
            String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.slide_code_history
                    (id, slide_id, old_slide_code, new_slide_code, reason, changed_at,
                     changed_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), slideId, oldCode, newCode, reason, Timestamp.from(now), actorRef,
                organizationReference);
    }

    public void insertSlideCompletionCorrection(UUID slideId, Instant priorCompletedAt,
            String priorCompletedBy, String reason, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.slide_completion_correction
                    (id, slide_id, prior_completed_at, prior_completed_by_ref, reason,
                     corrected_at, corrected_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), slideId, Timestamp.from(priorCompletedAt), priorCompletedBy, reason,
                Timestamp.from(now), actorRef, organizationReference);
    }

    public void resolveReworkSourceExceptions(UUID replacementSlideId, String organizationReference,
            String actorRef, Instant completedAt) {
        jdbcTemplate.update("""
                UPDATE pis_v2.material_process_fact fact
                   SET exception_resolved_at = ?, exception_resolved_by_ref = ?,
                       exception_resolution_note = '返工替代玻片已完成', updated_at = ?,
                       concurrency_version = concurrency_version + 1
                 WHERE fact.slide_id IN (
                       SELECT rework.original_slide_id
                         FROM pis_v2.material_rework rework
                        WHERE rework.replacement_slide_id = ?
                          AND rework.organization_reference = ?
                          AND rework.status_code = 'COMPLETED')
                   AND fact.organization_reference = ? AND fact.exception_code IS NOT NULL
                   AND fact.exception_resolved_at IS NULL
                """, Timestamp.from(completedAt), actorRef, Timestamp.from(completedAt), replacementSlideId,
                organizationReference, organizationReference);
    }

    public boolean hasSlideDownstreamDependency(UUID slideId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM pis_v2.digital_slide WHERE slide_id = ?)
                  + (SELECT COUNT(*) FROM pis_v2.technical_order_target WHERE slide_target_id = ?)
                  + (SELECT COUNT(*) FROM pis_v2.technical_order_output WHERE slide_output_id = ?)
                  + (SELECT COUNT(*) FROM pis_v2.slide_archive_current WHERE slide_id = ?)
                  + (SELECT COUNT(*) FROM pis_v2.loan_item WHERE slide_id = ?)
                """, Integer.class, slideId, slideId, slideId, slideId, slideId);
        return count != null && count > 0;
    }

    public List<SlideCodeHistoryRow> findSlideCodeHistory(UUID slideId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT old_slide_code, new_slide_code, reason, changed_at, changed_by_ref
                FROM pis_v2.slide_code_history
                WHERE slide_id = ? AND organization_reference = ?
                ORDER BY changed_at, id
                """, (rs, rowNum) -> new SlideCodeHistoryRow(rs.getString("old_slide_code"),
                rs.getString("new_slide_code"), rs.getString("reason"),
                rs.getTimestamp("changed_at").toInstant(), rs.getString("changed_by_ref")),
                slideId, organizationReference);
    }

    public Optional<UUID> findSpecimenIdByCase(UUID caseId, UUID specimenId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.specimen
                WHERE id = ? AND case_id = ? AND organization_reference = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), specimenId, caseId,
                organizationReference);
    }

    public List<UUID> findFrozenRoundSpecimenIds(UUID roundId, UUID caseId, String organizationReference) {
        return jdbcTemplate.queryForList("""
                SELECT frs.specimen_id
                  FROM pis_v2.frozen_round_specimen frs
                  JOIN pis_v2.frozen_round fr ON fr.id = frs.frozen_round_id
                  JOIN pis_v2.specimen s ON s.id = frs.specimen_id
                 WHERE frs.frozen_round_id = ?
                   AND fr.case_id = ?
                   AND fr.organization_reference = ?
                   AND s.case_id = fr.case_id
                   AND s.deleted_at IS NULL
                 ORDER BY frs.sequence_no
                """, UUID.class, roundId, caseId, organizationReference);
    }

    public Optional<FrozenRoundScope> frozenRoundScope(UUID roundId, UUID caseId,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT fr.round_no, fr.status_code, c.lifecycle_state_code, bt.modality_code
                  FROM pis_v2.frozen_round fr
                  JOIN pis_v2.pathology_case c ON c.id = fr.case_id
                  JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                 WHERE fr.id = ? AND fr.case_id = ?
                   AND fr.organization_reference = ? AND c.organization_reference = ?
                   AND bt.modality_code = 'FROZEN'
                """, rs -> rs.next() ? Optional.of(new FrozenRoundScope(rs.getInt("round_no"),
                        rs.getString("status_code"), rs.getString("lifecycle_state_code"),
                        rs.getString("modality_code"))) : Optional.empty(),
                roundId, caseId, organizationReference, organizationReference);
    }

    public boolean isSpecimenInFrozenRound(UUID roundId, UUID specimenId, String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM pis_v2.frozen_round_specimen frs
                  JOIN pis_v2.frozen_round fr ON fr.id = frs.frozen_round_id
                 WHERE frs.frozen_round_id = ?
                   AND frs.specimen_id = ?
                   AND fr.organization_reference = ?
                """, Integer.class, roundId, specimenId, organizationReference);
        return count != null && count == 1;
    }

    public List<MaterialTreeRow> findMaterialTree(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT material.specimen_id, material.specimen_no, material.specimen_code,
                       material.specimen_name, material.specimen_kind_code, material.creation_source_code,
                       material.collection_site, material.collection_method_code, material.description, material.source_specimen_code,
                       material.preparation_method_code, material.specimen_concurrency_version,
                       material.gross_material_description, material.gross_specimen_version,
                       material.block_id, material.block_code, material.block_type,
                       material.sampling_description, material.block_note,
                       material.block_concurrency_version, material.block_print_count, material.slide_id,
                       material.slide_code, material.slide_type, material.stain_code,
                       material.source_context_type, material.completed_at, material.completed_by_ref,
                       material.required, material.concurrency_version, material.slide_print_count
                FROM (
                    SELECT s.id AS specimen_id, s.specimen_no, s.specimen_code, s.specimen_name,
                           s.specimen_kind_code, s.creation_source_code, s.collection_site, s.collection_method_code, s.description,
                           s.preparation_method_code, s.concurrency_version AS specimen_concurrency_version,
                           source.specimen_code AS source_specimen_code,
                           CAST(NULL AS VARCHAR) AS gross_material_description,
                           CAST(NULL AS BIGINT) AS gross_specimen_version,
                           b.id AS block_id, b.block_code, b.block_type,
                           b.sampling_description, b.note AS block_note,
                           b.concurrency_version AS block_concurrency_version,
                           (SELECT CAST(COUNT(*) AS INTEGER) FROM pis_v2.print_log pl
                             WHERE pl.entity_kind_code = 'BLOCK' AND pl.entity_id = b.id) AS block_print_count,
                           sl.id AS slide_id, sl.slide_code, sl.slide_type, sl.stain_code, sl.source_context_type,
                            sl.completed_at, sl.completed_by_ref, sl.required, sl.concurrency_version,
                            (SELECT CAST(COUNT(*) AS INTEGER) FROM pis_v2.print_log pl
                              WHERE pl.entity_kind_code = 'SLIDE' AND pl.entity_id = sl.id) AS slide_print_count
                    FROM pis_v2.specimen s
                    LEFT JOIN pis_v2.specimen_split split ON split.child_specimen_id = s.id
                    LEFT JOIN pis_v2.specimen source ON source.id = split.source_specimen_id
                    JOIN pis_v2.block b ON b.specimen_id = s.id AND b.deleted_at IS NULL
                    LEFT JOIN pis_v2.slide sl ON sl.block_id = b.id AND sl.deleted_at IS NULL
                    WHERE s.case_id = ? AND s.organization_reference = ? AND s.deleted_at IS NULL
                    UNION ALL
                    SELECT s.id AS specimen_id, s.specimen_no, s.specimen_code, s.specimen_name,
                           s.specimen_kind_code, s.creation_source_code, s.collection_site, s.collection_method_code, s.description,
                           s.preparation_method_code, s.concurrency_version AS specimen_concurrency_version,
                           source.specimen_code AS source_specimen_code,
                           CAST(NULL AS VARCHAR) AS gross_material_description,
                           CAST(NULL AS BIGINT) AS gross_specimen_version,
                           NULL AS block_id, NULL AS block_code, NULL AS block_type,
                           NULL AS sampling_description, NULL AS block_note,
                           NULL AS block_concurrency_version,
                           0 AS block_print_count,
                           sl.id AS slide_id, sl.slide_code, sl.slide_type, sl.stain_code, sl.source_context_type,
                            sl.completed_at, sl.completed_by_ref, sl.required, sl.concurrency_version,
                            (SELECT CAST(COUNT(*) AS INTEGER) FROM pis_v2.print_log pl
                              WHERE pl.entity_kind_code = 'SLIDE' AND pl.entity_id = sl.id) AS slide_print_count
                    FROM pis_v2.specimen s
                    LEFT JOIN pis_v2.specimen_split split ON split.child_specimen_id = s.id
                    LEFT JOIN pis_v2.specimen source ON source.id = split.source_specimen_id
                    LEFT JOIN pis_v2.slide sl
                        ON sl.specimen_id = s.id AND sl.block_id IS NULL AND sl.deleted_at IS NULL
                    WHERE s.case_id = ? AND s.organization_reference = ? AND s.deleted_at IS NULL
                ) material
                ORDER BY material.specimen_code, material.block_code, material.slide_code
                """, (rs, rowNum) -> new MaterialTreeRow(rs.getObject("specimen_id", UUID.class),
                rs.getString("specimen_no"), rs.getString("specimen_code"), rs.getString("specimen_name"),
                rs.getString("specimen_kind_code"), rs.getString("creation_source_code"),
                rs.getString("collection_site"), rs.getString("collection_method_code"), rs.getString("description"),
                rs.getString("source_specimen_code"), rs.getString("preparation_method_code"),
                rs.getObject("specimen_concurrency_version", Long.class),
                rs.getString("gross_material_description"),
                rs.getObject("gross_specimen_version", Long.class),
                rs.getObject("block_id", UUID.class), rs.getString("block_code"), rs.getString("block_type"),
                rs.getString("sampling_description"), rs.getString("block_note"),
                rs.getObject("block_concurrency_version", Long.class), rs.getObject("block_print_count", Integer.class),
                rs.getObject("slide_id", UUID.class),
                rs.getString("slide_code"), rs.getString("slide_type"), rs.getString("stain_code"),
                rs.getString("source_context_type"), instant(rs, "completed_at"), rs.getString("completed_by_ref"),
                rs.getObject("required", Boolean.class), rs.getLong("concurrency_version"),
                rs.getInt("slide_print_count")), caseId,
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

    public record FrozenRoundScope(int roundNo, String statusCode, String lifecycleStateCode,
            String modalityCode) { }

    public record MaterialTreeRow(UUID specimenId, String specimenNo, String specimenCode, String specimenName,
            String specimenKindCode, String creationSourceCode, String collectionSite, String collectionMethodCode,
            String specimenDescription,
            String sourceSpecimenCode, String preparationMethodCode, Long specimenConcurrencyVersion,
            String grossMaterialDescription, Long grossSpecimenVersion,
            UUID blockId, String blockCode, String blockType, String samplingDescription, String blockNote,
            Long blockConcurrencyVersion, Integer blockPrintCount, UUID slideId,
            String slideCode, String slideType, String stainCode,
            String sourceContextType, Instant completedAt, String completedByRef, Boolean required,
            long concurrencyVersion, int slidePrintCount) { }

    public record SlideCodeHistoryRow(String oldCode, String newCode, String reason, Instant changedAt,
            String changedByRef) { }

    public record MaterialLocateRow(String materialKind, UUID materialId, UUID caseId, String businessCode) { }

    public record PrintServiceResult(String resultCode, String failureReason) { }

    public record GrossingSpecimenFact(UUID specimenId, String materialDescription, int sequenceNo,
            long concurrencyVersion) { }

    public record BlockVerificationPolicy(boolean verificationRequired, boolean dualCheckRequired,
            boolean sameUserAllowed) { }

    public record BlockVerificationFact(String resultCode, String verifiedCode, UUID verifiedSpecimenId,
            int verifiedQuantity, String reason, Instant verifiedAt, String verifiedByRef) { }

    public record SupplementaryTargetScope(boolean caseTarget, List<UUID> specimenIds) { }

    private record TechnicalTargetRow(UUID targetId, String targetType, UUID specimenId) { }

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
                rs.getString("block_code"), rs.getString("block_type"), rs.getString("sampling_description"),
                rs.getInt("quantity"), rs.getString("note"), rs.getBoolean("external_source_flag"),
                rs.getString("external_source_reference"), instant(rs, "deleted_at"),
                rs.getString("deletion_reason"), rs.getLong("concurrency_version"));
    }

    private Slide toSlide(java.sql.ResultSet rs) throws java.sql.SQLException {
        return Slide.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("block_id", UUID.class), rs.getObject("specimen_id", UUID.class),
                rs.getString("slide_code"), rs.getString("slide_type"), rs.getString("stain_code"),
                rs.getString("source_context_type"),
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

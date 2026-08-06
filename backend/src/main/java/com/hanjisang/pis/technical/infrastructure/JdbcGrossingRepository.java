package com.hanjisang.pis.technical.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.technical.domain.GrossingBatch;
import com.hanjisang.pis.technical.domain.TissueBlock;

@Repository
public class JdbcGrossingRepository {

    private final JdbcTemplate jdbc;

    public JdbcGrossingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SpecimenSource> eligibleSpecimen(UUID specimenId, String organizationReference) {
        return jdbc.query("""
                SELECT s.id, s.case_id, s.specimen_no, s.specimen_lifecycle_state_code,
                       s.organization_reference, s.collection_site_text, c.case_no
                  FROM pis.specimen s
                  JOIN pis.pathology_case c ON c.id = s.case_id
                 WHERE s.id = ? AND s.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new SpecimenSource(
                        rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("specimen_no"), rs.getString("case_no"),
                        rs.getString("specimen_lifecycle_state_code"), rs.getString("organization_reference"),
                        rs.getString("collection_site_text"))) : Optional.empty(), specimenId, organizationReference);
    }

    public Optional<IdempotentReference> findIdempotent(String operationCode, String key, String digest) {
        return jdbc.query("""
                SELECT result_object_id, payload_digest
                  FROM pis.p16_idempotency_key
                 WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> {
                    if (!rs.next()) return Optional.empty();
                    UUID resultId = rs.getObject("result_object_id", UUID.class);
                    String existingDigest = rs.getString("payload_digest");
                    if (!existingDigest.equals(digest)) {
                        throw new IdempotencyConflictException();
                    }
                    return Optional.of(new IdempotentReference(resultId));
                }, operationCode, key);
    }

    public void recordIdempotent(String operationCode, String key, String digest, UUID resultObjectId, String actor,
            Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO pis.p16_idempotency_key
                    (id, operation_code, idempotency_key, payload_digest, result_object_id, created_at, created_by_ref)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), operationCode, key, digest, resultObjectId, Timestamp.from(now), actor);
        } catch (DuplicateKeyException ignored) {
            // The unique key makes the first committed result authoritative.
        }
    }

    public BatchSnapshot createBatch(UUID specimenId, String batchNo, String organizationReference, String actor,
            Instant now) {
        SpecimenSource source = eligibleSpecimen(specimenId, organizationReference)
                .orElseThrow(() -> new IllegalArgumentException("SPECIMEN_NOT_FOUND"));
        UUID batchId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.grossing_batch
                (id, batch_no, organization_reference, task_state_code, batch_state_code,
                 record_version_no, concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P16-TASK-UNASSIGNED', ?, 1, 0, ?, ?)
                """, batchId, batchNo, organizationReference, GrossingBatch.PLANNED, Timestamp.from(now), actor);
        jdbc.update("""
                INSERT INTO pis.grossing_batch_specimen
                (id, batch_id, specimen_id, case_id, identity_verified_at, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), batchId, source.specimenId(), source.caseId(), Timestamp.from(now),
                Timestamp.from(now), actor);
        appendStateHistory(batchId, "P16-GROSSING-BATCH", "P16-SM-GROSSING-BATCH", "NONE", GrossingBatch.PLANNED,
                "P16-CMD-CREATE-BATCH", null, 0, now, actor, "batch created");
        return batch(batchId, organizationReference).orElseThrow();
    }

    public Optional<BatchSnapshot> batch(UUID batchId, String organizationReference) {
        return jdbc.query("""
                SELECT id, batch_no, task_state_code, batch_state_code, assigned_actor_ref, actual_actor_ref,
                       concurrency_version, organization_reference, created_at
                  FROM pis.grossing_batch
                 WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new BatchSnapshot(
                        rs.getObject("id", UUID.class), rs.getString("batch_no"), rs.getString("task_state_code"),
                        rs.getString("batch_state_code"), rs.getString("assigned_actor_ref"),
                        rs.getString("actual_actor_ref"), rs.getLong("concurrency_version"),
                        rs.getString("organization_reference"))) : Optional.empty(), batchId, organizationReference);
    }

    public boolean batchContainsSpecimen(UUID batchId, UUID specimenId, String organizationReference) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis.grossing_batch_specimen bs
                 JOIN pis.grossing_batch b ON b.id = bs.batch_id
                 WHERE bs.batch_id = ? AND bs.specimen_id = ? AND b.organization_reference = ?
                """, Integer.class, batchId, specimenId, organizationReference);
        return count != null && count == 1;
    }

    public List<Map<String, Object>> queue(String organizationReference, String actorRef) {
        return jdbc.queryForList("""
                SELECT CAST(NULL AS UUID) AS batch_id, CAST(NULL AS VARCHAR(64)) AS batch_no,
                       'P16-GROSSING-PLANNED' AS batch_state_code, 'P16-TASK-UNASSIGNED' AS task_state_code,
                       CAST(NULL AS VARCHAR(128)) AS assigned_actor_ref, CAST(NULL AS VARCHAR(128)) AS actual_actor_ref,
                       CAST(0 AS BIGINT) AS concurrency_version, s.created_at,
                       s.id AS specimen_id, s.specimen_no, s.specimen_lifecycle_state_code,
                       c.case_no, s.collection_site_text
                  FROM pis.specimen s
                  JOIN pis.pathology_case c ON c.id = s.case_id
                 WHERE s.organization_reference = ? AND s.specimen_lifecycle_state_code = 'P08-SM-003-ST-03'
                   AND NOT EXISTS (SELECT 1 FROM pis.grossing_batch_specimen bs WHERE bs.specimen_id = s.id)
                UNION ALL
                SELECT b.id AS batch_id, b.batch_no, b.batch_state_code, b.task_state_code,
                       b.assigned_actor_ref, b.actual_actor_ref, b.concurrency_version, b.created_at,
                       s.id AS specimen_id, s.specimen_no, s.specimen_lifecycle_state_code,
                       c.case_no, s.collection_site_text
                  FROM pis.grossing_batch b
                  JOIN pis.grossing_batch_specimen bs ON bs.batch_id = b.id
                  JOIN pis.specimen s ON s.id = bs.specimen_id
                  JOIN pis.pathology_case c ON c.id = bs.case_id
                 WHERE b.organization_reference = ?
                   AND (b.batch_state_code IN (?, ?, ?) OR b.assigned_actor_ref = ?)
                 ORDER BY created_at, batch_id
                 LIMIT 200
                """, organizationReference, organizationReference, GrossingBatch.PLANNED, GrossingBatch.ASSIGNED,
                GrossingBatch.PAUSED, actorRef);
    }

    public boolean appendSpecimen(UUID batchId, SpecimenSource source, String actor, Instant now) {
        try {
            return jdbc.update("""
                    INSERT INTO pis.grossing_batch_specimen
                    (id, batch_id, specimen_id, case_id, identity_verified_at, created_at, created_by_ref)
                    SELECT ?, ?, ?, ?, ?, ?, ?
                     WHERE EXISTS (SELECT 1 FROM pis.grossing_batch WHERE id = ? AND organization_reference = ?
                                   AND batch_state_code IN (?, ?))
                    """, UUID.randomUUID(), batchId, source.specimenId(), source.caseId(), Timestamp.from(now),
                    Timestamp.from(now), actor, batchId, source.organizationReference(), GrossingBatch.PLANNED,
                    GrossingBatch.ASSIGNED) == 1;
        } catch (DuplicateKeyException ignored) {
            return true;
        }
    }

    public Optional<UUID> sampleAssignment(UUID sampleId, String organizationReference) {
        return jdbc.query("""
                SELECT bs.block_id FROM pis.tissue_block_sample bs
                 JOIN pis.tissue_block b ON b.id = bs.block_id
                 WHERE bs.sample_id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), sampleId,
                organizationReference);
    }

    public boolean takeover(UUID batchId, long expectedVersion, String actor, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis.grossing_batch
                   SET assigned_actor_ref = ?, actual_actor_ref = ?, task_state_code = 'P16-TASK-TAKEN-OVER',
                       batch_state_code = CASE WHEN batch_state_code = ? THEN ? ELSE batch_state_code END,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND assigned_actor_ref IS NULL AND batch_state_code IN (?, ?) AND concurrency_version = ?
                """, actor, actor, GrossingBatch.PLANNED, GrossingBatch.ASSIGNED, batchId, GrossingBatch.PLANNED,
                GrossingBatch.ASSIGNED, expectedVersion);
        if (changed == 1) {
            appendResponsibility(batchId, "P16-GROSSING-BATCH", "P14-TASK-002", actor, actor, now);
            appendStateHistory(batchId, "P16-GROSSING-BATCH", "P16-SM-GROSSING-BATCH", GrossingBatch.PLANNED,
                    GrossingBatch.ASSIGNED, "P16-CMD-TAKEOVER", expectedVersion, expectedVersion + 1, now, actor,
                    "task takeover");
        }
        return changed == 1;
    }

    public boolean transitionBatch(UUID batchId, String organizationReference, String actor, String expectedState,
            String targetState, long expectedVersion, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis.grossing_batch
                   SET batch_state_code = ?, record_version_no = record_version_no + 1,
                       concurrency_version = concurrency_version + 1,
                       started_at = CASE WHEN ? = ? THEN COALESCE(started_at, ?) ELSE started_at END,
                       paused_at = CASE WHEN ? = ? THEN ? ELSE paused_at END,
                       completed_at = CASE WHEN ? = ? THEN ? ELSE completed_at END,
                       handed_off_at = CASE WHEN ? = ? THEN ? ELSE handed_off_at END
                 WHERE id = ? AND organization_reference = ? AND batch_state_code = ?
                   AND assigned_actor_ref = ? AND concurrency_version = ?
                """, targetState, targetState, GrossingBatch.IN_PROGRESS, Timestamp.from(now),
                targetState, GrossingBatch.PAUSED, Timestamp.from(now), targetState, GrossingBatch.COMPLETED,
                Timestamp.from(now), targetState, GrossingBatch.HANDED_OFF, Timestamp.from(now), batchId,
                organizationReference, expectedState, actor, expectedVersion);
        if (changed == 1) {
            appendStateHistory(batchId, "P16-GROSSING-BATCH", "P16-SM-GROSSING-BATCH", expectedState, targetState,
                    "P16-CMD-" + targetState, expectedVersion, expectedVersion + 1, now, actor, "batch transition");
        }
        return changed == 1;
    }

    public boolean touchBatch(UUID batchId, String organizationReference, String actor, long expectedVersion) {
        return jdbc.update("""
                UPDATE pis.grossing_batch
                   SET record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND assigned_actor_ref = ? AND concurrency_version = ?
                """, batchId, organizationReference, actor, expectedVersion) == 1;
    }

    public Optional<RecordSnapshot> latestRecord(UUID batchId, UUID specimenId) {
        return jdbc.query("""
                SELECT id, record_version_no FROM pis.grossing_record
                 WHERE batch_id = ? AND specimen_id = ?
                 ORDER BY record_version_no DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(new RecordSnapshot(rs.getObject("id", UUID.class),
                        rs.getInt("record_version_no"))) : Optional.empty(), batchId, specimenId);
    }

    public RecordSnapshot insertGrossingRecord(UUID batchId, UUID specimenId, int version, boolean identityVerified,
            boolean patientIdentityVerified, String appearance, String description, double quantity, String unit,
            String correctionReason, String reviewActor, String actor, Instant now) {
        UUID recordId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.grossing_record
                (id, batch_id, specimen_id, record_version_no, identity_verified, patient_identity_verified,
                 gross_appearance_text, quantity_value, quantity_unit_code, gross_description_text,
                 correction_reason, review_actor_ref, occurred_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, recordId, batchId, specimenId, version, identityVerified, patientIdentityVerified, appearance,
                quantity, unit, description, correctionReason, reviewActor, Timestamp.from(now), actor);
        return new RecordSnapshot(recordId, version);
    }

    public SampleSnapshot insertSample(UUID batchId, UUID recordId, UUID specimenId, String sampleNo, String sourceSite,
            String description, double quantity, String unit, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.tissue_sample
                (id, sample_no, batch_id, grossing_record_id, specimen_id, source_site_text,
                 sample_description_text, quantity_value, quantity_unit_code, sample_state_code,
                 concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'P16-SAMPLE-UNASSIGNED', 0, ?, ?)
                """, id, sampleNo, batchId, recordId, specimenId, sourceSite, description, quantity, unit,
                Timestamp.from(now), actor);
        return new SampleSnapshot(id, sampleNo, specimenId, "P16-SAMPLE-UNASSIGNED", 0);
    }

    public BlockSnapshot insertBlock(UUID batchId, UUID specimenId, UUID caseId, String blockNo, String kind,
            String sourceKind, String tissueBoxNo, String organizationReference, String actor, Instant now) {
        UUID blockId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.tissue_block
                (id, case_id, specimen_id, batch_id, block_no, block_kind_code, source_material_kind_code,
                 block_lifecycle_state_code, physical_formed_at, record_version_no, concurrency_version,
                 organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1, 0, ?, ?, ?)
                """, blockId, caseId, specimenId, batchId, blockNo, kind, sourceKind, TissueBlock.PLANNED,
                organizationReference, Timestamp.from(now), actor);
        UUID boxId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.tissue_box_identity
                (id, block_id, tissue_box_no, box_state_code, organization_reference, assigned_at, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P16-BOX-PLANNED', ?, ?, ?, ?)
                """, boxId, blockId, tissueBoxNo, organizationReference, Timestamp.from(now), Timestamp.from(now), actor);
        jdbc.update("UPDATE pis.tissue_block SET tissue_box_identity_id = ? WHERE id = ?", boxId, blockId);
        appendStateHistory(blockId, "OBJ-004", "P08-SM-004", "NONE", TissueBlock.PLANNED, "P16-CMD-CREATE-BLOCK",
                null, 0, now, actor, "planned block created");
        return block(blockId, organizationReference).orElseThrow();
    }

    public Optional<BlockSnapshot> block(UUID blockId, String organizationReference) {
        return jdbc.query("""
                SELECT b.id, b.case_id, b.specimen_id, b.batch_id, b.block_no, b.block_lifecycle_state_code,
                       b.concurrency_version, s.specimen_no, c.case_no, tb.tissue_box_no
                  FROM pis.tissue_block b
                  JOIN pis.specimen s ON s.id = b.specimen_id
                  JOIN pis.pathology_case c ON c.id = b.case_id
                  JOIN pis.tissue_box_identity tb ON tb.block_id = b.id
                 WHERE b.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new BlockSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getObject("specimen_id", UUID.class),
                        rs.getObject("batch_id", UUID.class), rs.getString("block_no"),
                        rs.getString("block_lifecycle_state_code"), rs.getLong("concurrency_version"),
                        rs.getString("specimen_no"), rs.getString("case_no"), rs.getString("tissue_box_no")))
                        : Optional.empty(), blockId, organizationReference);
    }

    public boolean assignSample(UUID blockId, UUID sampleId, String organizationReference, String actor, Instant now) {
        Integer belongs = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis.tissue_block b JOIN pis.tissue_sample s ON s.batch_id = b.batch_id
                 WHERE b.id = ? AND s.id = ? AND b.organization_reference = ?
                """, Integer.class, blockId, sampleId, organizationReference);
        if (belongs == null || belongs != 1) return false;
        try {
            int changed = jdbc.update("""
                    INSERT INTO pis.tissue_block_sample
                    (id, block_id, sample_id, assigned_at, assigned_by_ref)
                    VALUES (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), blockId, sampleId, Timestamp.from(now), actor);
            if (changed == 1) {
                jdbc.update("UPDATE pis.tissue_sample SET sample_state_code = 'P16-SAMPLE-ASSIGNED', concurrency_version = concurrency_version + 1 WHERE id = ?", sampleId);
            }
            return changed == 1;
        } catch (DuplicateKeyException exception) {
            UUID current = jdbc.queryForObject("SELECT block_id FROM pis.tissue_block_sample WHERE sample_id = ?",
                    UUID.class, sampleId);
            if (blockId.equals(current)) return true;
            throw new IllegalStateException("SAMPLE_ALREADY_ASSIGNED");
        }
    }

    public LabelSnapshot generateLabel(UUID blockId, long targetVersion, int labelVersion, String snapshot,
            String barcodePayload, String organizationReference, String actor, Instant now) {
        UUID labelId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.label_identity
                (id, target_kind_code, target_object_id, target_version, label_version_no, label_state_code,
                 template_logic_version, display_snapshot_text, barcode_payload, organization_reference,
                 generated_at, generated_by_ref)
                VALUES (?, 'TISSUE_BLOCK', ?, ?, ?, 'P16-LABEL-GENERATED', 'P16-REFERENCE-TEMPLATE-1', ?, ?, ?, ?, ?)
                """, labelId, blockId, targetVersion, labelVersion, snapshot, barcodePayload, organizationReference,
                Timestamp.from(now), actor);
        return new LabelSnapshot(labelId, blockId, labelVersion, "P16-LABEL-GENERATED", snapshot, barcodePayload);
    }

    public Optional<LabelSnapshot> labelForTargetVersion(UUID blockId, long targetVersion,
            String organizationReference) {
        return jdbc.query("""
                SELECT id, target_object_id, label_version_no, label_state_code, display_snapshot_text, barcode_payload
                  FROM pis.label_identity
                 WHERE target_object_id = ? AND target_version = ? AND organization_reference = ?
                   AND label_state_code <> 'P16-LABEL-VOIDED'
                 ORDER BY label_version_no DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(new LabelSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("target_object_id", UUID.class), rs.getInt("label_version_no"),
                        rs.getString("label_state_code"), rs.getString("display_snapshot_text"),
                        rs.getString("barcode_payload"))) : Optional.empty(), blockId, targetVersion,
                organizationReference);
    }

    public void lockBlockForLabel(UUID blockId) {
        jdbc.queryForObject("SELECT id FROM pis.tissue_block WHERE id = ? FOR UPDATE", UUID.class, blockId);
    }

    public int nextLabelVersion(UUID blockId) {
        jdbc.queryForObject("SELECT id FROM pis.tissue_block WHERE id = ? FOR UPDATE", UUID.class, blockId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pis.label_identity WHERE target_object_id = ?",
                Integer.class, blockId);
        return (count == null ? 0 : count) + 1;
    }

    public Optional<LabelSnapshot> label(UUID labelId, String organizationReference) {
        return jdbc.query("""
                SELECT id, target_object_id, label_version_no, label_state_code, display_snapshot_text, barcode_payload
                  FROM pis.label_identity WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new LabelSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("target_object_id", UUID.class), rs.getInt("label_version_no"),
                        rs.getString("label_state_code"), rs.getString("display_snapshot_text"),
                        rs.getString("barcode_payload"))) : Optional.empty(), labelId, organizationReference);
    }

    public PrintSnapshot submitPrint(UUID labelId, String requestKey, String kind, UUID originalLabelId, String reason,
            String actor, Instant now) {
        Optional<PrintSnapshot> existing = printByKey(labelId, requestKey);
        if (existing.isPresent()) return existing.get();
        UUID requestId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.label_print_request
                (id, label_id, idempotency_key, request_kind_code, original_label_id, reason,
                 request_state_code, requested_at, requested_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, 'P16-PRINT-SUBMITTED', ?, ?)
                """, requestId, labelId, requestKey, kind, originalLabelId, reason, Timestamp.from(now), actor);
        UUID attemptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.label_print_attempt
                (id, print_request_id, attempt_no, attempt_state_code, adapter_outcome_code,
                 result_note, attempted_at, attempted_by_ref)
                VALUES (?, ?, 1, 'P16-PRINT-SUBMITTED', 'REFERENCE_SUBMITTED',
                        'physical printer confirmation unavailable', ?, ?)
                """, attemptId, requestId, Timestamp.from(now), actor);
        return new PrintSnapshot(requestId, attemptId, labelId, "P16-PRINT-SUBMITTED", "REFERENCE_SUBMITTED");
    }

    public Optional<PrintSnapshot> printByKey(UUID labelId, String requestKey) {
        return jdbc.query("""
                SELECT r.id AS request_id, a.id AS attempt_id, r.label_id, a.attempt_state_code, a.adapter_outcome_code
                  FROM pis.label_print_request r JOIN pis.label_print_attempt a ON a.print_request_id = r.id
                 WHERE r.label_id = ? AND r.idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new PrintSnapshot(rs.getObject("request_id", UUID.class),
                        rs.getObject("attempt_id", UUID.class), rs.getObject("label_id", UUID.class),
                        rs.getString("attempt_state_code"), rs.getString("adapter_outcome_code"))) : Optional.empty(),
                labelId, requestKey);
    }

    public boolean recordPrintResult(UUID requestId, String state, String outcome, String note, String actor,
            Instant now) {
        int changed = jdbc.update("""
                UPDATE pis.label_print_attempt SET attempt_state_code = ?, adapter_outcome_code = ?, result_note = ?,
                    attempted_at = ?, attempted_by_ref = ?
                 WHERE print_request_id = ? AND attempt_state_code = 'P16-PRINT-SUBMITTED'
                """, state, outcome, note, Timestamp.from(now), actor, requestId);
        if (changed == 1) {
            jdbc.update("UPDATE pis.label_print_request SET request_state_code = ? WHERE id = ?", state, requestId);
        }
        return changed == 1;
    }

    public boolean voidLabel(UUID labelId, String organizationReference, String actor, Instant now) {
        return jdbc.update("""
                UPDATE pis.label_identity SET label_state_code = 'P16-LABEL-VOIDED'
                 WHERE id = ? AND organization_reference = ? AND label_state_code <> 'P16-LABEL-VOIDED'
                """, labelId, organizationReference) == 1;
    }

    public CompletionCheck completionCheck(UUID batchId) {
        Integer records = jdbc.queryForObject("SELECT COUNT(*) FROM pis.grossing_record WHERE batch_id = ?", Integer.class,
                batchId);
        Integer samples = jdbc.queryForObject("SELECT COUNT(*) FROM pis.tissue_sample WHERE batch_id = ?", Integer.class,
                batchId);
        Integer blocks = jdbc.queryForObject("SELECT COUNT(*) FROM pis.tissue_block WHERE batch_id = ?", Integer.class,
                batchId);
        Integer unassigned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis.tissue_sample s
                 WHERE s.batch_id = ? AND NOT EXISTS (SELECT 1 FROM pis.tissue_block_sample bs WHERE bs.sample_id = s.id)
                """, Integer.class, batchId);
        Integer unlabelled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis.tissue_block b
                 WHERE b.batch_id = ? AND NOT EXISTS
                       (SELECT 1 FROM pis.label_identity l WHERE l.target_object_id = b.id AND l.label_state_code <> 'P16-LABEL-VOIDED')
                """, Integer.class, batchId);
        return new CompletionCheck(records == null ? 0 : records, samples == null ? 0 : samples,
                blocks == null ? 0 : blocks, unassigned == null ? 0 : unassigned, unlabelled == null ? 0 : unlabelled);
    }

    public int markBlocksGrossingRecorded(UUID batchId, String actor, Instant now) {
        return jdbc.update("""
                UPDATE pis.tissue_block SET block_lifecycle_state_code = ?, record_version_no = record_version_no + 1,
                    concurrency_version = concurrency_version + 1
                 WHERE batch_id = ? AND block_lifecycle_state_code = ?
                """, TissueBlock.GROSSING_RECORDED, batchId, TissueBlock.PLANNED);
    }

    public void appendStateHistory(UUID objectId, String objectKind, String stateMachine, String source, String target,
            String event, Long expectedVersion, long resultingVersion, Instant now, String actor, String reason) {
        jdbc.update("""
                INSERT INTO pis.state_transition_history
                (id, object_id, object_kind_code, state_machine_code, source_state_code, target_state_code,
                 transition_event_code, expected_version, resulting_version, occurred_at, recorded_by_ref, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), objectId, objectKind, stateMachine, source, target, event, expectedVersion,
                resultingVersion, Timestamp.from(now), actor, reason);
    }

    public void appendResponsibility(UUID objectId, String objectKind, String type, String responsible, String actual,
            Instant now) {
        jdbc.update("""
                INSERT INTO pis.operation_responsibility
                (id, object_id, object_kind_code, responsibility_type_code, responsible_actor_ref,
                 actual_actor_ref, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), objectId, objectKind, type, responsible, actual, Timestamp.from(now), actual);
    }

    public record SpecimenSource(UUID specimenId, UUID caseId, String specimenNo, String caseNo, String stateCode,
            String organizationReference, String collectionSite) { }
    public record IdempotentReference(UUID resultObjectId) { }
    public record BatchSnapshot(UUID id, String batchNo, String taskStateCode, String stateCode, String assignedActor,
            String actualActor, long concurrencyVersion, String organizationReference) { }
    public record RecordSnapshot(UUID id, int version) { }
    public record SampleSnapshot(UUID id, String sampleNo, UUID specimenId, String stateCode, long version) { }
    public record BlockSnapshot(UUID id, UUID caseId, UUID specimenId, UUID batchId, String blockNo, String stateCode,
            long version, String specimenNo, String caseNo, String tissueBoxNo) { }
    public record LabelSnapshot(UUID id, UUID targetObjectId, int version, String stateCode, String snapshot,
            String barcodePayload) { }
    public record PrintSnapshot(UUID requestId, UUID attemptId, UUID labelId, String stateCode, String outcome) { }
    public record CompletionCheck(int records, int samples, int blocks, int unassignedSamples, int unlabelledBlocks) {
        public boolean complete() {
            return records > 0 && samples > 0 && blocks > 0 && unassignedSamples == 0 && unlabelledBlocks == 0;
        }
    }

    public static final class IdempotencyConflictException extends RuntimeException { }
}

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

import com.hanjisang.pis.technical.domain.ActualBlockFormation;
import com.hanjisang.pis.technical.domain.EmbeddingTask;
import com.hanjisang.pis.technical.domain.ProcessingBatch;
import com.hanjisang.pis.technical.domain.ProcessingTask;

@Repository
public class JdbcProcessingRepository {

    private final JdbcTemplate jdbc;

    public JdbcProcessingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> processingQueue(String organizationReference) {
        return jdbc.queryForList("""
                SELECT b.id AS block_id, b.block_no, b.block_lifecycle_state_code, b.concurrency_version,
                       b.specimen_id, s.specimen_no, b.case_id, c.case_no, tb.tissue_box_no,
                       gb.id AS grossing_batch_id, gb.batch_no AS grossing_batch_no,
                       gb.handed_off_at, t.id AS processing_task_id, t.task_state_code
                  FROM pis.tissue_block b
                  JOIN pis.specimen s ON s.id = b.specimen_id
                  JOIN pis.pathology_case c ON c.id = b.case_id
                  JOIN pis.tissue_box_identity tb ON tb.block_id = b.id
                  JOIN pis.grossing_batch gb ON gb.id = b.batch_id
                  LEFT JOIN pis.p17_processing_task t ON t.tissue_block_id = b.id
                 WHERE b.organization_reference = ?
                   AND gb.organization_reference = ?
                   AND gb.batch_state_code = 'P16-GROSSING-HANDED-OFF'
                   AND b.block_lifecycle_state_code = 'P08-SM-004-ST-02'
                   AND b.physical_formed_at IS NULL
                 ORDER BY gb.handed_off_at NULLS LAST, b.created_at, b.id
                 LIMIT 200
                """, organizationReference, organizationReference);
    }

    public Optional<SourceBlock> sourceBlock(UUID blockId, String organizationReference) {
        return jdbc.query("""
                SELECT b.id, b.block_no, b.block_lifecycle_state_code, b.physical_formed_at,
                       b.concurrency_version, b.specimen_id, s.specimen_no, b.case_id, c.case_no,
                       tb.id AS tissue_box_id, tb.tissue_box_no, gb.id AS grossing_batch_id,
                       gb.batch_state_code, gb.handed_off_at, b.organization_reference
                  FROM pis.tissue_block b
                  JOIN pis.specimen s ON s.id = b.specimen_id
                  JOIN pis.pathology_case c ON c.id = b.case_id
                  JOIN pis.tissue_box_identity tb ON tb.block_id = b.id
                  JOIN pis.grossing_batch gb ON gb.id = b.batch_id
                 WHERE b.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new SourceBlock(
                rs.getObject("id", UUID.class), rs.getString("block_no"),
                rs.getString("block_lifecycle_state_code"), rs.getTimestamp("physical_formed_at"),
                rs.getLong("concurrency_version"), rs.getObject("specimen_id", UUID.class),
                rs.getString("specimen_no"), rs.getObject("case_id", UUID.class), rs.getString("case_no"),
                rs.getObject("tissue_box_id", UUID.class), rs.getString("tissue_box_no"),
                rs.getObject("grossing_batch_id", UUID.class), rs.getString("batch_state_code"),
                rs.getTimestamp("handed_off_at"), rs.getString("organization_reference"))) : Optional.empty(),
                blockId, organizationReference);
    }

    public Optional<ProcessingTaskSnapshot> task(UUID taskId, String organizationReference) {
        return jdbc.query("""
                SELECT id, task_no, tissue_block_id, organization_reference, task_state_code, assigned_actor_ref,
                       actual_actor_ref, assigned_at, completed_at, record_version_no, concurrency_version
                  FROM pis.p17_processing_task
                 WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingTaskSnapshot(rs.getObject("id", UUID.class),
                rs.getString("task_no"), rs.getObject("tissue_block_id", UUID.class), rs.getString("organization_reference"),
                rs.getString("task_state_code"),
                rs.getString("assigned_actor_ref"), rs.getString("actual_actor_ref"), rs.getTimestamp("assigned_at"),
                rs.getTimestamp("completed_at"), rs.getInt("record_version_no"), rs.getLong("concurrency_version")))
                : Optional.empty(), taskId, organizationReference);
    }

    public Optional<ProcessingTaskSnapshot> taskByBlock(UUID blockId, String organizationReference) {
        return jdbc.query("""
                SELECT id, task_no, tissue_block_id, organization_reference, task_state_code, assigned_actor_ref,
                       actual_actor_ref, assigned_at, completed_at, record_version_no, concurrency_version
                  FROM pis.p17_processing_task
                 WHERE tissue_block_id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingTaskSnapshot(rs.getObject("id", UUID.class),
                rs.getString("task_no"), rs.getObject("tissue_block_id", UUID.class), rs.getString("organization_reference"),
                rs.getString("task_state_code"),
                rs.getString("assigned_actor_ref"), rs.getString("actual_actor_ref"), rs.getTimestamp("assigned_at"),
                rs.getTimestamp("completed_at"), rs.getInt("record_version_no"), rs.getLong("concurrency_version")))
                : Optional.empty(), blockId, organizationReference);
    }

    public ProcessingTaskSnapshot createTask(SourceBlock block, String taskNo, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_task
                (id, task_no, tissue_block_id, organization_reference, task_state_code, record_version_no,
                 concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, 1, 0, ?, ?)
                """, id, taskNo, block.blockId(), block.organizationReference(), ProcessingTask.PLANNED,
                Timestamp.from(now), actor);
        return task(id, block.organizationReference()).orElseThrow();
    }

    public boolean takeoverTask(UUID taskId, String organizationReference, String actor, long expectedVersion,
            Instant now) {
        ProcessingTaskSnapshot before = task(taskId, organizationReference).orElseThrow();
        int changed = jdbc.update("""
                UPDATE pis.p17_processing_task
                   SET task_state_code = ?, assigned_actor_ref = ?, actual_actor_ref = ?, assigned_at = ?,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND task_state_code IN (?, ?)
                   AND concurrency_version = ?
                """, ProcessingTask.ASSIGNED, actor, actor, Timestamp.from(now), taskId, organizationReference,
                ProcessingTask.PLANNED, ProcessingTask.ASSIGNED, expectedVersion);
        if (changed == 1) {
            jdbc.update("""
                    INSERT INTO pis.p17_processing_task_assignment
                    (id, task_id, from_actor_ref, to_actor_ref, action_code, reason, occurred_at, recorded_by_ref)
                    VALUES (?, ?, ?, ?, 'P17-TASK-TAKEOVER', ?, ?, ?)
                    """, UUID.randomUUID(), taskId, before.assignedActorRef(), actor, "责任接管", Timestamp.from(now), actor);
        }
        return changed == 1;
    }

    public boolean transitionTask(UUID taskId, String organizationReference, String expectedState, String targetState,
            String actor, long expectedVersion, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis.p17_processing_task
                   SET task_state_code = ?, completed_at = CASE WHEN ? IN (?, ?, ?) THEN ? ELSE completed_at END,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND task_state_code = ?
                   AND concurrency_version = ?
                """, targetState, targetState, ProcessingTask.COMPLETED, ProcessingTask.PARTIAL,
                ProcessingTask.FAILED, Timestamp.from(now), taskId, organizationReference, expectedState,
                expectedVersion);
        if (changed == 1) appendStateHistory(taskId, "P17-PROCESSING-TASK", "P17-TASK", expectedState, targetState,
                "P17-CMD-TASK-STATE", expectedVersion, expectedVersion + 1, now, actor, "processing task state");
        return changed == 1;
    }

    public Optional<ProgramVersionSnapshot> programVersion(String programCode, String versionLabel) {
        return jdbc.query("""
                SELECT v.id, p.program_code, v.version_label, p.environment_code, v.version_state_code,
                       v.version_digest, v.parameter_reference
                  FROM pis.p17_processing_program_version v
                  JOIN pis.p17_processing_program p ON p.id = v.program_id
                 WHERE p.program_code = ? AND v.version_label = ?
                """, rs -> rs.next() ? Optional.of(new ProgramVersionSnapshot(rs.getObject("id", UUID.class),
                rs.getString("program_code"), rs.getString("version_label"), rs.getString("environment_code"),
                rs.getString("version_state_code"), rs.getString("version_digest"), rs.getString("parameter_reference")))
                : Optional.empty(), programCode, versionLabel);
    }

    public Optional<ProgramVersionSnapshot> programVersion(UUID versionId) {
        return jdbc.query("""
                SELECT v.id, p.program_code, v.version_label, p.environment_code, v.version_state_code,
                       v.version_digest, v.parameter_reference
                  FROM pis.p17_processing_program_version v
                  JOIN pis.p17_processing_program p ON p.id = v.program_id
                 WHERE v.id = ?
                """, rs -> rs.next() ? Optional.of(new ProgramVersionSnapshot(rs.getObject("id", UUID.class),
                rs.getString("program_code"), rs.getString("version_label"), rs.getString("environment_code"),
                rs.getString("version_state_code"), rs.getString("version_digest"), rs.getString("parameter_reference")))
                : Optional.empty(), versionId);
    }

    public Optional<ProcessingBatchSnapshot> batch(UUID batchId, String organizationReference) {
        return jdbc.query("""
                SELECT b.id, b.task_id, b.batch_no, b.program_version_id, b.program_version_snapshot,
                       b.execution_mode_code, b.device_identity_ref, b.batch_state_code, b.assigned_actor_ref,
                       b.started_at, b.completed_at, b.interrupted_at, b.failure_reason_code, b.record_version_no,
                       b.concurrency_version, b.organization_reference
                  FROM pis.p17_processing_batch b
                 WHERE b.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingBatchSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class), rs.getString("batch_no"),
                rs.getObject("program_version_id", UUID.class), rs.getString("program_version_snapshot"),
                rs.getString("execution_mode_code"), rs.getString("device_identity_ref"),
                rs.getString("batch_state_code"), rs.getString("assigned_actor_ref"), rs.getTimestamp("started_at"),
                rs.getTimestamp("completed_at"), rs.getTimestamp("interrupted_at"), rs.getString("failure_reason_code"),
                rs.getInt("record_version_no"), rs.getLong("concurrency_version"), rs.getString("organization_reference"))) : Optional.empty(), batchId,
                organizationReference);
    }

    public Optional<ProcessingBatchSnapshot> batchByTask(UUID taskId, String organizationReference) {
        return jdbc.query("""
                SELECT b.id, b.task_id, b.batch_no, b.program_version_id, b.program_version_snapshot,
                       b.execution_mode_code, b.device_identity_ref, b.batch_state_code, b.assigned_actor_ref,
                       b.started_at, b.completed_at, b.interrupted_at, b.failure_reason_code, b.record_version_no,
                       b.concurrency_version, b.organization_reference
                  FROM pis.p17_processing_batch b
                 WHERE b.task_id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingBatchSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class), rs.getString("batch_no"),
                rs.getObject("program_version_id", UUID.class), rs.getString("program_version_snapshot"),
                rs.getString("execution_mode_code"), rs.getString("device_identity_ref"),
                rs.getString("batch_state_code"), rs.getString("assigned_actor_ref"), rs.getTimestamp("started_at"),
                rs.getTimestamp("completed_at"), rs.getTimestamp("interrupted_at"), rs.getString("failure_reason_code"),
                rs.getInt("record_version_no"), rs.getLong("concurrency_version"), rs.getString("organization_reference"))) : Optional.empty(), taskId,
                organizationReference);
    }

    public ProcessingBatchSnapshot createBatch(ProcessingTaskSnapshot task, String batchNo,
            ProgramVersionSnapshot version, String executionMode, String deviceIdentity, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_batch
                (id, task_id, batch_no, program_version_id, program_version_snapshot, execution_mode_code,
                 device_identity_ref, organization_reference, batch_state_code, assigned_actor_ref,
                 record_version_no, concurrency_version, created_at, created_by_ref)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?
                """, id, task.id(), batchNo, version.id(), version.versionDigest(), executionMode, deviceIdentity,
                task.organizationReference(), ProcessingBatch.PLANNED, task.assignedActorRef(), Timestamp.from(now), actor);
        return batch(id, task.organizationReference()).orElseThrow();
    }

    public boolean updateBatchProgram(UUID batchId, String organizationReference, UUID programVersionId,
            String snapshot, long expectedVersion, String actor, Instant now) {
        return jdbc.update("""
                UPDATE pis.p17_processing_batch
                   SET program_version_id = ?, program_version_snapshot = ?, record_version_no = record_version_no + 1,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND batch_state_code IN (?, ?)
                   AND concurrency_version = ?
                """, programVersionId, snapshot, batchId, organizationReference, ProcessingBatch.PLANNED,
                ProcessingBatch.ASSIGNED, expectedVersion) == 1;
    }

    public boolean updateBatchDevice(UUID batchId, String organizationReference, String executionMode,
            String deviceIdentity, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p17_processing_batch
                   SET execution_mode_code = ?, device_identity_ref = ?, record_version_no = record_version_no + 1,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND batch_state_code IN (?, ?)
                   AND concurrency_version = ?
                """, executionMode, deviceIdentity, batchId, organizationReference, ProcessingBatch.PLANNED,
                ProcessingBatch.ASSIGNED, expectedVersion) == 1;
    }

    public Optional<ProcessingMemberSnapshot> member(UUID memberId, String organizationReference) {
        return jdbc.query("""
                SELECT m.id, m.batch_id, m.tissue_block_id, m.tissue_box_identity_id, m.planned_block_no_snapshot,
                       m.member_state_code, m.impact_state_code, m.can_enter_embedding, m.concurrency_version
                  FROM pis.p17_processing_batch_member m
                  JOIN pis.p17_processing_batch b ON b.id = m.batch_id
                 WHERE m.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingMemberSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class), rs.getObject("tissue_block_id", UUID.class),
                rs.getObject("tissue_box_identity_id", UUID.class), rs.getString("planned_block_no_snapshot"),
                rs.getString("member_state_code"), rs.getString("impact_state_code"), rs.getBoolean("can_enter_embedding"),
                rs.getLong("concurrency_version"))) : Optional.empty(), memberId, organizationReference);
    }

    public List<ProcessingMemberSnapshot> members(UUID batchId, String organizationReference) {
        return jdbc.query("""
                SELECT m.id, m.batch_id, m.tissue_block_id, m.tissue_box_identity_id, m.planned_block_no_snapshot,
                       m.member_state_code, m.impact_state_code, m.can_enter_embedding, m.concurrency_version
                  FROM pis.p17_processing_batch_member m
                  JOIN pis.p17_processing_batch b ON b.id = m.batch_id
                 WHERE m.batch_id = ? AND b.organization_reference = ? ORDER BY m.joined_at, m.id
                """, (rs, rowNum) -> new ProcessingMemberSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class), rs.getObject("tissue_block_id", UUID.class),
                rs.getObject("tissue_box_identity_id", UUID.class), rs.getString("planned_block_no_snapshot"),
                rs.getString("member_state_code"), rs.getString("impact_state_code"), rs.getBoolean("can_enter_embedding"),
                rs.getLong("concurrency_version")), batchId, organizationReference);
    }

    public Optional<ProcessingMemberSnapshot> memberInBatch(UUID batchId, UUID tissueBlockId,
            String organizationReference) {
        return jdbc.query("""
                SELECT m.id, m.batch_id, m.tissue_block_id, m.tissue_box_identity_id, m.planned_block_no_snapshot,
                       m.member_state_code, m.impact_state_code, m.can_enter_embedding, m.concurrency_version
                  FROM pis.p17_processing_batch_member m
                  JOIN pis.p17_processing_batch b ON b.id = m.batch_id
                 WHERE m.batch_id = ? AND m.tissue_block_id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingMemberSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class), rs.getObject("tissue_block_id", UUID.class),
                rs.getObject("tissue_box_identity_id", UUID.class), rs.getString("planned_block_no_snapshot"),
                rs.getString("member_state_code"), rs.getString("impact_state_code"), rs.getBoolean("can_enter_embedding"),
                rs.getLong("concurrency_version"))) : Optional.empty(), batchId, tissueBlockId,
                organizationReference);
    }

    public boolean activeMemberExists(UUID tissueBlockId, UUID excludingBatchId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis.p17_processing_batch_member m
                 JOIN pis.p17_processing_batch b ON b.id = m.batch_id
                 WHERE m.tissue_block_id = ? AND b.id <> ?
                   AND b.batch_state_code IN (?, ?, ?, ?, ?, ?)
                """, Integer.class, tissueBlockId, excludingBatchId, ProcessingBatch.PLANNED, ProcessingBatch.ASSIGNED,
                ProcessingBatch.IN_PROGRESS, ProcessingBatch.PAUSED, ProcessingBatch.INTERRUPTED, ProcessingBatch.PARTIAL);
        return count != null && count > 0;
    }

    public ProcessingMemberSnapshot addMember(ProcessingBatchSnapshot batch, SourceBlock block, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_batch_member
                (id, batch_id, tissue_block_id, tissue_box_identity_id, planned_block_no_snapshot, member_state_code,
                 impact_state_code, can_enter_embedding, joined_at, concurrency_version, created_by_ref)
                VALUES (?, ?, ?, ?, ?, 'P17-MEMBER-PLANNED', 'P17-IMPACT-NONE', FALSE, ?, 0, ?)
                """, id, batch.id(), block.blockId(), block.tissueBoxId(), block.blockNo(), Timestamp.from(now), actor);
        return member(id, batch.organizationReference()).orElseThrow();
    }

    public Optional<ProcessingRunSnapshot> run(UUID runId, String organizationReference) {
        return jdbc.query("""
                SELECT r.id, r.batch_id, r.run_no, r.execution_mode_code, r.device_identity_ref,
                       r.external_run_id, r.program_version_snapshot, r.run_state_code, r.started_at,
                       r.completed_at, r.validated_at, r.confirmed_at
                  FROM pis.p17_processing_run r
                  JOIN pis.p17_processing_batch b ON b.id = r.batch_id
                 WHERE r.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingRunSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class), rs.getInt("run_no"), rs.getString("execution_mode_code"),
                rs.getString("device_identity_ref"), rs.getString("external_run_id"),
                rs.getString("program_version_snapshot"), rs.getString("run_state_code"), rs.getTimestamp("started_at"),
                rs.getTimestamp("completed_at"), rs.getTimestamp("validated_at"), rs.getTimestamp("confirmed_at")))
                : Optional.empty(), runId, organizationReference);
    }

    public Optional<ProcessingRunSnapshot> runForBatch(UUID batchId, String organizationReference) {
        return jdbc.query("""
                SELECT r.id, r.batch_id, r.run_no, r.execution_mode_code, r.device_identity_ref,
                       r.external_run_id, r.program_version_snapshot, r.run_state_code, r.started_at,
                       r.completed_at, r.validated_at, r.confirmed_at
                  FROM pis.p17_processing_run r
                  JOIN pis.p17_processing_batch b ON b.id = r.batch_id
                 WHERE r.batch_id = ? AND b.organization_reference = ? ORDER BY r.created_at DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(new ProcessingRunSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class), rs.getInt("run_no"), rs.getString("execution_mode_code"),
                rs.getString("device_identity_ref"), rs.getString("external_run_id"),
                rs.getString("program_version_snapshot"), rs.getString("run_state_code"), rs.getTimestamp("started_at"),
                rs.getTimestamp("completed_at"), rs.getTimestamp("validated_at"), rs.getTimestamp("confirmed_at")))
                : Optional.empty(), batchId, organizationReference);
    }

    public ProcessingRunSnapshot startBatch(ProcessingBatchSnapshot batch, String actor, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis.p17_processing_batch
                   SET batch_state_code = ?, assigned_actor_ref = COALESCE(assigned_actor_ref, ?),
                       started_at = ?, record_version_no = record_version_no + 1,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND batch_state_code IN (?, ?)
                   AND concurrency_version = ?
                """, ProcessingBatch.IN_PROGRESS, actor, Timestamp.from(now), batch.id(), batch.organizationReference(),
                ProcessingBatch.PLANNED, ProcessingBatch.ASSIGNED, batch.concurrencyVersion());
        if (changed != 1) throw new IllegalStateException("PROCESSING_BATCH_VERSION_CONFLICT");
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_run
                (id, batch_id, run_no, execution_mode_code, device_identity_ref, external_run_id,
                 program_version_snapshot, run_state_code, started_at, created_at, created_by_ref)
                VALUES (?, ?, 1, ?, ?, ?, ?, 'P17-RUN-STARTED', ?, ?, ?)
                """, runId, batch.id(), batch.executionMode(), batch.deviceIdentity(), "P17-RUN-" + runId,
                batch.programVersionSnapshot(), Timestamp.from(now), Timestamp.from(now), actor);
        jdbc.update("""
                UPDATE pis.p17_processing_batch_member SET member_state_code = 'P17-MEMBER-IN-PROCESSING'
                 WHERE batch_id = ? AND member_state_code = 'P17-MEMBER-PLANNED'
                """, batch.id());
        return run(runId, batch.organizationReference()).orElseThrow();
    }

    public boolean transitionBatch(UUID batchId, String organizationReference, String expectedState, String targetState,
            String actor, long expectedVersion, Instant now, String failureReason) {
        int changed = jdbc.update("""
                UPDATE pis.p17_processing_batch
                   SET batch_state_code = ?, failure_reason_code = ?,
                       interrupted_at = CASE WHEN ? = ? THEN ? ELSE interrupted_at END,
                       completed_at = CASE WHEN ? IN (?, ?, ?) THEN ? ELSE completed_at END,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND batch_state_code = ?
                   AND concurrency_version = ?
                """, targetState, failureReason, targetState, ProcessingBatch.INTERRUPTED, Timestamp.from(now),
                targetState, ProcessingBatch.COMPLETED, ProcessingBatch.PARTIAL, ProcessingBatch.FAILED,
                Timestamp.from(now), batchId, organizationReference, expectedState, expectedVersion);
        if (changed == 1) appendStateHistory(batchId, "P17-PROCESSING-BATCH", "P17-BATCH", expectedState, targetState,
                "P17-CMD-BATCH-STATE", expectedVersion, expectedVersion + 1, now, actor, failureReason == null ? "batch state" : failureReason);
        return changed == 1;
    }

    public boolean updateMember(UUID memberId, String expectedState, String targetState, boolean canEnterEmbedding,
            long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p17_processing_batch_member
                   SET member_state_code = ?, can_enter_embedding = ?, completed_at = ?,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND member_state_code = ? AND concurrency_version = ?
                """, targetState, canEnterEmbedding, Timestamp.from(now), memberId, expectedState, expectedVersion) == 1;
    }

    public ProcessingStepSnapshot insertStep(UUID runId, int sequence, String stepCode, String state, String observed,
            String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_run_step
                (id, run_id, step_sequence, step_code, step_state_code, observed_reference, started_at, completed_at,
                 created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, runId, sequence, stepCode, state, observed, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now), actor);
        return new ProcessingStepSnapshot(id, sequence, stepCode, state);
    }

    public Optional<RawResultSnapshot> rawResult(String externalMessageId) {
        return jdbc.query("SELECT id, run_id, external_payload_digest, raw_state_code FROM pis.p17_processing_raw_result WHERE external_message_id = ?",
                rs -> rs.next() ? Optional.of(new RawResultSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("run_id", UUID.class), rs.getString("external_payload_digest"), rs.getString("raw_state_code"))) : Optional.empty(),
                externalMessageId);
    }

    public RawResultSnapshot insertRawResult(UUID runId, String messageId, String digest, String rawState,
            String payloadReference, Instant deviceTime, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_raw_result
                (id, run_id, external_message_id, external_payload_digest, raw_state_code, raw_payload_reference,
                 device_occurred_at, received_at, received_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, runId, messageId, digest, rawState, payloadReference,
                deviceTime == null ? null : Timestamp.from(deviceTime), Timestamp.from(now), actor);
        jdbc.update("UPDATE pis.p17_processing_run SET run_state_code = 'P17-RUN-RAW-RECEIVED' WHERE id = ?", runId);
        return new RawResultSnapshot(id, runId, digest, rawState);
    }

    public Optional<ProcessingResultSnapshot> result(UUID resultId, String organizationReference) {
        return jdbc.query("""
                SELECT r.id, r.run_id, r.member_id, r.result_state_code, r.can_enter_embedding, r.result_summary,
                       r.record_version_no, r.confirmed_by_ref
                  FROM pis.p17_processing_result r
                  JOIN pis.p17_processing_run run ON run.id = r.run_id
                  JOIN pis.p17_processing_batch b ON b.id = run.batch_id
                 WHERE r.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingResultSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getObject("member_id", UUID.class),
                rs.getString("result_state_code"), rs.getBoolean("can_enter_embedding"),
                rs.getString("result_summary"), rs.getInt("record_version_no"), rs.getString("confirmed_by_ref")))
                : Optional.empty(), resultId, organizationReference);
    }

    public Optional<ProcessingResultSnapshot> resultForMember(UUID memberId, String organizationReference) {
        return jdbc.query("""
                SELECT r.id, r.run_id, r.member_id, r.result_state_code, r.can_enter_embedding, r.result_summary,
                       r.record_version_no, r.confirmed_by_ref
                  FROM pis.p17_processing_result r
                  JOIN pis.p17_processing_run run ON run.id = r.run_id
                  JOIN pis.p17_processing_batch b ON b.id = run.batch_id
                 WHERE r.member_id = ? AND b.organization_reference = ? ORDER BY r.created_at DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(new ProcessingResultSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getObject("member_id", UUID.class),
                rs.getString("result_state_code"), rs.getBoolean("can_enter_embedding"),
                rs.getString("result_summary"), rs.getInt("record_version_no"), rs.getString("confirmed_by_ref")))
                : Optional.empty(), memberId, organizationReference);
    }

    public Optional<ProcessingResultSnapshot> resultForEmbeddingFact(UUID embeddingFactId,
            String organizationReference) {
        return jdbc.query("""
                SELECT r.id, r.run_id, r.member_id, r.result_state_code, r.can_enter_embedding, r.result_summary,
                       r.record_version_no, r.confirmed_by_ref
                  FROM pis.p17_processing_result r
                  JOIN pis.p17_embedding_fact f ON f.processing_result_id = r.id
                  JOIN pis.p17_processing_run run ON run.id = r.run_id
                  JOIN pis.p17_processing_batch b ON b.id = run.batch_id
                 WHERE f.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ProcessingResultSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getObject("member_id", UUID.class),
                rs.getString("result_state_code"), rs.getBoolean("can_enter_embedding"),
                rs.getString("result_summary"), rs.getInt("record_version_no"), rs.getString("confirmed_by_ref")))
                : Optional.empty(), embeddingFactId, organizationReference);
    }

    public ProcessingResultSnapshot confirmResult(UUID runId, UUID memberId, String resultState,
            boolean canEnterEmbedding, String summary, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_result
                (id, run_id, member_id, result_state_code, can_enter_embedding, result_summary,
                 validated_at, validated_by_ref, confirmed_at, confirmed_by_ref, record_version_no, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """, id, runId, memberId, resultState, canEnterEmbedding, summary, Timestamp.from(now), actor,
                Timestamp.from(now), actor, Timestamp.from(now));
        jdbc.update("UPDATE pis.p17_processing_run SET run_state_code = 'P17-RUN-VALIDATED', validated_at = ?, confirmed_at = ? WHERE id = ?",
                Timestamp.from(now), Timestamp.from(now), runId);
        return result(id, findOrganizationForRun(runId)).orElseThrow();
    }

    private String findOrganizationForRun(UUID runId) {
        return jdbc.queryForObject("""
                SELECT b.organization_reference FROM pis.p17_processing_run r
                 JOIN pis.p17_processing_batch b ON b.id = r.batch_id WHERE r.id = ?
                """, String.class, runId);
    }

    public UUID createException(UUID batchId, UUID runId, UUID memberId, String code, String severity, String reason,
            String affectedScope, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_exception
                (id, batch_id, run_id, member_id, exception_code, severity_code, exception_state_code, reason,
                 affected_scope, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, 'P17-EXCEPTION-OPEN', ?, ?, ?, ?)
                """, id, batchId, runId, memberId, code, severity, reason, affectedScope, Timestamp.from(now), actor);
        return id;
    }

    public UUID recordImpact(UUID exceptionId, UUID memberId, boolean canContinue, boolean requiresReprocess,
            boolean isolationRequired, String state, String reason, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_member_impact
                (id, exception_id, member_id, impact_state_code, can_continue, requires_reprocess,
                 isolation_required, decision_reason, decided_at, decided_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, exceptionId, memberId, state, canContinue, requiresReprocess, isolationRequired, reason,
                Timestamp.from(now), actor);
        if (isolationRequired || requiresReprocess) {
            jdbc.update("UPDATE pis.p17_processing_batch_member SET impact_state_code = ?, member_state_code = 'P17-MEMBER-AFFECTED', can_enter_embedding = FALSE WHERE id = ?",
                    state, memberId);
        }
        return id;
    }

    public UUID recordRecovery(UUID exceptionId, String kind, String state, String reason, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_recovery
                (id, exception_id, recovery_kind_code, recovery_state_code, decision_reason, approved_at, approved_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, exceptionId, kind, state, reason, Timestamp.from(now), actor);
        jdbc.update("UPDATE pis.p17_processing_exception SET exception_state_code = 'P17-EXCEPTION-RESOLVED', resolved_at = ?, resolved_by_ref = ? WHERE id = ?",
                Timestamp.from(now), actor, exceptionId);
        return id;
    }

    public Optional<UUID> currentExceptionForBatch(UUID batchId) {
        return jdbc.query("""
                SELECT id FROM pis.p17_processing_exception
                 WHERE batch_id = ? AND exception_state_code <> 'P17-EXCEPTION-RESOLVED'
                 ORDER BY created_at DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty(), batchId);
    }

    public ProcessingTaskSnapshot createReprocessTask(SourceBlock block, String taskNo, String actor, Instant now) {
        return createTask(block, taskNo, actor, now);
    }

    public UUID recordReprocess(UUID originalBatchId, UUID originalRunId, UUID originalMemberId, UUID replacementTaskId,
            String reason, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_processing_reprocess
                (id, original_batch_id, original_run_id, original_member_id, replacement_task_id,
                 reason, approved_at, approved_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, originalBatchId, originalRunId, originalMemberId, replacementTaskId, reason,
                Timestamp.from(now), actor);
        return id;
    }

    public List<Map<String, Object>> embeddingQueue(String organizationReference) {
        return jdbc.queryForList("""
                SELECT r.id AS processing_result_id, m.id AS member_id, m.tissue_block_id, b.block_no,
                       s.specimen_no, tb.tissue_box_no, et.id AS embedding_task_id,
                       r.result_state_code, r.result_summary
                  FROM pis.p17_processing_result r
                  JOIN pis.p17_processing_batch_member m ON m.id = r.member_id
                  JOIN pis.tissue_block b ON b.id = m.tissue_block_id
                  JOIN pis.specimen s ON s.id = b.specimen_id
                  JOIN pis.tissue_box_identity tb ON tb.block_id = b.id
                  JOIN pis.p17_processing_batch pb ON pb.id = m.batch_id
                  LEFT JOIN pis.p17_embedding_task et ON et.tissue_block_id = b.id
                       AND et.processing_result_id = r.id
                 WHERE pb.organization_reference = ? AND r.can_enter_embedding = TRUE
                   AND r.result_state_code = 'P17-RESULT-VALIDATED'
                   AND b.block_lifecycle_state_code = 'P08-SM-004-ST-02'
                   AND b.physical_formed_at IS NULL
                   AND et.id IS NULL
                 ORDER BY b.created_at, b.id LIMIT 200
                """, organizationReference);
    }

    public Optional<EmbeddingTaskSnapshot> embeddingTask(UUID taskId, String organizationReference) {
        return jdbc.query("""
                SELECT e.id, e.task_no, e.tissue_block_id, e.processing_result_id, e.task_state_code,
                       e.assigned_actor_ref, e.actual_actor_ref, e.embedding_requirement_snapshot,
                       e.orientation_reference, e.task_attempt_no, e.rework_of_formation_id,
                       e.record_version_no, e.concurrency_version
                  FROM pis.p17_embedding_task e
                 WHERE e.id = ? AND e.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new EmbeddingTaskSnapshot(rs.getObject("id", UUID.class),
                rs.getString("task_no"), rs.getObject("tissue_block_id", UUID.class),
                rs.getObject("processing_result_id", UUID.class), rs.getString("task_state_code"),
                rs.getString("assigned_actor_ref"), rs.getString("actual_actor_ref"),
                rs.getString("embedding_requirement_snapshot"), rs.getString("orientation_reference"),
                rs.getInt("task_attempt_no"), rs.getObject("rework_of_formation_id", UUID.class),
                rs.getInt("record_version_no"), rs.getLong("concurrency_version"))) : Optional.empty(), taskId,
                organizationReference);
    }

    public EmbeddingTaskSnapshot createEmbeddingTask(SourceBlock block, ProcessingResultSnapshot result,
            String taskNo, int attemptNo, UUID reworkOfFormationId, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_embedding_task
                (id, task_no, tissue_block_id, processing_result_id, task_attempt_no, organization_reference,
                 task_state_code, record_version_no, concurrency_version, created_at, created_by_ref, rework_of_formation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?)
                """, id, taskNo, block.blockId(), result.id(), attemptNo, block.organizationReference(),
                EmbeddingTask.PLANNED, Timestamp.from(now), actor, reworkOfFormationId);
        return embeddingTask(id, block.organizationReference()).orElseThrow();
    }

    public int nextEmbeddingAttempt(UUID blockId, UUID resultId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pis.p17_embedding_task WHERE tissue_block_id = ? AND processing_result_id = ?",
                Integer.class, blockId, resultId);
        return (count == null ? 0 : count) + 1;
    }

    public boolean takeoverEmbedding(UUID taskId, String organizationReference, String actor, long expectedVersion,
            Instant now) {
        EmbeddingTaskSnapshot before = embeddingTask(taskId, organizationReference).orElseThrow();
        int changed = jdbc.update("""
                UPDATE pis.p17_embedding_task
                   SET task_state_code = ?, assigned_actor_ref = ?, actual_actor_ref = ?, assigned_at = ?,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND task_state_code IN (?, ?)
                   AND concurrency_version = ?
                """, EmbeddingTask.ASSIGNED, actor, actor, Timestamp.from(now), taskId, organizationReference,
                EmbeddingTask.PLANNED, EmbeddingTask.ASSIGNED, expectedVersion);
        if (changed == 1) {
            jdbc.update("""
                    INSERT INTO pis.p17_embedding_task_assignment
                    (id, task_id, from_actor_ref, to_actor_ref, action_code, reason, occurred_at, recorded_by_ref)
                    VALUES (?, ?, ?, ?, 'P17-EMBEDDING-TAKEOVER', ?, ?, ?)
                    """, UUID.randomUUID(), taskId, before.assignedActorRef(), actor, "包埋责任接管", Timestamp.from(now), actor);
        }
        return changed == 1;
    }

    public boolean transitionEmbedding(UUID taskId, String organizationReference, String expectedState,
            String targetState, String actor, long expectedVersion, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis.p17_embedding_task
                   SET task_state_code = ?, started_at = CASE WHEN ? = ? THEN ? ELSE started_at END,
                       actual_actor_ref = COALESCE(actual_actor_ref, ?),
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND task_state_code = ?
                   AND concurrency_version = ?
                """, targetState, targetState, EmbeddingTask.IN_PROGRESS, Timestamp.from(now), actor,
                taskId, organizationReference, expectedState, expectedVersion);
        if (changed == 1) appendStateHistory(taskId, "P17-EMBEDDING-TASK", "P17-EMBEDDING", expectedState, targetState,
                "P17-CMD-EMBEDDING-STATE", expectedVersion, expectedVersion + 1, now, actor, "embedding task state");
        return changed == 1;
    }

    public boolean updateEmbeddingRequirement(UUID taskId, String organizationReference, String actor,
            String requirement, String orientation, long expectedVersion) {
        return jdbc.update("""
                UPDATE pis.p17_embedding_task
                   SET embedding_requirement_snapshot = ?, orientation_reference = ?,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND assigned_actor_ref = ?
                   AND task_state_code IN (?, ?) AND concurrency_version = ?
                """, requirement, orientation, taskId, organizationReference, actor, EmbeddingTask.ASSIGNED,
                EmbeddingTask.IN_PROGRESS, expectedVersion) == 1;
    }

    public Optional<FormationSnapshot> currentFormation(UUID tissueBlockId, String organizationReference) {
        return jdbc.query("""
                SELECT f.id, f.tissue_block_id, f.embedding_fact_id, f.formation_version_no,
                       f.inherited_block_no, f.current_valid, f.formation_state_code, f.formed_at, f.formed_by_ref
                  FROM pis.p17_actual_block_formation f
                  JOIN pis.tissue_block b ON b.id = f.tissue_block_id
                 WHERE f.tissue_block_id = ? AND b.organization_reference = ? AND f.current_valid = TRUE
                 ORDER BY f.formation_version_no DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(new FormationSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("tissue_block_id", UUID.class), rs.getObject("embedding_fact_id", UUID.class),
                rs.getInt("formation_version_no"), rs.getString("inherited_block_no"), rs.getBoolean("current_valid"),
                rs.getString("formation_state_code"), rs.getTimestamp("formed_at"), rs.getString("formed_by_ref")))
                : Optional.empty(), tissueBlockId, organizationReference);
    }

    public Optional<FormationSnapshot> formation(UUID formationId, String organizationReference) {
        return jdbc.query("""
                SELECT f.id, f.tissue_block_id, f.embedding_fact_id, f.formation_version_no,
                       f.inherited_block_no, f.current_valid, f.formation_state_code, f.formed_at, f.formed_by_ref
                  FROM pis.p17_actual_block_formation f
                  JOIN pis.tissue_block b ON b.id = f.tissue_block_id
                 WHERE f.id = ? AND b.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new FormationSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("tissue_block_id", UUID.class), rs.getObject("embedding_fact_id", UUID.class),
                rs.getInt("formation_version_no"), rs.getString("inherited_block_no"), rs.getBoolean("current_valid"),
                rs.getString("formation_state_code"), rs.getTimestamp("formed_at"), rs.getString("formed_by_ref")))
                : Optional.empty(), formationId, organizationReference);
    }

    public UUID lockTissueBlock(UUID blockId, String organizationReference) {
        return jdbc.queryForObject("SELECT id FROM pis.tissue_block WHERE id = ? AND organization_reference = ? FOR UPDATE",
                UUID.class, blockId, organizationReference);
    }

    public int nextFormationVersion(UUID blockId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pis.p17_actual_block_formation WHERE tissue_block_id = ?",
                Integer.class, blockId);
        return (count == null ? 0 : count) + 1;
    }

    public EmbeddingFactSnapshot insertEmbeddingFact(EmbeddingTaskSnapshot task, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_embedding_fact
                (id, task_id, tissue_block_id, processing_result_id, embedding_state_code,
                 requirement_snapshot, orientation_reference, actual_actor_ref, started_at, completed_at, record_version_no)
                VALUES (?, ?, ?, ?, 'P17-EMBEDDING-COMPLETED', ?, ?, ?, COALESCE((SELECT started_at FROM pis.p17_embedding_task WHERE id = ?), ?), ?, 1)
                """, id, task.id(), task.tissueBlockId(), task.processingResultId(), task.requirementSnapshot(),
                task.orientationReference(), actor, task.id(), Timestamp.from(now), Timestamp.from(now));
        return new EmbeddingFactSnapshot(id, task.id(), task.tissueBlockId(), task.processingResultId());
    }

    public FormationSnapshot insertFormation(EmbeddingFactSnapshot fact, SourceBlock block, int formationVersion,
            UUID supersedes, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis.p17_actual_block_formation
                (id, tissue_block_id, embedding_fact_id, processing_result_id, formation_version_no,
                 inherited_block_no, current_valid, formation_state_code, formed_at, formed_by_ref, supersedes_formation_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, 'P17-ACTUAL-BLOCK-ACTIVE', ?, ?, ?, ?)
                """, id, fact.tissueBlockId(), fact.id(), fact.processingResultId(), formationVersion, block.blockNo(),
                Timestamp.from(now), actor, supersedes, Timestamp.from(now));
        return formation(id, block.organizationReference()).orElseThrow();
    }

    public void markPreviousFormationSuperseded(UUID formationId, Instant now, String actor) {
        jdbc.update("""
                UPDATE pis.p17_actual_block_formation
                   SET current_valid = FALSE, formation_state_code = 'P17-ACTUAL-BLOCK-SUPERSEDED'
                 WHERE id = ? AND current_valid = TRUE
                """, formationId);
    }

    public boolean voidFormation(UUID formationId, String organizationReference) {
        return jdbc.update("""
                UPDATE pis.p17_actual_block_formation f
                   SET current_valid = FALSE, formation_state_code = 'P17-ACTUAL-BLOCK-VOIDED'
                 WHERE f.id = ? AND f.current_valid = TRUE
                   AND EXISTS (SELECT 1 FROM pis.tissue_block b
                                WHERE b.id = f.tissue_block_id AND b.organization_reference = ?)
                """, formationId, organizationReference) == 1;
    }

    public boolean voidBlock(UUID blockId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.tissue_block
                   SET block_lifecycle_state_code = 'P08-SM-004-ST-04', record_version_no = record_version_no + 1,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                """, blockId, organizationReference, expectedVersion) == 1;
    }

    public void linkReplacement(UUID originalFormationId, UUID replacementFormationId, String reason, String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p17_actual_block_replacement
                (id, original_formation_id, replacement_formation_id, reason, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), originalFormationId, replacementFormationId, reason, Timestamp.from(now), actor);
    }

    public boolean markBlockFormed(UUID blockId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.tissue_block
                   SET block_lifecycle_state_code = 'P08-SM-004-ST-03', physical_formed_at = ?,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND block_lifecycle_state_code = 'P08-SM-004-ST-02'
                   AND physical_formed_at IS NULL AND concurrency_version = ?
                """, Timestamp.from(now), blockId, organizationReference, expectedVersion) == 1;
    }

    public boolean markEmbeddingCompleted(UUID taskId, String organizationReference, String actor, long expectedVersion,
            Instant now) {
        return jdbc.update("""
                UPDATE pis.p17_embedding_task
                   SET task_state_code = 'P17-EMBEDDING-TASK-COMPLETED', completed_at = ?, actual_actor_ref = ?,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND task_state_code IN (?, ?)
                   AND concurrency_version = ?
                """, Timestamp.from(now), actor, taskId, organizationReference, EmbeddingTask.ASSIGNED,
                EmbeddingTask.IN_PROGRESS, expectedVersion) == 1;
    }

    public Optional<IdempotentReference> idempotent(String operationCode, String key, String digest) {
        return jdbc.query("""
                SELECT payload_digest, result_object_id FROM pis.p16_idempotency_key
                 WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> {
                    if (!rs.next()) return Optional.empty();
                    if (!digest.equals(rs.getString("payload_digest"))) throw new IdempotencyConflictException();
                    return Optional.of(new IdempotentReference(rs.getObject("result_object_id", UUID.class)));
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
        } catch (DuplicateKeyException exception) {
            throw new IdempotencyConflictException();
        }
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

    public record SourceBlock(UUID blockId, String blockNo, String blockStateCode, Timestamp physicalFormedAt,
            long concurrencyVersion, UUID specimenId, String specimenNo, UUID caseId, String caseNo, UUID tissueBoxId,
            String tissueBoxNo, UUID grossingBatchId, String grossingBatchState, Timestamp handedOffAt,
            String organizationReference) { }
    public record ProcessingTaskSnapshot(UUID id, String taskNo, UUID tissueBlockId, String organizationReference, String stateCode,
            String assignedActorRef, String actualActorRef, Timestamp assignedAt, Timestamp completedAt,
            int recordVersion, long concurrencyVersion) { }
    public record ProgramVersionSnapshot(UUID id, String programCode, String versionLabel, String environmentCode,
            String versionStateCode, String versionDigest, String parameterReference) { }
    public record ProcessingBatchSnapshot(UUID id, UUID taskId, String batchNo, UUID programVersionId,
            String programVersionSnapshot, String executionMode, String deviceIdentity, String stateCode,
            String assignedActorRef, Timestamp startedAt, Timestamp completedAt, Timestamp interruptedAt,
            String failureReasonCode, int recordVersion, long concurrencyVersion, String organizationReference) { }
    public record ProcessingMemberSnapshot(UUID id, UUID batchId, UUID tissueBlockId, UUID tissueBoxIdentityId,
            String plannedBlockNo, String stateCode, String impactStateCode, boolean canEnterEmbedding, long version) { }
    public record ProcessingRunSnapshot(UUID id, UUID batchId, int runNo, String executionMode, String deviceIdentity,
            String externalRunId, String programVersionSnapshot, String stateCode, Timestamp startedAt,
            Timestamp completedAt, Timestamp validatedAt, Timestamp confirmedAt) { }
    public record ProcessingStepSnapshot(UUID id, int sequence, String stepCode, String stateCode) { }
    public record RawResultSnapshot(UUID id, UUID runId, String payloadDigest, String stateCode) { }
    public record ProcessingResultSnapshot(UUID id, UUID runId, UUID memberId, String stateCode,
            boolean canEnterEmbedding, String summary, int recordVersion, String confirmedByRef) { }
    public record EmbeddingTaskSnapshot(UUID id, String taskNo, UUID tissueBlockId, UUID processingResultId,
            String stateCode, String assignedActorRef, String actualActorRef, String requirementSnapshot,
            String orientationReference, int taskAttemptNo, UUID reworkOfFormationId, int recordVersion,
            long concurrencyVersion) { }
    public record EmbeddingFactSnapshot(UUID id, UUID taskId, UUID tissueBlockId, UUID processingResultId) { }
    public record FormationSnapshot(UUID id, UUID tissueBlockId, UUID embeddingFactId, int formationVersion,
            String inheritedBlockNo, boolean currentValid, String stateCode, Timestamp formedAt, String formedByRef) { }
    public record IdempotentReference(UUID resultObjectId) { }
    public static final class IdempotencyConflictException extends RuntimeException { }
}

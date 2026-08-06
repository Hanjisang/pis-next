package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.technical.application.ProcessingApplicationService;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.AddMemberCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CompleteEmbeddingCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ConfirmResultCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CreateBatchCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CreateEmbeddingCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.CreateTaskCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.RawResultCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.RequirementCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.VersionCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.FailureCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ImpactCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.RecoveryCommand;
import com.hanjisang.pis.technical.application.ProcessingApplicationService.ReprocessCommand;

@SpringBootTest
@ActiveProfiles("test")
@Sql({ "classpath:p16-test-schema.sql", "classpath:p17-test-schema.sql" })
class ProcessingApplicationServiceTest {

    private static final String ACTOR = "p15-local-registration-actor";
    private static final String ORGANIZATION = "LOCAL_HOSPITAL";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProcessingApplicationService service;

    private UUID blockId;
    private UUID caseId;
    private UUID specimenId;

    @BeforeEach
    void seedHandedOffPlannedBlock() {
        caseId = UUID.randomUUID();
        specimenId = UUID.randomUUID();
        blockId = UUID.randomUUID();
        UUID grossingBatchId = UUID.randomUUID();
        UUID tissueBoxId = UUID.randomUUID();
        String suffix = blockId.toString().substring(0, 8);
        Instant now = Instant.now();

        jdbc.update("INSERT INTO pis.pathology_case(id, case_no, organization_reference) VALUES (?, ?, ?)",
                caseId, "DEV-P17-CASE-" + suffix, ORGANIZATION);
        jdbc.update("""
                INSERT INTO pis.specimen
                (id, case_id, specimen_no, specimen_kind_code, specimen_source_code, collection_site_text,
                 collection_method_code, specimen_lifecycle_state_code, record_version_no, concurrency_version,
                 organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, 'SYNTHETIC', 'LOCAL', 'synthetic site', 'DIRECT', 'P08-SM-003-ST-03', 1, 0, ?, ?, ?)
                """, specimenId, caseId, "DEV-P17-SP-" + suffix, ORGANIZATION, now, ACTOR);
        jdbc.update("""
                INSERT INTO pis.grossing_batch
                (id, batch_no, organization_reference, task_state_code, batch_state_code, assigned_actor_ref,
                 actual_actor_ref, handed_off_at, record_version_no, concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P16-TASK-COMPLETED', 'P16-GROSSING-HANDED-OFF', ?, ?, ?, 1, 1, ?, ?)
                """, grossingBatchId, "DEV-P17-GROSS-" + suffix, ORGANIZATION, ACTOR, ACTOR, now, now, ACTOR);
        jdbc.update("""
                INSERT INTO pis.tissue_block
                (id, case_id, specimen_id, batch_id, block_no, block_kind_code, source_material_kind_code,
                 block_lifecycle_state_code, physical_formed_at, tissue_box_identity_id, record_version_no,
                 concurrency_version, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, 'ROUTINE', 'TISSUE', 'P08-SM-004-ST-02', NULL, ?, 1, 0, ?, ?, ?)
                """, blockId, caseId, specimenId, grossingBatchId, "DEV-P17-BLOCK-" + suffix, tissueBoxId,
                ORGANIZATION, now, ACTOR);
        jdbc.update("""
                INSERT INTO pis.tissue_box_identity
                (id, block_id, tissue_box_no, box_state_code, organization_reference, assigned_at, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P16-TISSUE-BOX-ACTIVE', ?, ?, ?, ?)
                """, tissueBoxId, blockId, "DEV-P17-BOX-" + suffix, ORGANIZATION, now, now, ACTOR);
    }

    @Test
    void normalProcessingAndEmbeddingFlowFormsOneActualBlockAndReplaysSafely() {
        String suffix = blockId.toString().substring(0, 8);
        var task = service.createTask(new CreateTaskCommand(blockId, "p17-task-" + suffix));
        var taskReplay = service.createTask(new CreateTaskCommand(blockId, "p17-task-" + suffix));
        assertThat(taskReplay.duplicate()).isTrue();

        task = service.takeoverTask(task.taskId(), new VersionCommand(task.concurrencyVersion(), "p17-takeover-" + suffix));
        var batch = service.createBatch(new CreateBatchCommand(task.taskId(), "P17-SYNTHETIC-REFERENCE", "SYNTHETIC-1",
                "HUMAN", null, "p17-batch-" + suffix));
        var member = service.addMember(batch.batchId(), new AddMemberCommand(blockId, "p17-member-" + suffix));
        var run = service.startBatch(batch.batchId(), new VersionCommand(batch.concurrencyVersion(), "p17-start-" + suffix));
        var raw = service.receiveRawResult(new RawResultCommand(run.runId(), "p17-message-" + suffix, "digest-" + suffix,
                "P17-RAW-COMPLETE", "synthetic://p17/" + suffix, Instant.now(), "p17-raw-" + suffix));
        assertThat(raw.duplicate()).isFalse();
        var result = service.confirmResult(new ConfirmResultCommand(run.runId(), member.memberId(), "P17-RESULT-VALIDATED",
                true, "synthetic validated result", member.concurrencyVersion(), "p17-confirm-" + suffix));
        assertThat(result.canEnterEmbedding()).isTrue();
        var completedBatch = service.completeBatch(batch.batchId(), new VersionCommand(batch.concurrencyVersion() + 1,
                "p17-complete-batch-" + suffix));
        assertThat(completedBatch.stateCode()).isEqualTo("P17-PROCESSING-BATCH-COMPLETED");

        var embedding = service.createEmbeddingTask(new CreateEmbeddingCommand(blockId, result.resultId(), null,
                "p17-embedding-" + suffix));
        embedding = service.takeoverEmbeddingTask(embedding.taskId(), new VersionCommand(embedding.concurrencyVersion(),
                "p17-embedding-takeover-" + suffix));
        embedding = service.startEmbedding(embedding.taskId(), new VersionCommand(embedding.concurrencyVersion(),
                "p17-embedding-start-" + suffix));
        embedding = service.recordEmbeddingRequirements(embedding.taskId(), new RequirementCommand(
                "P17-SYNTHETIC-EMBEDDING-REQUIREMENTS", "synthetic orientation", embedding.concurrencyVersion(),
                "p17-embedding-requirements-" + suffix));
        var formation = service.completeEmbedding(new CompleteEmbeddingCommand(embedding.taskId(),
                embedding.concurrencyVersion(), 0, null, "p17-embedding-complete-" + suffix));
        var formationReplay = service.completeEmbedding(new CompleteEmbeddingCommand(embedding.taskId(),
                embedding.concurrencyVersion(), 0, null, "p17-embedding-complete-" + suffix));

        assertThat(formation.formationVersion()).isEqualTo(1);
        assertThat(formation.currentValid()).isTrue();
        assertThat(formationReplay.duplicate()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p17_actual_block_formation WHERE tissue_block_id = ?",
                Integer.class, blockId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT block_lifecycle_state_code FROM pis.tissue_block WHERE id = ?",
                String.class, blockId)).isEqualTo("P08-SM-004-ST-03");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.outbox_event", Integer.class)).isGreaterThan(0);
    }

    @Test
    void admissionAndExistingTaskChecksAreRejectedBeforeMutation() {
        String suffix = blockId.toString().substring(0, 8);
        var task = service.createTask(new CreateTaskCommand(blockId, "p17-conflict-task-" + suffix));
        var existing = service.createTask(new CreateTaskCommand(blockId, "p17-conflict-task-other-" + suffix));
        assertThat(existing.duplicate()).isTrue();
        assertThatThrownBy(() -> service.createTask(new CreateTaskCommand(UUID.randomUUID(), "p17-invalid-task-" + suffix)))
                .isInstanceOf(P15BusinessException.class);
        assertThat(task.stateCode()).isEqualTo("P17-PROCESSING-TASK-PLANNED");
    }

    @Test
    void interruptionKeepsExceptionImpactRecoveryAndReprocessTrace() {
        String suffix = blockId.toString().substring(0, 8);
        var task = service.createTask(new CreateTaskCommand(blockId, "p17-failure-task-" + suffix));
        task = service.takeoverTask(task.taskId(), new VersionCommand(task.concurrencyVersion(), "p17-failure-takeover-" + suffix));
        var batch = service.createBatch(new CreateBatchCommand(task.taskId(), "P17-SYNTHETIC-REFERENCE", "SYNTHETIC-1",
                "HUMAN", null, "p17-failure-batch-" + suffix));
        var member = service.addMember(batch.batchId(), new AddMemberCommand(blockId, "p17-failure-member-" + suffix));
        service.startBatch(batch.batchId(), new VersionCommand(batch.concurrencyVersion(), "p17-failure-start-" + suffix));
        batch = service.batch(batch.batchId());
        var interrupted = service.interruptBatch(batch.batchId(), new ProcessingApplicationService.InterruptCommand(
                1, "synthetic interruption", "p17-failure-interrupt-" + suffix));
        UUID exceptionId = jdbc.queryForObject("SELECT id FROM pis.p17_processing_exception WHERE batch_id = ?",
                UUID.class, interrupted.batchId());

        var impact = service.decideImpact(new ImpactCommand(member.memberId(), "P17-IMPACT-REPROCESS", false, true, true,
                "synthetic member isolation", "p17-failure-impact-" + suffix));
        var recovery = service.recover(new RecoveryCommand(exceptionId, "P17-RECOVERY-REPROCESS", "synthetic recovery approved",
                "p17-failure-recovery-" + suffix));
        var replacement = service.requestReprocess(new ReprocessCommand(member.memberId(), "synthetic reprocess",
                "p17-failure-reprocess-" + suffix));

        assertThat(impact.memberId()).isEqualTo(member.memberId());
        assertThat(recovery.exceptionId()).isEqualTo(exceptionId);
        assertThat(replacement.stateCode()).isEqualTo("P17-PROCESSING-TASK-PLANNED");
        assertThat(jdbc.queryForObject("SELECT exception_state_code FROM pis.p17_processing_exception WHERE id = ?", String.class,
                exceptionId)).isEqualTo("P17-EXCEPTION-RESOLVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p17_processing_reprocess WHERE replacement_task_id = ?",
                Integer.class, replacement.taskId())).isEqualTo(1);
    }
}

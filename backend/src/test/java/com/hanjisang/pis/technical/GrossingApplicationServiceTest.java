package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.technical.application.GrossingApplicationService;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:p16-test-schema.sql")
class GrossingApplicationServiceTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private GrossingApplicationService service;
    private UUID caseId;
    private UUID specimenId;

    @BeforeEach
    void seedReceivedSpecimen() {
        caseId = UUID.randomUUID();
        specimenId = UUID.randomUUID();
        String suffix = specimenId.toString().substring(0, 8);
        jdbc.update("INSERT INTO pis.pathology_case(id, case_no, organization_reference) VALUES (?, ?, ?)", caseId,
                "DEV-CASE-" + suffix, "LOCAL_HOSPITAL");
        jdbc.update("""
                INSERT INTO pis.specimen
                (id, case_id, specimen_no, specimen_kind_code, specimen_source_code, collection_site_text,
                 collection_method_code, specimen_lifecycle_state_code, record_version_no, concurrency_version,
                 organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, 'TISSUE', 'LOCAL', ?, 'SURGICAL', ?, 1, 1, ?, CURRENT_TIMESTAMP, 'test-actor')
                """, specimenId, caseId, "DEV-SP-" + suffix, "synthetic site", "P08-SM-003-ST-03", "LOCAL_HOSPITAL");
    }

    @Test
    void completeFlowKeepsStableTraceAndSameBatchRequestIsIdempotent() {
        String suffix = specimenId.toString().substring(0, 8);
        var created = service.createBatch(new GrossingApplicationService.CreateBatchCommand(specimenId,
                "DEV-SP-" + suffix, "DEV-CASE-" + suffix, "SYNTH-PATIENT-001", "batch-" + suffix));
        var replay = service.createBatch(new GrossingApplicationService.CreateBatchCommand(specimenId,
                "DEV-SP-" + suffix, "DEV-CASE-" + suffix, "SYNTH-PATIENT-001", "batch-" + suffix));
        assertThat(replay.duplicate()).isTrue();

        var assigned = service.takeover(created.batchId(), new GrossingApplicationService.TakeoverCommand(0));
        var started = service.transition(created.batchId(), new GrossingApplicationService.TransitionCommand(assigned.concurrencyVersion()),
                "P16-GROSSING-IN-PROGRESS", "P14-PERM-013");
        var record = service.recordGrossing(created.batchId(), new GrossingApplicationService.GrossingRecordCommand(specimenId,
                "DEV-SP-" + suffix, "DEV-CASE-" + suffix, "SYNTH-PATIENT-001", true, true, "synthetic gross appearance",
                "synthetic gross description", 1, "PIECE", null, null, started.concurrencyVersion()));
        var sample = service.addSample(created.batchId(), new GrossingApplicationService.SampleCommand(specimenId,
                "synthetic site", "synthetic tissue fragment", 1, "PIECE", started.concurrencyVersion() + 1));
        var block = service.createBlock(created.batchId(), new GrossingApplicationService.CreateBlockCommand(specimenId,
                "ROUTINE", "TISSUE", started.concurrencyVersion() + 2));
        service.assignSample(block.blockId(), new GrossingApplicationService.AssignSampleCommand(sample.sampleId(),
                started.concurrencyVersion() + 3));
        var label = service.generateLabel(block.blockId(), new GrossingApplicationService.GenerateLabelCommand("label-" + suffix));
        assertThat(label.snapshot()).contains("block_no=").contains("specimen_no=");
        var print = service.submitPrint(label.labelId(), new GrossingApplicationService.PrintCommand("print-" + suffix, null), false);
        assertThat(print.outcome()).isEqualTo("REFERENCE_SUBMITTED");
        var completed = service.complete(created.batchId(), new GrossingApplicationService.TransitionCommand(started.concurrencyVersion() + 4));
        assertThat(completed.stateCode()).isEqualTo("P16-GROSSING-COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.outbox_event", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event", Integer.class)).isGreaterThan(0);
    }

    @Test
    void identityMismatchAndOrdinaryModificationAreRejected() {
        String suffix = specimenId.toString().substring(0, 8);
        var created = service.createBatch(new GrossingApplicationService.CreateBatchCommand(specimenId,
                "DEV-SP-" + suffix, "DEV-CASE-" + suffix, "SYNTH-PATIENT-001", "batch-mismatch-" + suffix));
        var assigned = service.takeover(created.batchId(), new GrossingApplicationService.TakeoverCommand(0));
        var started = service.transition(created.batchId(), new GrossingApplicationService.TransitionCommand(assigned.concurrencyVersion()),
                "P16-GROSSING-IN-PROGRESS", "P14-PERM-013");
        assertThatThrownBy(() -> service.recordGrossing(created.batchId(), new GrossingApplicationService.GrossingRecordCommand(
                specimenId, "WRONG-SPECIMEN", "DEV-CASE-" + suffix, "SYNTH-PATIENT-001", true, true,
                "appearance", "description", 1, "PIECE", null, null, started.concurrencyVersion())))
                .isInstanceOf(P15BusinessException.class)
                .extracting("errorCode").isEqualTo("P12-ERR-079");
    }

    @Test
    void concurrentTakeoverHasOnlyOneAuthoritativeOwner() throws Exception {
        var created = service.createBatch(new GrossingApplicationService.CreateBatchCommand(specimenId,
                "DEV-SP-" + specimenId.toString().substring(0, 8), "DEV-CASE-" + specimenId.toString().substring(0, 8),
                "SYNTH-PATIENT-001", "concurrent-takeover-" + specimenId));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    () -> takeoverSucceeded(created.batchId()), () -> takeoverSucceeded(created.batchId())));
            assertThat(results.stream().filter(this::futureSucceeded).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.grossing_batch WHERE id = ? AND assigned_actor_ref IS NOT NULL",
                    Integer.class, created.batchId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentBlockAndLabelGenerationDoNotCreateDuplicateEffectiveFacts() throws Exception {
        String suffix = specimenId.toString().substring(0, 8);
        var created = service.createBatch(new GrossingApplicationService.CreateBatchCommand(specimenId,
                "DEV-SP-" + suffix, "DEV-CASE-" + suffix, "SYNTH-PATIENT-001", "concurrent-block-" + suffix));
        var assigned = service.takeover(created.batchId(), new GrossingApplicationService.TakeoverCommand(0));
        var started = service.transition(created.batchId(), new GrossingApplicationService.TransitionCommand(assigned.concurrencyVersion()),
                "P16-GROSSING-IN-PROGRESS", "P14-PERM-013");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<GrossingApplicationService.BlockResult>> blocks = executor.invokeAll(List.of(
                    () -> service.createBlock(created.batchId(), new GrossingApplicationService.CreateBlockCommand(
                            specimenId, "ROUTINE", "TISSUE", started.concurrencyVersion())),
                    () -> service.createBlock(created.batchId(), new GrossingApplicationService.CreateBlockCommand(
                            specimenId, "ROUTINE", "TISSUE", started.concurrencyVersion()))));
            List<GrossingApplicationService.BlockResult> successfulBlocks = blocks.stream().map(this::futureBlock)
                    .filter(java.util.Objects::nonNull).toList();
            assertThat(successfulBlocks).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.tissue_block WHERE batch_id = ?", Integer.class,
                    created.batchId())).isEqualTo(1);

            var block = successfulBlocks.getFirst();
            List<Future<GrossingApplicationService.LabelResult>> labels = executor.invokeAll(List.of(
                    () -> service.generateLabel(block.blockId(), new GrossingApplicationService.GenerateLabelCommand("concurrent-label-a-" + suffix)),
                    () -> service.generateLabel(block.blockId(), new GrossingApplicationService.GenerateLabelCommand("concurrent-label-b-" + suffix))));
            List<GrossingApplicationService.LabelResult> successfulLabels = labels.stream().map(this::futureLabel)
                    .filter(java.util.Objects::nonNull).toList();
            assertThat(successfulLabels).hasSize(2);
            assertThat(successfulLabels.stream().filter(GrossingApplicationService.LabelResult::duplicate).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.label_identity WHERE target_object_id = ?", Integer.class,
                    block.blockId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean takeoverSucceeded(UUID batchId) {
        try {
            service.takeover(batchId, new GrossingApplicationService.TakeoverCommand(0));
            return true;
        } catch (P15BusinessException expected) {
            return false;
        }
    }

    private boolean futureSucceeded(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception expected) {
            return false;
        }
    }

    private GrossingApplicationService.BlockResult futureBlock(Future<GrossingApplicationService.BlockResult> future) {
        try {
            return future.get();
        } catch (Exception expected) {
            return null;
        }
    }

    private GrossingApplicationService.LabelResult futureLabel(Future<GrossingApplicationService.LabelResult> future) {
        try {
            return future.get();
        } catch (Exception expected) {
            return null;
        }
    }
}

package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.CancelCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.CreateOrderCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.PlannedOutputCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ProjectCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ResultCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.ReviewCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.AssignCommand;
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService.VersionCommand;

@SpringBootTest
@ActiveProfiles("test")
@Sql({ "classpath:p16-test-schema.sql", "classpath:p17-test-schema.sql", "classpath:p18-test-schema.sql" })
class TechnicalOrderApplicationServiceTest {

    private static final String ACTOR = "p15-local-registration-actor";
    private static final String ORGANIZATION = "LOCAL_HOSPITAL";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TechnicalOrderApplicationService service;

    private UUID caseId;
    private UUID formationId;

    @BeforeEach
    void seedActualBlock() {
        caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        formationId = UUID.randomUUID();
        String suffix = caseId.toString().substring(0, 8);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO pis.pathology_case(id, case_no, organization_reference) VALUES (?, ?, ?)",
                caseId, "DEV-P18-CASE-" + suffix, ORGANIZATION);
        jdbc.update("""
                INSERT INTO pis.specimen
                (id, case_id, specimen_no, specimen_kind_code, specimen_source_code, collection_site_text,
                 collection_method_code, specimen_lifecycle_state_code, record_version_no, concurrency_version,
                 organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, 'SYNTHETIC', 'LOCAL', 'synthetic site', 'DIRECT', 'P08-SM-003-ST-03', 1, 0, ?, ?, ?)
                """, specimenId, caseId, "DEV-P18-SP-" + suffix, ORGANIZATION, now, ACTOR);
        jdbc.update("""
                INSERT INTO pis.grossing_batch
                (id, batch_no, organization_reference, task_state_code, batch_state_code, record_version_no,
                 concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P16-TASK-COMPLETED', 'P16-GROSSING-HANDED-OFF', 1, 1, ?, ?)
                """, batchId, "DEV-P18-GROSS-" + suffix, ORGANIZATION, now, ACTOR);
        jdbc.update("""
                INSERT INTO pis.tissue_block
                (id, case_id, specimen_id, batch_id, block_no, block_kind_code, source_material_kind_code,
                 block_lifecycle_state_code, record_version_no, concurrency_version, organization_reference,
                 created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, 'ROUTINE', 'TISSUE', 'P08-SM-004-ST-03', 1, 0, ?, ?, ?)
                """, blockId, caseId, specimenId, batchId, "DEV-P18-BLOCK-" + suffix, ORGANIZATION, now, ACTOR);
        jdbc.update("""
                INSERT INTO pis.p17_actual_block_formation
                (id, tissue_block_id, embedding_fact_id, processing_result_id, formation_version_no,
                 inherited_block_no, current_valid, formation_state_code, formed_at, formed_by_ref, created_at)
                VALUES (?, ?, ?, ?, 1, ?, TRUE, 'P17-ACTUAL-BLOCK-ACTIVE', ?, ?, ?)
                """, formationId, blockId, UUID.randomUUID(), UUID.randomUUID(), "DEV-P18-BLOCK-" + suffix, now, ACTOR, now);
    }

    private ProjectCommand project(String type, String code, String key) {
        return new ProjectCommand(code, "SYNTHETIC-1", type, formationId, "DIAGNOSTIC_SUPPORT", "ROUTINE",
                "synthetic project reason", List.of(new PlannedOutputCommand(1, "PLANNED_SLIDE", type,
                        "synthetic-layer", 1, null, "DIAGNOSTIC_SUPPORT", 1, "synthetic planned output", key + "-output")));
    }

    @Test
    void fullOrderLifecyclePreservesTargetPlanResponsibilityResultAndOutbox() {
        String suffix = caseId.toString().substring(0, 8);
        var order = service.createOrder(new CreateOrderCommand(caseId, "TECHNICAL_ORDER", "ROUTINE", "synthetic order",
                ACTOR, List.of(project("DEEP_SECTION", "P18-SYNTHETIC-DEEP-SECTION", "p18-output-" + suffix)), "p18-create-" + suffix));
        var replay = service.createOrder(new CreateOrderCommand(caseId, "TECHNICAL_ORDER", "ROUTINE", "synthetic order",
                ACTOR, List.of(project("DEEP_SECTION", "P18-SYNTHETIC-DEEP-SECTION", "p18-output-" + suffix)), "p18-create-" + suffix));
        assertThat(replay.duplicate()).isTrue();
        assertThat(order.projects()).hasSize(1);
        var project = order.projects().getFirst();

        order = service.submit(order.orderId(), new VersionCommand(order.concurrencyVersion(), "p18-submit-" + suffix));
        project = order.projects().getFirst();
        project = service.review(project.projectId(), new ReviewCommand("APPROVED", "synthetic review", project.concurrencyVersion(), "p18-review-" + suffix));
        project = service.receive(project.projectId(), new VersionCommand(project.concurrencyVersion(), "p18-receive-" + suffix));
        project = service.assign(project.projectId(), new AssignCommand(ACTOR, project.concurrencyVersion(), "p18-assign-" + suffix, "synthetic assignment"));
        project = service.handoff(project.projectId(), new VersionCommand(project.concurrencyVersion(), "p18-handoff-" + suffix));
        project = service.referenceResult(project.projectId(), new ResultCommand("NORMALIZED_BOUNDARY_REFERENCE",
                "SYNTHETIC-DEV-NON-CLINICAL-" + suffix, "digest-" + suffix, "SYNTHETIC", "synthetic non-clinical boundary result",
                project.concurrencyVersion(), "p18-result-" + suffix));
        project = service.closeProject(project.projectId(), new VersionCommand(project.concurrencyVersion(), "p18-close-" + suffix));

        assertThat(project.resultStateCode()).isEqualTo("CLOSED");
        assertThat(service.order(order.orderId()).stateCode()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p18_order_target_history WHERE project_id = ?", Integer.class,
                project.projectId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p18_project_result_reference WHERE project_id = ?", Integer.class,
                project.projectId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.outbox_event", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p18_planned_output WHERE project_id = ?", Integer.class,
                project.projectId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p17_actual_block_formation WHERE id = ?", Integer.class, formationId)).isEqualTo(1);
    }

    @Test
    void targetIntegrityAndVersionGuardsBlockUnsafeActions() {
        String suffix = caseId.toString().substring(0, 8);
        assertThatThrownBy(() -> service.createOrder(new CreateOrderCommand(caseId, "TECHNICAL_ORDER", "ROUTINE", "bad target",
                ACTOR, List.of(new ProjectCommand("P18-SYNTHETIC-DEEP-SECTION", "SYNTHETIC-1", "DEEP_SECTION", UUID.randomUUID(),
                        "DIAGNOSTIC_SUPPORT", "ROUTINE", "bad", List.of(new PlannedOutputCommand(1, "PLANNED_SLIDE", "DEEP_SECTION",
                                null, 1, null, "DIAGNOSTIC_SUPPORT", 1, null, "bad-output")))), "p18-bad-" + suffix)))
                .isInstanceOf(P15BusinessException.class);

        var order = service.createOrder(new CreateOrderCommand(caseId, "TECHNICAL_ORDER", "ROUTINE", "synthetic cancellation",
                ACTOR, List.of(project("IHC", "P18-SYNTHETIC-IHC", "p18-cancel-output-" + suffix)), "p18-cancel-create-" + suffix));
        var project = order.projects().getFirst();
        assertThatThrownBy(() -> service.submit(order.orderId(), new VersionCommand(99, "p18-stale-submit-" + suffix)))
                .isInstanceOf(P15BusinessException.class);
        project = service.cancel(project.projectId(), new CancelCommand("FULL_CANCEL", "synthetic cancellation", "no downstream fact",
                project.concurrencyVersion(), "p18-cancel-" + suffix));
        assertThat(project.taskStateCode()).isEqualTo("P08-SM-007-ST-04");
        assertThat(service.order(order.orderId()).stateCode()).isEqualTo("CANCELLED");
    }
}

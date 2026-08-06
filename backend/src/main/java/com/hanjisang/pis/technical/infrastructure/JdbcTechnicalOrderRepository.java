package com.hanjisang.pis.technical.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTechnicalOrderRepository {

    private final JdbcTemplate jdbc;

    public JdbcTechnicalOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ConfigurationSnapshot> configuration(String projectCode, String versionLabel) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id, project_code, version_label, display_name, project_type_code, target_kind_code,
                           environment_code, lifecycle_state_code, configuration_digest
                    FROM pis.p18_technical_project_configuration
                    WHERE project_code = ? AND version_label = ? AND lifecycle_state_code = 'ACTIVE'
                    """, (rs, row) -> new ConfigurationSnapshot(rs.getObject("id", UUID.class),
                            rs.getString("project_code"), rs.getString("version_label"), rs.getString("display_name"),
                            rs.getString("project_type_code"), rs.getString("target_kind_code"),
                            rs.getString("environment_code"), rs.getString("configuration_digest")),
                    projectCode, versionLabel));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void createOrder(OrderSnapshot order) {
        jdbc.update("""
                INSERT INTO pis.p18_technical_order
                (id, technical_order_no, case_id, order_kind_code, order_lifecycle_state_code, priority_code,
                 reason_text, ordering_actor_ref, represented_actor_ref, organization_reference, record_version_no,
                 concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, 1, 0, ?, ?)
                """, order.id(), order.orderNo(), order.caseId(), order.orderKindCode(), order.priorityCode(),
                order.reasonText(), order.orderingActorRef(), order.representedActorRef(), order.organizationReference(),
                Timestamp.from(order.createdAt()), order.createdByRef());
    }

    public Optional<OrderSnapshot> order(UUID orderId, String organizationReference) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id, technical_order_no, case_id, order_kind_code, order_lifecycle_state_code,
                           priority_code, reason_text, ordering_actor_ref, represented_actor_ref,
                           organization_reference, record_version_no, concurrency_version, created_at, created_by_ref
                    FROM pis.p18_technical_order
                    WHERE id = ? AND organization_reference = ?
                    """, (rs, row) -> new OrderSnapshot(rs.getObject("id", UUID.class),
                            rs.getString("technical_order_no"), rs.getObject("case_id", UUID.class),
                            rs.getString("order_kind_code"), rs.getString("order_lifecycle_state_code"),
                            rs.getString("priority_code"), rs.getString("reason_text"),
                            rs.getString("ordering_actor_ref"), rs.getString("represented_actor_ref"),
                            rs.getString("organization_reference"), rs.getInt("record_version_no"),
                            rs.getLong("concurrency_version"), rs.getTimestamp("created_at").toInstant(),
                            rs.getString("created_by_ref")), orderId, organizationReference));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<ProjectSnapshot> project(UUID projectId, String organizationReference) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT p.id, p.technical_order_id, p.project_no, p.configuration_id, p.project_type_code,
                           p.project_task_state_code, p.review_state_code, p.receiving_state_code,
                           p.execution_handoff_state_code, p.result_state_code, p.usage_code, p.priority_code,
                           p.reason_text, p.assigned_actor_ref, p.record_version_no, p.concurrency_version,
                           o.case_id, o.organization_reference
                    FROM pis.p18_technical_order_project p
                    JOIN pis.p18_technical_order o ON o.id = p.technical_order_id
                    WHERE p.id = ? AND o.organization_reference = ?
                    """, (rs, row) -> new ProjectSnapshot(rs.getObject("id", UUID.class),
                            rs.getObject("technical_order_id", UUID.class), rs.getString("project_no"),
                            rs.getObject("configuration_id", UUID.class), rs.getString("project_type_code"),
                            rs.getString("project_task_state_code"), rs.getString("review_state_code"),
                            rs.getString("receiving_state_code"), rs.getString("execution_handoff_state_code"),
                            rs.getString("result_state_code"), rs.getString("usage_code"), rs.getString("priority_code"),
                            rs.getString("reason_text"), rs.getString("assigned_actor_ref"), rs.getInt("record_version_no"),
                            rs.getLong("concurrency_version"), rs.getObject("case_id", UUID.class),
                            rs.getString("organization_reference")), projectId, organizationReference));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<ProjectSnapshot> projects(UUID orderId, String organizationReference) {
        return jdbc.query("""
                SELECT p.id, p.technical_order_id, p.project_no, p.configuration_id, p.project_type_code,
                       p.project_task_state_code, p.review_state_code, p.receiving_state_code,
                       p.execution_handoff_state_code, p.result_state_code, p.usage_code, p.priority_code,
                       p.reason_text, p.assigned_actor_ref, p.record_version_no, p.concurrency_version,
                       o.case_id, o.organization_reference
                FROM pis.p18_technical_order_project p
                JOIN pis.p18_technical_order o ON o.id = p.technical_order_id
                WHERE p.technical_order_id = ? AND o.organization_reference = ?
                ORDER BY p.project_no
                """, (rs, row) -> new ProjectSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("technical_order_id", UUID.class), rs.getString("project_no"),
                        rs.getObject("configuration_id", UUID.class), rs.getString("project_type_code"),
                        rs.getString("project_task_state_code"), rs.getString("review_state_code"),
                        rs.getString("receiving_state_code"), rs.getString("execution_handoff_state_code"),
                        rs.getString("result_state_code"), rs.getString("usage_code"), rs.getString("priority_code"),
                        rs.getString("reason_text"), rs.getString("assigned_actor_ref"), rs.getInt("record_version_no"),
                        rs.getLong("concurrency_version"), rs.getObject("case_id", UUID.class),
                        rs.getString("organization_reference")), orderId, organizationReference);
    }

    public Optional<ProjectSnapshot> duplicateCandidate(UUID caseId, UUID actualBlockFormationId, String projectTypeCode,
            String organizationReference) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT p.id, p.technical_order_id, p.project_no, p.configuration_id, p.project_type_code,
                           p.project_task_state_code, p.review_state_code, p.receiving_state_code,
                           p.execution_handoff_state_code, p.result_state_code, p.usage_code, p.priority_code,
                           p.reason_text, p.assigned_actor_ref, p.record_version_no, p.concurrency_version,
                           o.case_id, o.organization_reference
                    FROM pis.p18_technical_order_project p
                    JOIN pis.p18_technical_order o ON o.id = p.technical_order_id
                    JOIN pis.p18_order_target t ON t.project_id = p.id
                    WHERE o.case_id = ? AND o.organization_reference = ? AND p.project_type_code = ?
                      AND t.actual_block_formation_id = ?
                      AND o.order_lifecycle_state_code NOT IN ('CANCELLED', 'COMPLETED')
                    ORDER BY o.created_at DESC
                    LIMIT 1
                    """, (rs, row) -> projectFrom(rs), caseId, organizationReference, projectTypeCode,
                    actualBlockFormationId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void createProject(ProjectSnapshot project, ConfigurationSnapshot configuration, Instant now, String createdByRef) {
        jdbc.update("""
                INSERT INTO pis.p18_technical_order_project
                (id, technical_order_id, project_no, configuration_id, project_type_code, project_task_state_code,
                 review_state_code, receiving_state_code, execution_handoff_state_code, result_state_code, usage_code,
                 priority_code, reason_text, record_version_no, concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, 'P08-SM-007-ST-01', 'PENDING', 'NOT_RECEIVED', 'NOT_HANDOFF', 'WAITING',
                        ?, ?, ?, 1, 0, ?, ?)
                """, project.id(), project.orderId(), project.projectNo(), configuration.id(), configuration.projectTypeCode(),
                project.usageCode(), project.priorityCode(), project.reasonText(), Timestamp.from(now), createdByRef);
    }

    public Optional<BlockTargetSnapshot> validActualBlock(UUID formationId, String organizationReference) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT f.id AS formation_id, f.tissue_block_id, b.block_no, b.case_id, b.block_lifecycle_state_code,
                           b.organization_reference, f.current_valid, f.formation_state_code
                    FROM pis.p17_actual_block_formation f
                    JOIN pis.tissue_block b ON b.id = f.tissue_block_id
                    WHERE f.id = ? AND f.current_valid = TRUE AND f.formation_state_code = 'P17-ACTUAL-BLOCK-ACTIVE'
                      AND b.block_lifecycle_state_code = 'P08-SM-004-ST-03' AND b.organization_reference = ?
                    """, (rs, row) -> new BlockTargetSnapshot(rs.getObject("formation_id", UUID.class),
                            rs.getObject("tissue_block_id", UUID.class), rs.getString("block_no"),
                            rs.getObject("case_id", UUID.class), rs.getString("block_lifecycle_state_code"),
                            rs.getString("organization_reference"), rs.getBoolean("current_valid"),
                            rs.getString("formation_state_code")), formationId, organizationReference));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void bindTarget(UUID projectId, UUID caseId, UUID formationId, int targetVersion, String targetState,
            String actor, String reason, String changeKind, Instant now) {
        if (target(projectId).isPresent()) {
            jdbc.update("""
                    UPDATE pis.p18_order_target SET case_id = ?, actual_block_formation_id = ?, target_state_code = ?,
                        target_version_no = ?, bound_at = ?, bound_by_ref = ? WHERE project_id = ?
                    """, caseId, formationId, targetState, targetVersion, Timestamp.from(now), actor, projectId);
        } else {
            jdbc.update("""
                    INSERT INTO pis.p18_order_target
                    (id, project_id, case_id, target_kind_code, actual_block_formation_id, target_state_code,
                     target_version_no, bound_at, bound_by_ref)
                    VALUES (?, ?, ?, 'ACTUAL_BLOCK', ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), projectId, caseId, formationId, targetState, targetVersion,
                    Timestamp.from(now), actor);
        }
        jdbc.update("""
                INSERT INTO pis.p18_order_target_history
                (id, project_id, case_id, target_kind_code, actual_block_formation_id, target_state_code,
                 target_version_no, change_kind_code, reason_text, occurred_at, recorded_by_ref)
                VALUES (?, ?, ?, 'ACTUAL_BLOCK', ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, caseId, formationId, targetState, targetVersion, changeKind,
                reason, Timestamp.from(now), actor);
    }

    public Optional<TargetSnapshot> target(UUID projectId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id, project_id, case_id, target_kind_code, actual_block_formation_id,
                           target_state_code, target_version_no, bound_at, bound_by_ref
                    FROM pis.p18_order_target WHERE project_id = ?
                    """, (rs, row) -> new TargetSnapshot(rs.getObject("id", UUID.class),
                            rs.getObject("project_id", UUID.class), rs.getObject("case_id", UUID.class),
                            rs.getString("target_kind_code"), rs.getObject("actual_block_formation_id", UUID.class),
                            rs.getString("target_state_code"), rs.getInt("target_version_no"),
                            rs.getTimestamp("bound_at").toInstant(), rs.getString("bound_by_ref")), projectId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void addPlannedOutput(PlannedOutputSnapshot output, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_planned_output
                (id, project_id, sequence_no, output_kind_code, slide_purpose_code, planned_layer_reference,
                 planned_quantity, planned_stain_project_code, planned_usage_code, planned_label_quantity,
                 execution_note, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, output.id(), output.projectId(), output.sequenceNo(), output.outputKindCode(), output.slidePurposeCode(),
                output.plannedLayerReference(), output.plannedQuantity(), output.plannedStainProjectCode(), output.plannedUsageCode(),
                output.plannedLabelQuantity(), output.executionNote(), Timestamp.from(now), output.createdByRef());
    }

    public List<PlannedOutputSnapshot> plannedOutputs(UUID projectId) {
        return jdbc.query("""
                SELECT id, project_id, sequence_no, output_kind_code, slide_purpose_code, planned_layer_reference,
                       planned_quantity, planned_stain_project_code, planned_usage_code, planned_label_quantity,
                       execution_note, created_by_ref
                FROM pis.p18_planned_output WHERE project_id = ? ORDER BY sequence_no
                """, (rs, row) -> new PlannedOutputSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class), rs.getInt("sequence_no"), rs.getString("output_kind_code"),
                        rs.getString("slide_purpose_code"), rs.getString("planned_layer_reference"),
                        rs.getInt("planned_quantity"), rs.getString("planned_stain_project_code"),
                        rs.getString("planned_usage_code"), rs.getInt("planned_label_quantity"),
                        rs.getString("execution_note"), rs.getString("created_by_ref")), projectId);
    }

    public boolean submitProject(UUID projectId, String organizationReference, long expectedVersion, Instant now, String actor) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET project_task_state_code = 'P08-SM-007-ST-02',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.project_task_state_code = 'P08-SM-007-ST-01'
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public boolean submitOrder(UUID orderId, String organizationReference, long expectedVersion, Instant now, String actor) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order SET order_lifecycle_state_code = 'SUBMITTED', submitted_at = ?,
                    record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                  AND order_lifecycle_state_code IN ('DRAFT', 'RETURNED')
                """, Timestamp.from(now), orderId, organizationReference, expectedVersion) == 1;
    }

    public boolean approveProject(UUID projectId, String organizationReference, long expectedVersion, String actor,
            Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET review_state_code = 'APPROVED',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.project_task_state_code = 'P08-SM-007-ST-02'
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public boolean rejectProject(UUID projectId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET review_state_code = 'REJECTED',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.project_task_state_code = 'P08-SM-007-ST-02'
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public void appendReview(UUID projectId, String decision, String reason, String actor, long version, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_project_review
                (id, project_id, decision_code, review_reason, reviewer_actor_ref, reviewed_at, project_version_no)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, decision, reason, actor, Timestamp.from(now), version);
    }

    public boolean receiveProject(UUID projectId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET receiving_state_code = 'RECEIVED',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.review_state_code = 'APPROVED'
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public boolean assignProject(UUID projectId, String organizationReference, long expectedVersion, String actor, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET assigned_actor_ref = ?,
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.receiving_state_code = 'RECEIVED'
                """, actor, projectId, organizationReference, expectedVersion) == 1;
    }

    public boolean handoffProject(UUID projectId, String organizationReference, long expectedVersion, String actor, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET project_task_state_code = 'P08-SM-007-ST-03',
                    execution_handoff_state_code = 'HANDED_OFF', result_state_code = 'WAITING',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.review_state_code = 'APPROVED'
                  AND p.receiving_state_code = 'RECEIVED' AND p.assigned_actor_ref = ?
                """, projectId, organizationReference, expectedVersion, actor) == 1;
    }

    public boolean setResultReferenced(UUID projectId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET result_state_code = 'REFERENCED',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.execution_handoff_state_code = 'HANDED_OFF'
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public boolean closeProject(UUID projectId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET result_state_code = 'CLOSED',
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.result_state_code = 'REFERENCED'
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public boolean cancelProject(UUID projectId, String organizationReference, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE pis.p18_technical_order_project p SET project_task_state_code = 'P08-SM-007-ST-04',
                    result_state_code = CASE WHEN p.result_state_code = 'REFERENCED' THEN 'CLOSED' ELSE p.result_state_code END,
                    record_version_no = p.record_version_no + 1, concurrency_version = p.concurrency_version + 1
                FROM pis.p18_technical_order o
                WHERE p.id = ? AND p.technical_order_id = o.id AND o.organization_reference = ?
                  AND p.concurrency_version = ? AND p.project_task_state_code IN ('P08-SM-007-ST-01', 'P08-SM-007-ST-02')
                """, projectId, organizationReference, expectedVersion) == 1;
    }

    public void appendResponsibility(UUID projectId, String type, String from, String to, String action, String reason,
            String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_project_responsibility_history
                (id, project_id, responsibility_type_code, from_actor_ref, to_actor_ref, action_code, reason_text,
                 occurred_at, recorded_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, type, from, to, action, reason, Timestamp.from(now), actor);
    }

    public void appendChange(UUID projectId, String kind, int version, String summary, String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_project_change
                (id, project_id, change_kind_code, prior_version_no, change_summary, changed_by_ref, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, kind, version, summary, actor, Timestamp.from(now));
    }

    public void appendCancellation(UUID projectId, String kind, String reason, String impact, String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_project_cancellation
                (id, project_id, cancellation_kind_code, reason_text, impact_summary, cancelled_by_ref, cancelled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, kind, reason, impact, actor, Timestamp.from(now));
    }

    public void appendResult(ResultReferenceSnapshot result, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_project_result_reference
                (id, project_id, result_reference_kind_code, result_identity, result_digest, result_environment_code,
                 result_note, referenced_by_ref, referenced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, result.id(), result.projectId(), result.kindCode(), result.resultIdentity(), result.resultDigest(),
                result.environmentCode(), result.note(), result.referencedBy(), Timestamp.from(now));
    }

    public void appendProjectState(UUID projectId, String source, String target, String event, Long expectedVersion,
            long resultingVersion, String actor, String reason, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_project_state_history
                (id, project_id, source_state_code, target_state_code, transition_event_code, expected_version,
                 resulting_version, occurred_at, recorded_by_ref, reason_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, source, target, event, expectedVersion, resultingVersion,
                Timestamp.from(now), actor, reason);
    }

    public void appendOrderState(UUID orderId, String source, String target, String reason, String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p18_order_state_history
                (id, order_id, source_state_code, target_state_code, transition_reason, occurred_at, recorded_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, source, target, reason, Timestamp.from(now), actor);
    }

    public String refreshDerivedOrderState(UUID orderId, String organizationReference, String actor, Instant now) {
        OrderSnapshot order = order(orderId, organizationReference).orElseThrow();
        List<ProjectSnapshot> projects = projects(orderId, organizationReference);
        boolean submitted = !"DRAFT".equals(order.orderLifecycleStateCode());
        boolean returned = projects.stream().anyMatch(project -> "REJECTED".equals(project.reviewStateCode()));
        boolean allApproved = !projects.isEmpty() && projects.stream().allMatch(project -> "APPROVED".equals(project.reviewStateCode()));
        boolean anyHandoff = projects.stream().anyMatch(project -> "HANDED_OFF".equals(project.executionHandoffStateCode()));
        boolean anyWaitingResult = projects.stream().anyMatch(project -> "WAITING".equals(project.resultStateCode())
                && "HANDED_OFF".equals(project.executionHandoffStateCode()));
        boolean allClosed = !projects.isEmpty() && projects.stream().allMatch(project -> "CLOSED".equals(project.resultStateCode())
                || "P08-SM-007-ST-04".equals(project.projectTaskStateCode()));
        boolean anyClosed = projects.stream().anyMatch(project -> "CLOSED".equals(project.resultStateCode()));
        boolean allCancelled = !projects.isEmpty() && projects.stream().allMatch(project -> "P08-SM-007-ST-04".equals(project.projectTaskStateCode()));
        String target = TechnicalOrderStateDerivation.derive(submitted, returned, allApproved, anyHandoff, anyWaitingResult,
                allClosed, anyClosed, allCancelled);
        if (!target.equals(order.orderLifecycleStateCode())) {
            jdbc.update("""
                    UPDATE pis.p18_technical_order SET order_lifecycle_state_code = ?, record_version_no = record_version_no + 1,
                        concurrency_version = concurrency_version + 1, cancelled_at = CASE WHEN ? = 'CANCELLED' THEN ? ELSE cancelled_at END
                    WHERE id = ? AND organization_reference = ?
                    """, target, target, Timestamp.from(now), orderId, organizationReference);
            appendOrderState(orderId, order.orderLifecycleStateCode(), target, "derived from project facts", actor, now);
        }
        return target;
    }

    public List<OrderListRow> list(String organizationReference) {
        return jdbc.query("""
                SELECT o.id, o.technical_order_no, o.case_id, o.order_lifecycle_state_code, o.priority_code,
                       o.ordering_actor_ref, o.created_at, COUNT(p.id) AS project_count
                FROM pis.p18_technical_order o
                LEFT JOIN pis.p18_technical_order_project p ON p.technical_order_id = o.id
                WHERE o.organization_reference = ?
                GROUP BY o.id, o.technical_order_no, o.case_id, o.order_lifecycle_state_code, o.priority_code,
                         o.ordering_actor_ref, o.created_at
                ORDER BY o.created_at DESC, o.technical_order_no DESC
                LIMIT 100
                """, (rs, row) -> new OrderListRow(rs.getObject("id", UUID.class), rs.getString("technical_order_no"),
                        rs.getObject("case_id", UUID.class), rs.getString("order_lifecycle_state_code"),
                        rs.getString("priority_code"), rs.getString("ordering_actor_ref"),
                        rs.getTimestamp("created_at").toInstant(), rs.getLong("project_count")), organizationReference);
    }

    public Optional<IdempotentReference> idempotent(String operation, String key, String digest) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT payload_digest, result_object_id FROM pis.p16_idempotency_key
                    WHERE operation_code = ? AND idempotency_key = ?
                    """, (rs, row) -> {
                        if (!digest.equals(rs.getString("payload_digest"))) throw new IdempotencyConflictException();
                        return new IdempotentReference(rs.getObject("result_object_id", UUID.class));
                    }, operation, key));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void recordIdempotent(String operation, String key, String digest, UUID resultObjectId, String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis.p16_idempotency_key
                (id, operation_code, idempotency_key, payload_digest, result_object_id, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operation, key, digest, resultObjectId, Timestamp.from(now), actor);
    }

    private ProjectSnapshot projectFrom(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProjectSnapshot(rs.getObject("id", UUID.class), rs.getObject("technical_order_id", UUID.class),
                rs.getString("project_no"), rs.getObject("configuration_id", UUID.class), rs.getString("project_type_code"),
                rs.getString("project_task_state_code"), rs.getString("review_state_code"), rs.getString("receiving_state_code"),
                rs.getString("execution_handoff_state_code"), rs.getString("result_state_code"), rs.getString("usage_code"),
                rs.getString("priority_code"), rs.getString("reason_text"), rs.getString("assigned_actor_ref"),
                rs.getInt("record_version_no"), rs.getLong("concurrency_version"), rs.getObject("case_id", UUID.class),
                rs.getString("organization_reference"));
    }

    public record ConfigurationSnapshot(UUID id, String projectCode, String versionLabel, String displayName,
            String projectTypeCode, String targetKindCode, String environmentCode, String configurationDigest) { }
    public record OrderSnapshot(UUID id, String orderNo, UUID caseId, String orderKindCode, String orderLifecycleStateCode,
            String priorityCode, String reasonText, String orderingActorRef, String representedActorRef,
            String organizationReference, int recordVersionNo, long concurrencyVersion, Instant createdAt, String createdByRef) { }
    public record ProjectSnapshot(UUID id, UUID orderId, String projectNo, UUID configurationId, String projectTypeCode,
            String projectTaskStateCode, String reviewStateCode, String receivingStateCode, String executionHandoffStateCode,
            String resultStateCode, String usageCode, String priorityCode, String reasonText, String assignedActorRef,
            int recordVersionNo, long concurrencyVersion, UUID caseId, String organizationReference) { }
    public record BlockTargetSnapshot(UUID formationId, UUID tissueBlockId, String blockNo, UUID caseId,
            String blockStateCode, String organizationReference, boolean currentValid, String formationStateCode) { }
    public record TargetSnapshot(UUID id, UUID projectId, UUID caseId, String targetKindCode, UUID actualBlockFormationId,
            String targetStateCode, int targetVersionNo, Instant boundAt, String boundByRef) { }
    public record PlannedOutputSnapshot(UUID id, UUID projectId, int sequenceNo, String outputKindCode,
            String slidePurposeCode, String plannedLayerReference, int plannedQuantity, String plannedStainProjectCode,
            String plannedUsageCode, int plannedLabelQuantity, String executionNote, String createdByRef) { }
    public record ResultReferenceSnapshot(UUID id, UUID projectId, String kindCode, String resultIdentity,
            String resultDigest, String environmentCode, String note, String referencedBy) { }
    public record OrderListRow(UUID id, String orderNo, UUID caseId, String stateCode, String priorityCode,
            String orderingActorRef, Instant createdAt, long projectCount) { }
    public record IdempotentReference(UUID resultObjectId) { }

    public static final class IdempotencyConflictException extends RuntimeException { }

    private static final class TechnicalOrderStateDerivation {
        private static String derive(boolean submitted, boolean returned, boolean allApproved, boolean anyHandoff,
                boolean anyWaitingResult, boolean allClosed, boolean anyClosed, boolean allCancelled) {
            if (allCancelled) return "CANCELLED";
            if (allClosed) return "COMPLETED";
            if (anyHandoff && anyWaitingResult) return "WAITING_RESULT";
            if (anyHandoff) return "IN_PROGRESS";
            if (anyClosed) return "PARTIALLY_COMPLETED";
            if (returned) return "RETURNED";
            if (allApproved) return "ACCEPTED";
            return submitted ? "SUBMITTED" : "DRAFT";
        }
    }
}

package com.hanjisang.pis.v2.technical.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.technical.domain.TechnicalOrder;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderItem;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderItemResult;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderStatus;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderTarget;
import com.hanjisang.pis.v2.technical.domain.TechnicalOutputType;
import com.hanjisang.pis.v2.technical.domain.TechnicalProject;
import com.hanjisang.pis.v2.technical.domain.TechnicalTargetType;

@Repository
public class JdbcV2TechnicalOrderRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public JdbcV2TechnicalOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        Boolean databaseIsPostgres = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres"));
        this.postgres = Boolean.TRUE.equals(databaseIsPostgres);
    }

    public String allocateOrderNo(String organizationReference, UUID caseId) {
        jdbcTemplate.update("""
                MERGE INTO pis_v2.technical_order_sequence AS target
                USING (VALUES (?, CAST(? AS UUID), ?)) AS incoming
                    (organization_reference, case_id, next_serial)
                ON target.organization_reference = incoming.organization_reference
                   AND target.case_id = incoming.case_id
                WHEN NOT MATCHED THEN INSERT (organization_reference, case_id, next_serial)
                    VALUES (incoming.organization_reference, incoming.case_id, incoming.next_serial)
                """, organizationReference, caseId, 1L);
        Long serial = jdbcTemplate.query("""
                SELECT next_serial FROM pis_v2.technical_order_sequence
                WHERE organization_reference = ? AND case_id = ? FOR UPDATE
                """, rs -> rs.next() ? rs.getLong(1) : null, organizationReference, caseId);
        if (serial == null) throw new IllegalStateException("无法分配技术医嘱业务编号");
        if (jdbcTemplate.update("""
                UPDATE pis_v2.technical_order_sequence SET next_serial = next_serial + 1
                WHERE organization_reference = ? AND case_id = ? AND next_serial = ?
                """, organizationReference, caseId, serial) != 1) {
            throw new IllegalStateException("技术医嘱业务编号并发更新失败");
        }
        return "TO" + String.format("%03d", serial);
    }

    public List<TechnicalProject> findProjects(String organizationReference, UUID businessTypeId,
            boolean enabledOnly) {
        String enabledClause = enabledOnly ? " AND enabled = TRUE" : "";
        return jdbcTemplate.query("""
                SELECT id, organization_reference, business_type_id, project_code, project_name, enabled,
                       allowed_target_types, produces_slide, produces_block, produces_structured_result,
                       default_slide_type, CAST(parameters_schema AS VARCHAR) AS parameters_schema,
                       CAST(result_schema AS VARCHAR) AS result_schema, CAST(fee_mapping AS VARCHAR) AS fee_mapping,
                       CAST(display_configuration AS VARCHAR) AS display_configuration,
                       required_before_sign_out_default, configuration_version
                FROM pis_v2.technical_project
                WHERE organization_reference = ? AND business_type_id = ?
                """ + enabledClause + " ORDER BY project_code", (rs, rowNum) -> toProject(rs),
                organizationReference, businessTypeId);
    }

    public List<TechnicalProject> findAllProjects(String organizationReference, boolean enabledOnly) {
        String enabledClause = enabledOnly ? " AND enabled = TRUE" : "";
        return jdbcTemplate.query("""
                SELECT id, organization_reference, business_type_id, project_code, project_name, enabled,
                       allowed_target_types, produces_slide, produces_block, produces_structured_result,
                       default_slide_type, CAST(parameters_schema AS VARCHAR) AS parameters_schema,
                       CAST(result_schema AS VARCHAR) AS result_schema, CAST(fee_mapping AS VARCHAR) AS fee_mapping,
                       CAST(display_configuration AS VARCHAR) AS display_configuration,
                       required_before_sign_out_default, configuration_version
                FROM pis_v2.technical_project
                WHERE organization_reference = ?
                """ + enabledClause + " ORDER BY project_code", (rs, rowNum) -> toProject(rs), organizationReference);
    }

    public Optional<TechnicalProject> findProject(UUID projectId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, organization_reference, business_type_id, project_code, project_name, enabled,
                       allowed_target_types, produces_slide, produces_block, produces_structured_result,
                       default_slide_type, CAST(parameters_schema AS VARCHAR) AS parameters_schema,
                       CAST(result_schema AS VARCHAR) AS result_schema, CAST(fee_mapping AS VARCHAR) AS fee_mapping,
                       CAST(display_configuration AS VARCHAR) AS display_configuration,
                       required_before_sign_out_default, configuration_version
                FROM pis_v2.technical_project WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toProject(rs)) : Optional.empty(), projectId, organizationReference);
    }

    public void insertProject(TechnicalProject project, Instant now, String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_project
                    (id, organization_reference, business_type_id, project_code, project_name, enabled,
                     allowed_target_types, produces_slide, produces_block, produces_structured_result,
                     default_slide_type, parameters_schema, result_schema, fee_mapping, display_configuration,
                     required_before_sign_out_default, configuration_version, created_at, created_by_ref,
                     updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, project.id(), project.organizationReference(), project.businessTypeId(), project.code(),
                project.name(), project.enabled(), project.allowedTargetTypesCode(), project.producesSlide(),
                project.producesBlock(), project.producesStructuredResult(), project.defaultSlideType(),
                json(project.parametersSchema()), json(project.resultSchema()), json(project.feeMapping()),
                json(project.displayConfiguration()), project.requiredBeforeSignOutDefault(),
                project.configurationVersion(), Timestamp.from(now), actorRef, Timestamp.from(now), actorRef);
    }

    public boolean lockOrder(UUID orderId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.technical_order
                WHERE id = ? AND organization_reference = ? FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), orderId, organizationReference);
    }

    public boolean lockItem(UUID itemId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT i.id FROM pis_v2.technical_order_item i
                JOIN pis_v2.technical_order o ON o.id = i.order_id
                WHERE i.id = ? AND o.organization_reference = ? FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), itemId, organizationReference);
    }

    public Optional<TechnicalOrder> findOrder(UUID orderId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, order_no, diagnosis_id, case_id, required_before_sign_out, status_code,
                       cancelled_at, cancelled_by_ref, cancellation_reason, concurrency_version
                FROM pis_v2.technical_order WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toOrder(rs)) : Optional.empty(), orderId, organizationReference);
    }

    public List<UUID> findOrderIdsByDiagnosis(UUID diagnosisId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.technical_order
                WHERE diagnosis_id = ? AND organization_reference = ? ORDER BY created_at, id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), diagnosisId, organizationReference);
    }

    public List<UUID> findOrderIdsForWorkbench(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.technical_order
                WHERE organization_reference = ? AND status_code NOT IN ('COMPLETED', 'CANCELLED')
                ORDER BY created_at, id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), organizationReference);
    }

    public void insertOrder(TechnicalOrder order, String organizationReference, Instant now, String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order
                    (id, organization_reference, order_no, diagnosis_id, case_id, required_before_sign_out,
                     status_code, concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), organizationReference, order.orderNo(), order.diagnosisId(), order.caseId(),
                order.requiredBeforeSignOut(), order.status().name(), order.version(), Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef);
    }

    public boolean updateOrder(TechnicalOrder order, String organizationReference, long expectedVersion,
            Instant now, String actorRef) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.technical_order
                   SET status_code = ?, cancelled_at = ?, cancelled_by_ref = ?, cancellation_reason = ?,
                       concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                """, order.status().name(), timestamp(order.cancelledAt()), order.cancelledBy(),
                order.cancellationReason(), order.version(), Timestamp.from(now), actorRef, order.id(),
                organizationReference, expectedVersion) == 1;
    }

    public void insertItem(TechnicalOrderItem item, String organizationReference, Instant now, String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order_item
                    (id, order_id, technical_project_id, project_code_snapshot, project_name_snapshot,
                     project_configuration_version, quantity, parameters, note, concurrency_version,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, item.id(), item.orderId(), item.project().id(), item.project().code(), item.project().name(),
                item.project().configurationVersion(), item.quantity(), json(item.parameters()), item.note(),
                item.version(), Timestamp.from(now), actorRef, Timestamp.from(now), actorRef);
    }

    public void insertTarget(TechnicalOrderTarget target, String organizationReference, Instant now, String actorRef) {
        String targetColumn = switch (target.targetType()) {
            case CASE -> "case_target_id";
            case SPECIMEN -> "specimen_target_id";
            case BLOCK -> "block_target_id";
            case SLIDE -> "slide_target_id";
        };
        jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order_target
                    (id, item_id, case_id, target_type, %s, target_display_code, concurrency_version,
                     created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """.formatted(targetColumn), target.id(), target.itemId(), target.caseId(),
                target.targetType().name(), target.targetId(), target.displayCode(), Timestamp.from(now), actorRef);
    }

    public boolean insertOutput(UUID outputId, UUID itemId, UUID targetId, TechnicalOutputType kind, UUID producedId,
            int occurrenceNo, Instant now, String actorRef) {
        String outputColumn = switch (kind) {
            case GROSSING -> "grossing_output_id";
            case BLOCK -> "block_output_id";
            case SLIDE -> "slide_output_id";
            case RESULT -> "result_output_id";
        };
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order_output
                    (id, item_id, target_id, output_kind, %s, occurrence_no, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """.formatted(outputColumn), outputId, itemId, targetId, kind.name(), producedId, occurrenceNo,
                Timestamp.from(now), actorRef) == 1;
    }

    public Optional<TechnicalOrderItemResult> findResult(UUID itemId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT r.id, r.item_id, CAST(r.result_schema_snapshot AS VARCHAR) AS result_schema_snapshot,
                       CAST(r.result_data AS VARCHAR) AS result_data, r.concurrency_version, r.entered_at,
                       r.entered_by_ref
                FROM pis_v2.technical_order_item_result r
                JOIN pis_v2.technical_order_item i ON i.id = r.item_id
                JOIN pis_v2.technical_order o ON o.id = i.order_id
                WHERE r.item_id = ? AND o.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toResult(rs)) : Optional.empty(), itemId, organizationReference);
    }

    public void insertResult(TechnicalOrderItemResult result, String organizationReference) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order_item_result
                    (id, item_id, result_schema_snapshot, result_data, concurrency_version, entered_at, entered_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, result.id(), result.itemId(), json(result.schemaSnapshot()), json(result.data()), result.version(),
                Timestamp.from(result.enteredAt()), result.enteredBy());
    }

    public boolean updateResult(TechnicalOrderItemResult result, long expectedVersion, String organizationReference) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.technical_order_item_result
                   SET result_data = ?, concurrency_version = ?, entered_at = ?, entered_by_ref = ?
                 WHERE id = ? AND concurrency_version = ?
                """, json(result.data()), result.version(), Timestamp.from(result.enteredAt()), result.enteredBy(),
                result.id(), expectedVersion) == 1;
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String key) {
        return jdbcTemplate.query("""
                SELECT id, payload_digest, result_kind_code, result_entity_id
                FROM pis_v2.technical_order_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getObject("id", UUID.class),
                rs.getString("payload_digest"), rs.getString("result_kind_code"),
                rs.getObject("result_entity_id", UUID.class))) : Optional.empty(), operationCode, key);
    }

    public boolean insertIdempotency(String operationCode, String key, String digest, String resultKind,
            UUID resultEntityId, String actorRef, Instant now) {
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.technical_order_idempotency
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code, result_entity_id,
                     created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), operationCode, key, digest, resultKind, resultEntityId, Timestamp.from(now),
                actorRef) == 1;
    }

    public Optional<TechnicalOrderItem> findItem(UUID itemId, String organizationReference) {
        return findOrderSnapshotByItemForCommand(itemId, organizationReference).flatMap(snapshot -> snapshot.items().stream()
                .filter(item -> item.item().id().equals(itemId)).map(ItemSnapshot::item).findFirst());
    }

    public Optional<OrderSnapshot> findOrderSnapshot(UUID orderId, String organizationReference) {
        return findOrder(orderId, organizationReference).map(order -> buildSnapshot(order, organizationReference));
    }

    public List<OrderSnapshot> findOrderSnapshotsByDiagnosis(UUID diagnosisId, String organizationReference) {
        return findOrderIdsByDiagnosis(diagnosisId, organizationReference).stream()
                .map(id -> findOrderSnapshot(id, organizationReference).orElseThrow())
                .toList();
    }

    public List<OrderSnapshot> findWorkbenchSnapshots(String organizationReference) {
        return findAllOrderIds(organizationReference).stream()
                .map(id -> findOrderSnapshot(id, organizationReference).orElseThrow())
                .filter(snapshot -> snapshot.derivedStatus() != TechnicalOrderStatus.CANCELLED)
                .toList();
    }

    private List<UUID> findAllOrderIds(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.technical_order
                WHERE organization_reference = ?
                ORDER BY created_at DESC, id
                FETCH FIRST 100 ROWS ONLY
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), organizationReference);
    }

    public Optional<OrderSnapshot> findOrderSnapshotByItemForCommand(UUID itemId, String organizationReference) {
        UUID orderId = jdbcTemplate.query("""
                SELECT o.id FROM pis_v2.technical_order_item i
                JOIN pis_v2.technical_order o ON o.id = i.order_id
                WHERE i.id = ? AND o.organization_reference = ?
                """, (ResultSetExtractor<Optional<UUID>>) rs -> rs.next()
                        ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), itemId,
                organizationReference).orElse(null);
        return orderId == null ? Optional.empty() : findOrderSnapshot(orderId, organizationReference);
    }

    public boolean itemBelongsToCurrentResponsibility(UUID itemId, String doctorReference,
            String organizationReference) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pis_v2.technical_order_item i
                JOIN pis_v2.technical_order o ON o.id = i.order_id
                JOIN pis_v2.diagnosis d ON d.id = o.diagnosis_id
                JOIN pis_v2.responsibility_unit r ON r.diagnosis_id = d.id
                WHERE i.id = ? AND o.organization_reference = ?
                  AND r.doctor_id = ? AND r.completed_at IS NULL AND r.ended_at IS NULL
                """, Integer.class, itemId, organizationReference, doctorReference);
        return count != null && count > 0;
    }

    private OrderSnapshot buildSnapshot(TechnicalOrder order, String organizationReference) {
        List<ItemSnapshot> items = jdbcTemplate.query("""
                SELECT i.id, i.order_id, i.technical_project_id, i.project_code_snapshot, i.project_name_snapshot,
                       i.project_configuration_version, i.quantity, CAST(i.parameters AS VARCHAR) AS parameters,
                       i.note, i.concurrency_version, p.enabled, p.business_type_id,
                       p.allowed_target_types, p.produces_slide, p.produces_block, p.produces_structured_result,
                       p.default_slide_type, CAST(p.parameters_schema AS VARCHAR) AS parameters_schema,
                       CAST(p.result_schema AS VARCHAR) AS result_schema, CAST(p.fee_mapping AS VARCHAR) AS fee_mapping,
                       CAST(p.display_configuration AS VARCHAR) AS display_configuration,
                       p.required_before_sign_out_default
                FROM pis_v2.technical_order_item i
                JOIN pis_v2.technical_project p ON p.id = i.technical_project_id
                WHERE i.order_id = ? ORDER BY i.created_at, i.id
                """, (rs, rowNum) -> toItemSnapshot(rs, order, organizationReference), order.id());
        TechnicalOrderStatus derived = deriveStatus(order, items);
        boolean blocking = order.requiredBeforeSignOut() && derived != TechnicalOrderStatus.COMPLETED
                && derived != TechnicalOrderStatus.CANCELLED;
        return new OrderSnapshot(order, derived, blocking, items);
    }

    private ItemSnapshot toItemSnapshot(ResultSet rs, TechnicalOrder order, String organizationReference)
            throws SQLException {
        TechnicalProject project = TechnicalProject.create(rs.getObject("technical_project_id", UUID.class),
                organizationReference, rs.getObject("business_type_id", UUID.class),
                rs.getString("project_code_snapshot"), rs.getString("project_name_snapshot"),
                rs.getBoolean("enabled"), rs.getString("allowed_target_types"), rs.getBoolean("produces_slide"),
                rs.getBoolean("produces_block"), rs.getBoolean("produces_structured_result"),
                rs.getString("default_slide_type"), rs.getString("parameters_schema"), rs.getString("result_schema"),
                rs.getString("fee_mapping"), rs.getString("display_configuration"),
                rs.getBoolean("required_before_sign_out_default"), rs.getInt("project_configuration_version"));
        TechnicalOrderItem item = new TechnicalOrderItem(rs.getObject("id", UUID.class), order.id(), project,
                rs.getInt("quantity"), rs.getString("parameters"), rs.getString("note"),
                rs.getLong("concurrency_version"));
        List<TargetSnapshot> targets = jdbcTemplate.query("""
                SELECT id, item_id, case_id, target_type,
                       CASE target_type
                           WHEN 'CASE' THEN case_target_id
                           WHEN 'SPECIMEN' THEN specimen_target_id
                           WHEN 'BLOCK' THEN block_target_id
                           WHEN 'SLIDE' THEN slide_target_id
                       END AS target_object_id,
                       target_display_code
                FROM pis_v2.technical_order_target WHERE item_id = ? ORDER BY created_at, id
                """, (targetRs, rowNum) -> new TargetSnapshot(new TechnicalOrderTarget(
                targetRs.getObject("id", UUID.class), targetRs.getObject("item_id", UUID.class),
                targetRs.getObject("case_id", UUID.class), TechnicalTargetType.valueOf(targetRs.getString("target_type")),
                targetRs.getObject("target_object_id", UUID.class), targetRs.getString("target_display_code"))), item.id());
        List<OutputSnapshot> outputs = jdbcTemplate.query("""
                SELECT id, target_id, output_kind,
                       CASE output_kind
                           WHEN 'GROSSING' THEN grossing_output_id
                           WHEN 'BLOCK' THEN block_output_id
                           WHEN 'SLIDE' THEN slide_output_id
                           WHEN 'RESULT' THEN result_output_id
                       END AS output_object_id,
                       occurrence_no
                FROM pis_v2.technical_order_output WHERE item_id = ? ORDER BY created_at, id
                """, (outputRs, rowNum) -> new OutputSnapshot(outputRs.getObject("id", UUID.class),
                outputRs.getObject("target_id", UUID.class), TechnicalOutputType.valueOf(outputRs.getString("output_kind")),
                outputRs.getObject("output_object_id", UUID.class), outputRs.getInt("occurrence_no")), item.id());
        TechnicalOrderItemResult result = findResult(item.id(), organizationReference).orElse(null);
        int expected = targets.size() * item.quantity();
        boolean complete = itemComplete(item.project(), expected, outputs, result);
        int completed = completedCount(item.project(), outputs, result);
        TechnicalItemStatus status = complete ? TechnicalItemStatus.COMPLETED
                : completed > 0 || !outputs.isEmpty() || result != null ? TechnicalItemStatus.EXECUTING
                        : TechnicalItemStatus.PENDING;
        return new ItemSnapshot(item, targets, outputs, result, expected, completed, status);
    }

    private boolean itemComplete(TechnicalProject project, int expected, List<OutputSnapshot> outputs,
            TechnicalOrderItemResult result) {
        if (expected == 0) return false;
        boolean slideComplete = !project.producesSlide() || outputs.stream()
                .filter(output -> output.kind() == TechnicalOutputType.SLIDE).count() >= expected
                && outputs.stream().filter(output -> output.kind() == TechnicalOutputType.SLIDE)
                        .allMatch(output -> outputCompleted(output.outputId()));
        boolean blockComplete = !project.producesBlock() || outputs.stream()
                .filter(output -> output.kind() == TechnicalOutputType.BLOCK).count() >= expected;
        boolean resultComplete = !project.producesStructuredResult() || result != null;
        return slideComplete && blockComplete && resultComplete;
    }

    private int completedCount(TechnicalProject project, List<OutputSnapshot> outputs,
            TechnicalOrderItemResult result) {
        int count = 0;
        if (project.producesSlide()) {
            count += (int) outputs.stream().filter(output -> output.kind() == TechnicalOutputType.SLIDE)
                    .filter(output -> outputCompleted(output.outputId())).count();
        }
        if (project.producesBlock()) {
            count += (int) outputs.stream().filter(output -> output.kind() == TechnicalOutputType.BLOCK).count();
        }
        if (project.producesStructuredResult() && result != null) count++;
        return count;
    }

    private boolean outputCompleted(UUID outputId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.slide WHERE id = ? AND completed_at IS NOT NULL AND deleted_at IS NULL
                """, Integer.class, outputId);
        return count != null && count == 1;
    }

    private static TechnicalOrderStatus deriveStatus(TechnicalOrder order, List<ItemSnapshot> items) {
        if (order.status() == TechnicalOrderStatus.CANCELLED) return TechnicalOrderStatus.CANCELLED;
        if (!items.isEmpty() && items.stream().allMatch(item -> item.status() == TechnicalItemStatus.COMPLETED)) {
            return TechnicalOrderStatus.COMPLETED;
        }
        if (order.status() == TechnicalOrderStatus.EXECUTING
                || items.stream().anyMatch(item -> item.status() != TechnicalItemStatus.PENDING)) {
            return TechnicalOrderStatus.EXECUTING;
        }
        return TechnicalOrderStatus.PENDING;
    }

    private TechnicalProject toProject(ResultSet rs) throws SQLException {
        return TechnicalProject.create(rs.getObject("id", UUID.class), rs.getString("organization_reference"),
                rs.getObject("business_type_id", UUID.class), rs.getString("project_code"),
                rs.getString("project_name"), rs.getBoolean("enabled"), rs.getString("allowed_target_types"),
                rs.getBoolean("produces_slide"), rs.getBoolean("produces_block"),
                rs.getBoolean("produces_structured_result"), rs.getString("default_slide_type"),
                rs.getString("parameters_schema"), rs.getString("result_schema"), rs.getString("fee_mapping"),
                rs.getString("display_configuration"), rs.getBoolean("required_before_sign_out_default"),
                rs.getInt("configuration_version"));
    }

    private TechnicalOrder toOrder(ResultSet rs) throws SQLException {
        return TechnicalOrder.persisted(rs.getObject("id", UUID.class), rs.getString("order_no"),
                rs.getObject("diagnosis_id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getBoolean("required_before_sign_out"), TechnicalOrderStatus.valueOf(rs.getString("status_code")),
                instant(rs, "cancelled_at"), rs.getString("cancelled_by_ref"), rs.getString("cancellation_reason"),
                rs.getLong("concurrency_version"));
    }

    private TechnicalOrderItemResult toResult(ResultSet rs) throws SQLException {
        return TechnicalOrderItemResult.persisted(rs.getObject("id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getString("result_schema_snapshot"), rs.getString("result_data"),
                rs.getLong("concurrency_version"), rs.getTimestamp("entered_at").toInstant(),
                rs.getString("entered_by_ref"));
    }

    private Object json(String value) {
        return value == null ? null : postgres ? new SqlParameterValue(Types.OTHER, value) : value;
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record IdempotencyResult(UUID id, String payloadDigest, String resultKind, UUID resultEntityId) { }
    public record OrderSnapshot(TechnicalOrder order, TechnicalOrderStatus derivedStatus, boolean blocking,
            List<ItemSnapshot> items) { }
    public record ItemSnapshot(TechnicalOrderItem item, List<TargetSnapshot> targets, List<OutputSnapshot> outputs,
            TechnicalOrderItemResult result, int expectedCount, int completedCount, TechnicalItemStatus status) { }
    public record TargetSnapshot(TechnicalOrderTarget target) { }
    public record OutputSnapshot(UUID id, UUID targetId, TechnicalOutputType kind, UUID outputId, int occurrenceNo) { }
    public enum TechnicalItemStatus { PENDING, EXECUTING, COMPLETED }
}

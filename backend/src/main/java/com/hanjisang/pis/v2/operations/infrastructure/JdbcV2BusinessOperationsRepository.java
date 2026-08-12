package com.hanjisang.pis.v2.operations.infrastructure;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence for independent supporting business facts. */
@Repository
public class JdbcV2BusinessOperationsRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2BusinessOperationsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean belongs(String entity, UUID id, String organization) {
        if (id == null) return false;
        if ("DIGITAL_SLIDE".equals(entity)) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pis_v2.digital_slide ds
                    JOIN pis_v2.pathology_case pc ON pc.id = ds.case_id
                    WHERE ds.id = ? AND pc.organization_reference = ?
                    """, Integer.class, id, organization);
            return count != null && count == 1;
        }
        String table = switch (entity) {
            case "CASE" -> "pathology_case";
            case "REPORT" -> "report";
            case "EQUIPMENT" -> "equipment";
            case "CATALOG" -> "consumable_catalog";
            case "BATCH" -> "consumable_batch";
            case "PROCUREMENT" -> "procurement_request";
            case "SPACE" -> "department_space";
            case "CRITICAL_VALUE" -> "critical_value";
            case "PACKAGE" -> "logistics_package";
            case "MOLECULAR_PROJECT" -> "molecular_project";
            case "REGIONAL_SHARE" -> "regional_share";
            case "MIGRATION_JOB" -> "migration_job";
            default -> throw new IllegalArgumentException("Unsupported V34 entity: " + entity);
        };
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2." + table
                + " WHERE id = ? AND organization_reference = ?", Integer.class, id, organization);
        return count != null && count == 1;
    }

    public Map<String, List<Map<String, Object>>> overview(String organization) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("schedules", rows("SELECT id AS \"id\", staff_reference AS \"staffReference\", schedule_date AS \"scheduleDate\", shift_code AS \"shiftCode\", work_area AS \"workArea\" FROM pis_v2.staff_schedule WHERE organization_reference = ? ORDER BY schedule_date DESC", organization));
        result.put("procurements", rows("SELECT id AS \"id\", request_no AS \"requestNo\", department_reference AS \"departmentReference\", reason AS \"reason\", status_code AS \"statusCode\", requested_at AS \"requestedAt\" FROM pis_v2.procurement_request WHERE organization_reference = ? ORDER BY requested_at DESC", organization));
        result.put("requisitions", rows("SELECT id AS \"id\", request_no AS \"requestNo\", department_reference AS \"departmentReference\", purpose AS \"purpose\", status_code AS \"statusCode\", requested_at AS \"requestedAt\" FROM pis_v2.consumable_requisition WHERE organization_reference = ? ORDER BY requested_at DESC", organization));
        result.put("distributions", rows("SELECT id AS \"id\", report_id AS \"reportId\", target_code AS \"targetCode\", status_code AS \"statusCode\", retry_count AS \"retryCount\", last_error AS \"lastError\", requested_at AS \"requestedAt\" FROM pis_v2.report_distribution WHERE organization_reference = ? ORDER BY requested_at DESC", organization));
        result.put("packages", rows("SELECT id AS \"id\", case_id AS \"caseId\", courier_company AS \"courierCompany\", tracking_no AS \"trackingNo\", recipient_reference AS \"recipientReference\", status_code AS \"statusCode\", created_at AS \"createdAt\" FROM pis_v2.logistics_package WHERE organization_reference = ? ORDER BY created_at DESC", organization));
        result.put("molecularProjects", rows("SELECT id AS \"id\", project_code AS \"projectCode\", project_name AS \"projectName\", project_type_code AS \"projectTypeCode\", enabled AS \"enabled\" FROM pis_v2.molecular_project WHERE organization_reference = ? ORDER BY project_code", organization));
        result.put("molecularTests", rows("SELECT id AS \"id\", case_id AS \"caseId\", detection_no AS \"detectionNo\", status_code AS \"statusCode\", structured_result AS \"structuredResult\", analysis_result AS \"analysisResult\", created_at AS \"createdAt\" FROM pis_v2.molecular_test WHERE organization_reference = ? ORDER BY created_at DESC", organization));
        result.put("digitalArchives", rows("SELECT id AS \"id\", digital_slide_id AS \"digitalSlideId\", pathology_no AS \"pathologyNo\", slide_no AS \"slideNo\", storage_tier AS \"storageTier\", status_code AS \"statusCode\", imported_at AS \"importedAt\" FROM pis_v2.digital_slide_archive WHERE organization_reference = ? ORDER BY imported_at DESC", organization));
        result.put("regionalShares", rows("SELECT id AS \"id\", case_id AS \"caseId\", receiving_organization AS \"receivingOrganization\", receiving_doctor AS \"receivingDoctor\", expires_at AS \"expiresAt\", status_code AS \"statusCode\", requested_at AS \"requestedAt\" FROM pis_v2.regional_share WHERE organization_reference = ? ORDER BY requested_at DESC", organization));
        result.put("income", rows("SELECT id AS \"id\", case_id AS \"caseId\", project_code AS \"projectCode\", amount AS \"amount\", occurred_at AS \"occurredAt\", source_reference AS \"sourceReference\" FROM pis_v2.income_fact WHERE organization_reference = ? ORDER BY occurred_at DESC", organization));
        result.put("migrationJobs", rows("SELECT j.id AS \"id\", j.source_code AS \"sourceCode\", j.mode_code AS \"modeCode\", j.status_code AS \"statusCode\", j.created_at AS \"createdAt\", COUNT(DISTINCT r.id) AS \"recordCount\", COUNT(DISTINCT e.id) AS \"errorCount\" FROM pis_v2.migration_job j LEFT JOIN pis_v2.migration_record r ON r.job_id = j.id LEFT JOIN pis_v2.migration_error e ON e.job_id = j.id WHERE j.organization_reference = ? GROUP BY j.id, j.source_code, j.mode_code, j.status_code, j.created_at ORDER BY j.created_at DESC", organization));
        return result;
    }

    private List<Map<String, Object>> rows(String sql, String organization) {
        return jdbc.queryForList(sql, organization);
    }

    public List<NotificationRow> notifications(String recipient, String organization) {
        return jdbc.query("""
                SELECT id, recipient_reference, type_code, title, body, business_path, priority_code, created_at, read_at
                  FROM pis_v2.notification WHERE recipient_reference = ? AND organization_reference = ?
                 ORDER BY read_at NULLS FIRST, created_at DESC
                """, (rs, n) -> new NotificationRow(rs.getObject("id", UUID.class), rs.getString("recipient_reference"),
                rs.getString("type_code"), rs.getString("title"), rs.getString("body"), rs.getString("business_path"),
                rs.getString("priority_code"), instant(rs, "created_at"), instant(rs, "read_at")), recipient, organization);
    }

    public boolean markNotificationRead(UUID id, String recipient, String organization, Instant at) {
        return jdbc.update("""
                UPDATE pis_v2.notification SET read_at = COALESCE(read_at, ?)
                 WHERE id = ? AND recipient_reference = ? AND organization_reference = ?
                """, Timestamp.from(at), id, recipient, organization) == 1;
    }

    public UUID insertSchedule(ScheduleCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.staff_schedule
                    (id, staff_reference, schedule_date, shift_code, work_area, note,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, item.staffReference(), Date.valueOf(item.scheduleDate()), item.shiftCode(), item.workArea(),
                item.note(), organization, Timestamp.from(now), actor);
        return id;
    }

    public List<ScheduleRow> schedules(String staffReference, LocalDate from, LocalDate to, String organization) {
        String sql = """
                SELECT id, staff_reference, schedule_date, shift_code, work_area, note, created_at, created_by_ref
                  FROM pis_v2.staff_schedule
                 WHERE organization_reference = ? AND schedule_date BETWEEN ? AND ?
                """ + (staffReference == null || staffReference.isBlank() ? "" : " AND staff_reference = ?")
                + " ORDER BY schedule_date, staff_reference, shift_code";
        if (staffReference == null || staffReference.isBlank()) {
            return jdbc.query(sql, (rs, n) -> new ScheduleRow(rs.getObject("id", UUID.class), rs.getString("staff_reference"),
                    rs.getDate("schedule_date").toLocalDate(), rs.getString("shift_code"), rs.getString("work_area"),
                    rs.getString("note"), instant(rs, "created_at"), rs.getString("created_by_ref")), organization,
                    Date.valueOf(from), Date.valueOf(to));
        }
        return jdbc.query(sql, (rs, n) -> new ScheduleRow(rs.getObject("id", UUID.class), rs.getString("staff_reference"),
                rs.getDate("schedule_date").toLocalDate(), rs.getString("shift_code"), rs.getString("work_area"),
                rs.getString("note"), instant(rs, "created_at"), rs.getString("created_by_ref")), organization,
                Date.valueOf(from), Date.valueOf(to), staffReference);
    }

    public UUID insertQualityDocument(QualityDocumentCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.quality_document
                    (id, title, document_no, category_code, version_label, effective_at, owner_reference,
                     status_code, content_reference, previous_document_id, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
                """, id, item.title(), item.documentNo(), item.categoryCode(), item.versionLabel(),
                item.effectiveAt() == null ? null : Timestamp.from(item.effectiveAt()), item.ownerReference(),
                item.contentReference(), item.previousDocumentId(), organization, Timestamp.from(now), actor);
        return id;
    }

    public List<QualityDocumentRow> qualityDocuments(String organization) {
        return jdbc.query("""
                SELECT id, title, document_no, category_code, version_label, effective_at, owner_reference,
                       status_code, content_reference, previous_document_id, created_at, created_by_ref,
                       reviewed_at, reviewed_by_ref, archived_at
                  FROM pis_v2.quality_document WHERE organization_reference = ?
                 ORDER BY document_no, version_label DESC
                """, (rs, n) -> new QualityDocumentRow(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("document_no"), rs.getString("category_code"), rs.getString("version_label"),
                instant(rs, "effective_at"), rs.getString("owner_reference"), rs.getString("status_code"),
                rs.getString("content_reference"), uuid(rs, "previous_document_id"), instant(rs, "created_at"),
                rs.getString("created_by_ref"), instant(rs, "reviewed_at"), rs.getString("reviewed_by_ref"),
                instant(rs, "archived_at")), organization);
    }

    public Optional<QualityDocumentRow> transitionQualityDocument(UUID id, String status, String actor, Instant now,
            String organization) {
        int changed = switch (status) {
            case "REVIEW" -> jdbc.update("""
                    UPDATE pis_v2.quality_document SET status_code = 'REVIEW', reviewed_at = ?, reviewed_by_ref = ?
                     WHERE id = ? AND organization_reference = ? AND status_code = 'DRAFT'
                    """, Timestamp.from(now), actor, id, organization);
            case "PUBLISHED" -> jdbc.update("""
                    UPDATE pis_v2.quality_document SET status_code = 'PUBLISHED', effective_at = COALESCE(effective_at, ?)
                     WHERE id = ? AND organization_reference = ? AND status_code IN ('REVIEW', 'DRAFT')
                    """, Timestamp.from(now), id, organization);
            case "ARCHIVED" -> jdbc.update("""
                    UPDATE pis_v2.quality_document SET status_code = 'ARCHIVED', archived_at = ?
                     WHERE id = ? AND organization_reference = ? AND status_code = 'PUBLISHED'
                    """, Timestamp.from(now), id, organization);
            default -> 0;
        };
        return changed == 1 ? qualityDocuments(organization).stream().filter(item -> item.id().equals(id)).findFirst()
                : Optional.empty();
    }

    public UUID insertEquipment(EquipmentCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.equipment
                    (id, equipment_code, name, category_code, manufacturer, model, serial_no, location_reference,
                     custodian_reference, purchase_date, warranty_until, calibration_due_at, status_code,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, item.equipmentCode(), item.name(), item.categoryCode(), item.manufacturer(), item.model(),
                item.serialNo(), item.locationReference(), item.custodianReference(), date(item.purchaseDate()),
                date(item.warrantyUntil()), date(item.calibrationDueAt()), item.statusCode(), organization,
                Timestamp.from(now), actor);
        return id;
    }

    public List<EquipmentRow> equipment(String organization) {
        return jdbc.query("""
                SELECT id, equipment_code, name, category_code, manufacturer, model, serial_no, location_reference,
                       custodian_reference, purchase_date, warranty_until, calibration_due_at, status_code, created_at
                  FROM pis_v2.equipment WHERE organization_reference = ? ORDER BY equipment_code
                """, (rs, n) -> new EquipmentRow(rs.getObject("id", UUID.class), rs.getString("equipment_code"),
                rs.getString("name"), rs.getString("category_code"), rs.getString("manufacturer"), rs.getString("model"),
                rs.getString("serial_no"), rs.getString("location_reference"), rs.getString("custodian_reference"),
                localDate(rs, "purchase_date"), localDate(rs, "warranty_until"), localDate(rs, "calibration_due_at"),
                rs.getString("status_code"), instant(rs, "created_at")), organization);
    }

    public UUID insertEquipmentEvent(UUID equipmentId, EquipmentEventCommand item, String organization, String actor,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.equipment_event
                    (id, equipment_id, event_code, occurred_at, operator_reference, description, amount, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, equipmentId, item.eventCode(), item.occurredAt() == null ? Timestamp.from(now) : Timestamp.from(item.occurredAt()),
                actor, item.description(), item.amount(), organization);
        return id;
    }

    public List<EquipmentEventRow> equipmentEvents(UUID equipmentId, String organization) {
        return jdbc.query("""
                SELECT id, equipment_id, event_code, occurred_at, operator_reference, description, amount
                  FROM pis_v2.equipment_event WHERE equipment_id = ? AND organization_reference = ?
                 ORDER BY occurred_at DESC
                """, (rs, n) -> new EquipmentEventRow(rs.getObject("id", UUID.class), rs.getObject("equipment_id", UUID.class),
                rs.getString("event_code"), instant(rs, "occurred_at"), rs.getString("operator_reference"),
                rs.getString("description"), rs.getBigDecimal("amount")), equipmentId, organization);
    }

    public UUID insertCatalog(ConsumableCatalogCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.consumable_catalog
                    (id, material_code, name, category_code, specification, unit_code, manufacturer, supplier,
                     hazardous, active, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?)
                """, id, item.materialCode(), item.name(), item.categoryCode(), item.specification(), item.unitCode(),
                item.manufacturer(), item.supplier(), item.hazardous(), organization, Timestamp.from(now), actor);
        return id;
    }

    public List<ConsumableCatalogRow> catalogs(String organization) {
        return jdbc.query("""
                SELECT id, material_code, name, category_code, specification, unit_code, manufacturer, supplier, hazardous, active
                  FROM pis_v2.consumable_catalog WHERE organization_reference = ? ORDER BY material_code
                """, (rs, n) -> new ConsumableCatalogRow(rs.getObject("id", UUID.class), rs.getString("material_code"),
                rs.getString("name"), rs.getString("category_code"), rs.getString("specification"), rs.getString("unit_code"),
                rs.getString("manufacturer"), rs.getString("supplier"), rs.getBoolean("hazardous"), rs.getBoolean("active")), organization);
    }

    public UUID insertBatch(UUID catalogId, ConsumableBatchCommand item, String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.consumable_batch
                    (id, catalog_id, batch_no, expiry_date, storage_location, organization_reference, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, catalogId, item.batchNo(), date(item.expiryDate()), item.storageLocation(), organization, Timestamp.from(now));
        return id;
    }

    public UUID insertConsumableTransaction(UUID batchId, ConsumableTransactionCommand item, String organization, String actor,
            Instant now) {
        BigDecimal balance = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN direction_code = 'INBOUND' THEN quantity
                                         WHEN direction_code = 'OUTBOUND' THEN -quantity
                                         ELSE quantity END), 0)
                  FROM pis_v2.consumable_transaction WHERE batch_id = ? AND organization_reference = ?
                """, BigDecimal.class, batchId, organization);
        if ("OUTBOUND".equals(item.directionCode()) && (balance == null || balance.compareTo(item.quantity()) < 0)) {
            throw new IllegalStateException("INSUFFICIENT_CONSUMABLE_STOCK");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.consumable_transaction
                    (id, batch_id, direction_code, quantity, reason, source_reference, operator_reference, occurred_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, item.directionCode(), item.quantity(), item.reason(), item.sourceReference(), actor,
                item.occurredAt() == null ? Timestamp.from(now) : Timestamp.from(item.occurredAt()), organization);
        return id;
    }

    public List<StockRow> stock(String organization) {
        return jdbc.query("""
                SELECT b.id AS batch_id, c.id AS catalog_id, c.material_code, c.name, b.batch_no, b.expiry_date,
                       COALESCE(SUM(CASE WHEN t.direction_code = 'INBOUND' THEN t.quantity
                                         WHEN t.direction_code = 'OUTBOUND' THEN -t.quantity ELSE t.quantity END), 0) AS balance
                  FROM pis_v2.consumable_batch b JOIN pis_v2.consumable_catalog c ON c.id = b.catalog_id
                  LEFT JOIN pis_v2.consumable_transaction t ON t.batch_id = b.id AND t.organization_reference = ?
                 WHERE b.organization_reference = ? GROUP BY b.id, c.id, c.material_code, c.name, b.batch_no, b.expiry_date
                 ORDER BY c.material_code, b.batch_no
                """, (rs, n) -> new StockRow(rs.getObject("batch_id", UUID.class), rs.getObject("catalog_id", UUID.class),
                rs.getString("material_code"), rs.getString("name"), rs.getString("batch_no"), localDate(rs, "expiry_date"),
                rs.getBigDecimal("balance")), organization, organization);
    }

    public UUID insertRequisition(RequisitionCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.consumable_requisition
                    (id, request_no, requester_reference, department_reference, purpose, status_code, requested_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, 'REQUESTED', ?, ?)
                """, id, item.requestNo(), actor, item.departmentReference(), item.purpose(), Timestamp.from(now), organization);
        for (RequisitionItem itemRow : item.items()) {
            jdbc.update("""
                    INSERT INTO pis_v2.consumable_requisition_item (id, requisition_id, catalog_id, quantity)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), id, itemRow.catalogId(), itemRow.quantity());
        }
        return id;
    }

    public boolean decideRequisition(UUID id, String status, String actor, Instant now, String organization) {
        return jdbc.update("""
                UPDATE pis_v2.consumable_requisition SET status_code = ?, decided_at = ?, decided_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND status_code = 'REQUESTED'
                """, status, Timestamp.from(now), actor, id, organization) == 1;
    }

    public UUID insertProcurementRequest(ProcurementRequestCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.procurement_request
                    (id, request_no, requester_reference, department_reference, reason, status_code, requested_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, 'REQUESTED', ?, ?)
                """, id, item.requestNo(), actor, item.departmentReference(), item.reason(), Timestamp.from(now), organization);
        for (ProcurementItem itemRow : item.items()) {
            jdbc.update("""
                    INSERT INTO pis_v2.procurement_item (id, request_id, material_reference, quantity, estimated_amount, supplier)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, itemRow.materialReference(), itemRow.quantity(), itemRow.estimatedAmount(), itemRow.supplier());
        }
        return id;
    }

    public boolean approveProcurement(UUID id, String decision, String comment, String actor, Instant now, String organization) {
        String status = "APPROVED".equals(decision) ? "APPROVED" : "REJECTED";
        int changed = jdbc.update("""
                INSERT INTO pis_v2.procurement_approval
                    (id, request_id, approval_sequence, approver_reference, decision_code, comment, decided_at, organization_reference)
                VALUES (?, ?, COALESCE((SELECT MAX(approval_sequence) + 1 FROM pis_v2.procurement_approval WHERE request_id = ?), 1), ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, id, actor, status, comment, Timestamp.from(now), organization);
        jdbc.update("UPDATE pis_v2.procurement_request SET status_code = ? WHERE id = ? AND organization_reference = ?",
                status, id, organization);
        return changed == 1;
    }

    public UUID insertProcurementAttachment(UUID requestId, String kind, String reference, String actor, Instant now,
            String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.procurement_attachment
                    (id, request_id, attachment_kind_code, storage_reference, created_at, created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, requestId, kind, reference, Timestamp.from(now), actor, organization);
        return id;
    }

    public UUID insertSpace(SpaceCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.department_space
                    (id, parent_id, space_code, name, zone_code, area_value, administrator_reference, description,
                     view_reference, active, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?)
                """, id, item.parentId(), item.spaceCode(), item.name(), item.zoneCode(), item.areaValue(),
                item.administratorReference(), item.description(), item.viewReference(), organization, Timestamp.from(now), actor);
        return id;
    }

    public List<SpaceRow> spaces(String organization) {
        return jdbc.query("""
                SELECT id, parent_id, space_code, name, zone_code, area_value, administrator_reference, description, view_reference, active
                  FROM pis_v2.department_space WHERE organization_reference = ? ORDER BY space_code
                """, (rs, n) -> new SpaceRow(rs.getObject("id", UUID.class), uuid(rs, "parent_id"), rs.getString("space_code"),
                rs.getString("name"), rs.getString("zone_code"), rs.getBigDecimal("area_value"), rs.getString("administrator_reference"),
                rs.getString("description"), rs.getString("view_reference"), rs.getBoolean("active")), organization);
    }

    public UUID insertEnvironment(UUID spaceId, EnvironmentCommand item, String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.space_environment_record
                    (id, space_id, metric_code, measure_value, unit_code, measured_at, source_reference, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, spaceId, item.metricCode(), item.measureValue(), item.unitCode(),
                item.measuredAt() == null ? Timestamp.from(now) : Timestamp.from(item.measuredAt()), item.sourceReference(), organization);
        return id;
    }

    public UUID insertSafetyCheck(UUID spaceId, SafetyCheckCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.space_safety_check
                    (id, space_id, check_code, result_code, note, checked_at, checked_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, spaceId, item.checkCode(), item.resultCode(), item.note(), Timestamp.from(now), actor, organization);
        return id;
    }

    public UUID insertCriticalValue(UUID caseId, CriticalValueCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.critical_value
                    (id, case_id, value_type_code, grade_code, trigger_reference, status_code, due_at, created_at, created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
                """, id, caseId, item.valueTypeCode(), item.gradeCode(), item.triggerReference(),
                item.dueAt() == null ? null : Timestamp.from(item.dueAt()), Timestamp.from(now), actor, organization);
        return id;
    }

    public Optional<CriticalValueRow> criticalValue(UUID id, String organization) {
        return jdbc.query("""
                SELECT id, case_id, value_type_code, grade_code, trigger_reference, status_code, due_at, created_at, created_by_ref
                  FROM pis_v2.critical_value WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new CriticalValueRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("value_type_code"), rs.getString("grade_code"), rs.getString("trigger_reference"), rs.getString("status_code"),
                instant(rs, "due_at"), instant(rs, "created_at"), rs.getString("created_by_ref"))) : Optional.empty(), id, organization);
    }

    public List<CriticalValueRow> criticalValues(String organization) {
        return jdbc.query("""
                SELECT id, case_id, value_type_code, grade_code, trigger_reference, status_code, due_at, created_at, created_by_ref
                  FROM pis_v2.critical_value WHERE organization_reference = ? ORDER BY created_at DESC
                """, (rs, n) -> new CriticalValueRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("value_type_code"), rs.getString("grade_code"), rs.getString("trigger_reference"), rs.getString("status_code"),
                instant(rs, "due_at"), instant(rs, "created_at"), rs.getString("created_by_ref")), organization);
    }

    public UUID notifyCriticalValue(UUID valueId, CriticalNotificationCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.critical_value_notification
                    (id, critical_value_id, department_reference, recipient_reference, method_code, notified_at, notified_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, valueId, item.departmentReference(), item.recipientReference(), item.methodCode(), Timestamp.from(now), actor, organization);
        jdbc.update("""
                INSERT INTO pis_v2.notification
                (id, recipient_reference, type_code, title, body, business_path, priority_code, created_at, organization_reference)
                VALUES (?, ?, 'CRITICAL_VALUE', '危急值通知', ?, ?, 'URGENT', ?, ?)""", UUID.randomUUID(), item.recipientReference(),
                item.message(), item.businessPath(), Timestamp.from(now), organization);
        return id;
    }

    public boolean acknowledgeCriticalValue(UUID notificationId, String actor, Instant now, String organization) {
        int changed = jdbc.update("""
                UPDATE pis_v2.critical_value_notification SET acknowledgement_at = ?, acknowledged_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND acknowledgement_at IS NULL
                """, Timestamp.from(now), actor, notificationId, organization);
        if (changed == 1) {
            jdbc.update("""
                    UPDATE pis_v2.critical_value SET status_code = 'ACKNOWLEDGED'
                    WHERE id = (SELECT critical_value_id FROM pis_v2.critical_value_notification WHERE id = ?) AND status_code = 'OPEN'""", notificationId);
        }
        return changed == 1;
    }

    public UUID addCriticalFeedback(UUID valueId, String content, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.critical_value_feedback
                (id, critical_value_id, content, feedback_at, feedback_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?)""", id, valueId, content, Timestamp.from(now), actor, organization);
        jdbc.update("UPDATE pis_v2.critical_value SET status_code = 'COMPLETED' WHERE id = ? AND organization_reference = ?",
                valueId, organization);
        return id;
    }

    public UUID insertDistribution(UUID reportId, String target, String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.report_distribution
                (id, report_id, target_code, requested_at, status_code, retry_count, organization_reference)
                VALUES (?, ?, ?, ?, 'REQUESTED', 0, ?)""", id, reportId, target, Timestamp.from(now), organization);
        return id;
    }

    public boolean updateDistribution(UUID id, String status, String error, String organization, Instant now) {
        return jdbc.update("""
                UPDATE pis_v2.report_distribution
                SET status_code = ?, sent_at = CASE WHEN ? = 'SENT' THEN ? ELSE sent_at END,
                    last_error = ?, retry_count = CASE WHEN ? = 'RETRY_PENDING' THEN retry_count + 1 ELSE retry_count END
                WHERE id = ? AND organization_reference = ? AND status_code <> 'SENT'""", status, status, Timestamp.from(now), error,
                status, id, organization) == 1;
    }

    public UUID insertPrintRecord(UUID reportId, String identity, String terminal, String printer, String result, int copies,
            String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.report_print_record
                (id, report_id, identity_reference, terminal_reference, printer_reference, printed_at, result_code, copy_count, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, reportId, identity, terminal, printer, Timestamp.from(now), result, copies, organization);
        return id;
    }

    public UUID insertAddress(AddressCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.common_address
                (id, address_name, recipient_name, phone, address_text, organization_reference, active, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)""", id, item.addressName(), item.recipientName(), item.phone(), item.addressText(),
                organization, Timestamp.from(now), actor);
        return id;
    }

    public List<AddressRow> addresses(String organization) {
        return jdbc.query("""
                SELECT id, address_name, recipient_name, phone, address_text, active
                FROM pis_v2.common_address WHERE organization_reference = ? AND active = TRUE ORDER BY address_name""",
                (rs, n) -> new AddressRow(rs.getObject("id", UUID.class), rs.getString("address_name"), rs.getString("recipient_name"),
                rs.getString("phone"), rs.getString("address_text"), rs.getBoolean("active")), organization);
    }

    public UUID insertPackage(PackageCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.logistics_package
                (id, case_id, consultation_id, courier_company, tracking_no, sender_reference, recipient_reference, address_text,
                 status_code, sent_at, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', NULL, ?, ?, ?)""", id, item.caseId(), item.consultationId(), item.courierCompany(),
                item.trackingNo(), item.senderReference(), item.recipientReference(), item.addressText(), organization, Timestamp.from(now), actor);
        return id;
    }

    public UUID insertPackageItem(UUID packageId, PackageItem item) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.logistics_package_item
                (id, package_id, block_id, slide_id, document_reference) VALUES (?, ?, ?, ?, ?)""", id, packageId, item.blockId(), item.slideId(), item.documentReference());
        return id;
    }

    public UUID insertLogisticsEvent(UUID packageId, String status, String note, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.logistics_event
                (id, package_id, status_code, occurred_at, note, recorded_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", id, packageId, status, Timestamp.from(now), note, actor, organization);
        jdbc.update("UPDATE pis_v2.logistics_package SET status_code = ?, sent_at = CASE WHEN ? = 'SENT' THEN ? ELSE sent_at END WHERE id = ? AND organization_reference = ?",
                status, status, Timestamp.from(now), packageId, organization);
        return id;
    }

    public UUID insertMolecularProject(MolecularProjectCommand item, String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_project
                (id, project_code, project_name, project_type_code, enabled, organization_reference)
                VALUES (?, ?, ?, ?, TRUE, ?)""", id, item.projectCode(), item.projectName(), item.projectTypeCode(), organization);
        return id;
    }

    public UUID insertMolecularInstrument(MolecularInstrumentCommand item, String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_instrument
                (id, instrument_code, name, adapter_code, enabled, organization_reference)
                VALUES (?, ?, ?, ?, TRUE, ?)""", id, item.instrumentCode(), item.name(), item.adapterCode(), organization);
        return id;
    }

    public UUID insertMolecularReagent(MolecularReagentCommand item, String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_reagent_kit
                (id, kit_code, manufacturer, batch_no, expiry_date, enabled, organization_reference)
                VALUES (?, ?, ?, ?, ?, TRUE, ?)""", id, item.kitCode(), item.manufacturer(), item.batchNo(), date(item.expiryDate()), organization);
        return id;
    }

    public UUID insertMolecularTest(MolecularTestCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_test
                (id, case_id, specimen_id, project_id, detection_no, instrument_id, reagent_kit_id, raw_data_reference,
                 structured_result, analysis_result, status_code, created_at, created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED', ?, ?, ?)""", id, item.caseId(), item.specimenId(), item.projectId(),
                item.detectionNo(), item.instrumentId(), item.reagentKitId(), item.rawDataReference(), item.structuredResult(),
                item.analysisResult(), Timestamp.from(now), actor, organization);
        return id;
    }

    public boolean completeMolecularTest(UUID id, String structuredResult, String analysisResult, String organization, Instant now) {
        return jdbc.update("""
                UPDATE pis_v2.molecular_test SET status_code = 'COMPLETED', structured_result = ?, analysis_result = ?, completed_at = ?
                WHERE id = ? AND organization_reference = ? AND status_code <> 'CANCELLED'""", structuredResult, analysisResult, Timestamp.from(now), id, organization) == 1;
    }

    public UUID archiveDigitalSlide(ArchiveCommand item, String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.digital_slide_archive
                (id, digital_slide_id, storage_path, storage_tier, filename, format_code, pathology_no, slide_no, patient_reference,
                 organ_reference, integrity_digest, status_code, imported_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ARCHIVED', ?, ?)""", id, item.digitalSlideId(), item.storagePath(), item.storageTier(),
                item.filename(), item.formatCode(), item.pathologyNo(), item.slideNo(), item.patientReference(), item.organReference(),
                item.integrityDigest(), Timestamp.from(now), organization);
        return id;
    }

    public boolean updateArchiveStatus(UUID id, String status, String organization, Instant now) {
        return jdbc.update("""
                UPDATE pis_v2.digital_slide_archive SET status_code = ?, restored_at = CASE WHEN ? = 'RESTORED' THEN ? ELSE restored_at END
                WHERE id = ? AND organization_reference = ?""", status, status, Timestamp.from(now), id, organization) == 1;
    }

    public UUID createRegionalShare(RegionalShareCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.regional_share
                (id, case_id, receiving_organization, receiving_doctor, expires_at, patient_authorized, status_code, requested_at, requested_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)""", id, item.caseId(), item.receivingOrganization(), item.receivingDoctor(),
                item.expiresAt() == null ? null : Timestamp.from(item.expiresAt()), item.patientAuthorized(), Timestamp.from(now), actor, organization);
        for (RegionalShareItem itemRow : item.items()) {
            jdbc.update("""
                    INSERT INTO pis_v2.regional_share_item
                    (id, share_id, report_id, digital_slide_id, attachment_reference) VALUES (?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), id, itemRow.reportId(), itemRow.digitalSlideId(), itemRow.attachmentReference());
        }
        return id;
    }

    public UUID recordRegionalAccess(UUID shareId, String accessor, String action, String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.regional_share_access
                (id, share_id, accessor_reference, accessed_at, action_code, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?)""", id, shareId, accessor, Timestamp.from(now), action, organization);
        return id;
    }

    public UUID insertIncome(IncomeCommand item, String organization, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.income_fact
                (id, case_id, project_code, amount, occurred_at, source_reference, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", id, item.caseId(), item.projectCode(), item.amount(),
                item.occurredAt() == null ? Timestamp.from(now) : Timestamp.from(item.occurredAt()), item.sourceReference(), organization);
        return id;
    }

    public UUID createMigrationJob(MigrationJobCommand item, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.migration_job
                (id, source_code, mode_code, status_code, created_at, created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", id, item.sourceCode(), item.modeCode(), item.statusCode(), Timestamp.from(now), actor, organization);
        return id;
    }

    public UUID insertMigrationRecord(MigrationRecordCommand item, String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.migration_record
                (id, job_id, legacy_type, legacy_key, local_type, local_id, record_status, raw_reference, mapped_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, item.jobId(), item.legacyType(), item.legacyKey(), item.localType(), item.localId(),
                item.recordStatus(), item.rawReference(), item.mappedAt() == null ? null : Timestamp.from(item.mappedAt()), organization);
        return id;
    }

    public UUID insertMigrationError(MigrationErrorCommand item, String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.migration_error
                (id, job_id, record_id, error_code, error_message, retry_count, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", id, item.jobId(), item.recordId(), item.errorCode(), item.errorMessage(), item.retryCount(), organization);
        return id;
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static UUID uuid(java.sql.ResultSet rs, String column) throws java.sql.SQLException { return rs.getObject(column, UUID.class); }
    private static Date date(LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private static LocalDate localDate(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Date value = rs.getDate(column); return value == null ? null : value.toLocalDate();
    }

    public record NotificationRow(UUID id, String recipientReference, String typeCode, String title, String body,
            String businessPath, String priorityCode, Instant createdAt, Instant readAt) { }
    public record ScheduleCommand(String staffReference, LocalDate scheduleDate, String shiftCode, String workArea, String note) { }
    public record ScheduleRow(UUID id, String staffReference, LocalDate scheduleDate, String shiftCode, String workArea,
            String note, Instant createdAt, String createdByRef) { }
    public record QualityDocumentCommand(String title, String documentNo, String categoryCode, String versionLabel,
            Instant effectiveAt, String ownerReference, String contentReference, UUID previousDocumentId) { }
    public record QualityDocumentRow(UUID id, String title, String documentNo, String categoryCode, String versionLabel,
            Instant effectiveAt, String ownerReference, String statusCode, String contentReference, UUID previousDocumentId,
            Instant createdAt, String createdByRef, Instant reviewedAt, String reviewedByRef, Instant archivedAt) { }
    public record EquipmentCommand(String equipmentCode, String name, String categoryCode, String manufacturer, String model,
            String serialNo, String locationReference, String custodianReference, LocalDate purchaseDate, LocalDate warrantyUntil,
            LocalDate calibrationDueAt, String statusCode) { }
    public record EquipmentRow(UUID id, String equipmentCode, String name, String categoryCode, String manufacturer, String model,
            String serialNo, String locationReference, String custodianReference, LocalDate purchaseDate, LocalDate warrantyUntil,
            LocalDate calibrationDueAt, String statusCode, Instant createdAt) { }
    public record EquipmentEventCommand(String eventCode, Instant occurredAt, String description, BigDecimal amount) { }
    public record EquipmentEventRow(UUID id, UUID equipmentId, String eventCode, Instant occurredAt, String operatorReference,
            String description, BigDecimal amount) { }
    public record ConsumableCatalogCommand(String materialCode, String name, String categoryCode, String specification,
            String unitCode, String manufacturer, String supplier, boolean hazardous) { }
    public record ConsumableCatalogRow(UUID id, String materialCode, String name, String categoryCode, String specification,
            String unitCode, String manufacturer, String supplier, boolean hazardous, boolean active) { }
    public record ConsumableBatchCommand(String batchNo, LocalDate expiryDate, String storageLocation) { }
    public record ConsumableTransactionCommand(String directionCode, BigDecimal quantity, String reason, String sourceReference,
            Instant occurredAt) { }
    public record StockRow(UUID batchId, UUID catalogId, String materialCode, String name, String batchNo, LocalDate expiryDate,
            BigDecimal balance) { }
    public record RequisitionCommand(String requestNo, String departmentReference, String purpose, List<RequisitionItem> items) { }
    public record RequisitionItem(UUID catalogId, BigDecimal quantity) { }
    public record ProcurementRequestCommand(String requestNo, String departmentReference, String reason, List<ProcurementItem> items) { }
    public record ProcurementItem(String materialReference, BigDecimal quantity, BigDecimal estimatedAmount, String supplier) { }
    public record SpaceCommand(UUID parentId, String spaceCode, String name, String zoneCode, BigDecimal areaValue,
            String administratorReference, String description, String viewReference) { }
    public record SpaceRow(UUID id, UUID parentId, String spaceCode, String name, String zoneCode, BigDecimal areaValue,
            String administratorReference, String description, String viewReference, boolean active) { }
    public record EnvironmentCommand(String metricCode, BigDecimal measureValue, String unitCode, Instant measuredAt,
            String sourceReference) { }
    public record SafetyCheckCommand(String checkCode, String resultCode, String note) { }
    public record CriticalValueCommand(String valueTypeCode, String gradeCode, String triggerReference, Instant dueAt) { }
    public record CriticalValueRow(UUID id, UUID caseId, String valueTypeCode, String gradeCode, String triggerReference,
            String statusCode, Instant dueAt, Instant createdAt, String createdByRef) { }
    public record CriticalNotificationCommand(String departmentReference, String recipientReference, String methodCode,
            String message, String businessPath) { }
    public record AddressCommand(String addressName, String recipientName, String phone, String addressText) { }
    public record AddressRow(UUID id, String addressName, String recipientName, String phone, String addressText, boolean active) { }
    public record PackageCommand(UUID caseId, UUID consultationId, String courierCompany, String trackingNo,
            String senderReference, String recipientReference, String addressText) { }
    public record PackageItem(UUID blockId, UUID slideId, String documentReference) { }
    public record MolecularProjectCommand(String projectCode, String projectName, String projectTypeCode) { }
    public record MolecularInstrumentCommand(String instrumentCode, String name, String adapterCode) { }
    public record MolecularReagentCommand(String kitCode, String manufacturer, String batchNo, LocalDate expiryDate) { }
    public record MolecularTestCommand(UUID caseId, UUID specimenId, UUID projectId, String detectionNo, UUID instrumentId,
            UUID reagentKitId, String rawDataReference, String structuredResult, String analysisResult) { }
    public record ArchiveCommand(UUID digitalSlideId, String storagePath, String storageTier, String filename, String formatCode,
            String pathologyNo, String slideNo, String patientReference, String organReference, String integrityDigest) { }
    public record RegionalShareCommand(UUID caseId, String receivingOrganization, String receivingDoctor, Instant expiresAt,
            boolean patientAuthorized, List<RegionalShareItem> items) { }
    public record RegionalShareItem(UUID reportId, UUID digitalSlideId, String attachmentReference) { }
    public record IncomeCommand(UUID caseId, String projectCode, BigDecimal amount, Instant occurredAt, String sourceReference) { }
    public record MigrationJobCommand(String sourceCode, String modeCode, String statusCode) { }
    public record MigrationRecordCommand(UUID jobId, String legacyType, String legacyKey, String localType, UUID localId,
            String recordStatus, String rawReference, Instant mappedAt) { }
    public record MigrationErrorCommand(UUID jobId, UUID recordId, String errorCode, String errorMessage, int retryCount) { }
}

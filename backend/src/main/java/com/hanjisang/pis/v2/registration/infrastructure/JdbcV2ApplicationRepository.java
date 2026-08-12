package com.hanjisang.pis.v2.registration.infrastructure;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2ApplicationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2ApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertApplication(ApplicationRow application, List<ApplicationItemInput> items,
            String organizationReference, String actorReference, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_application
                    (id, application_no, source_type_code, source_system_code, patient_reference, patient_name,
                     patient_sex_code, patient_birth_date, patient_info_source_code, patient_identity_no,
                     visit_card_no, contact_phone, age_value, age_unit_code, visit_reference, visit_type_code,
                     ward_reference, bed_reference, application_department,
                     applicant_reference, applied_at, clinical_diagnosis, medical_history, operation_finding,
                     surgery_name, examination_purpose, specimen_description, note, status_code, organization_reference,
                     concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        'RECEIVED', ?, 0, ?, ?, ?, ?)
                """, application.id(), application.applicationNo(), application.sourceTypeCode(),
                application.sourceSystemCode(), application.patientReference(), application.patientName(),
                application.patientSexCode(), application.patientBirthDate() == null ? null : Date.valueOf(application.patientBirthDate()),
                application.patientInfoSourceCode(), application.patientIdentityNo(), application.visitCardNo(),
                application.contactPhone(), application.ageValue(), application.ageUnitCode(),
                application.visitReference(), application.visitTypeCode(), application.wardReference(),
                application.bedReference(), application.applicationDepartment(),
                application.applicantReference(), Timestamp.from(application.appliedAt()), application.clinicalDiagnosis(),
                application.medicalHistory(), application.operationFinding(), application.surgeryName(), application.examinationPurpose(),
                application.specimenDescription(), application.note(), organizationReference, Timestamp.from(now),
                actorReference, Timestamp.from(now), actorReference);
        for (ApplicationItemInput item : items) {
            jdbcTemplate.update("""
                    INSERT INTO pis_v2.pathology_application_item
                        (id, application_id, external_item_code, item_name, mapping_id, business_type_id,
                         specimen_kind_code, specimen_description, sequence_no, status_code, created_at, created_by_ref)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                    """, item.id(), application.id(), item.externalItemCode(), item.itemName(), item.mappingId(),
                    item.businessTypeId(), item.specimenKindCode(), item.specimenDescription(), item.sequenceNo(),
                    Timestamp.from(now), actorReference);
        }
    }

    public Optional<ApplicationRow> findApplication(UUID applicationId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, application_no, source_type_code, source_system_code, patient_reference, patient_name,
                       patient_sex_code, patient_birth_date, patient_info_source_code, patient_identity_no,
                       visit_card_no, contact_phone, age_value, age_unit_code, visit_reference, visit_type_code,
                       ward_reference, bed_reference, application_department,
                       applicant_reference, applied_at, clinical_diagnosis, medical_history, operation_finding,
                       surgery_name, examination_purpose, specimen_description, note, status_code, concurrency_version
                FROM pis_v2.pathology_application
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(application(rs)) : Optional.empty(), applicationId,
                organizationReference);
    }

    public Optional<ApplicationRow> findBySourceIdentity(String sourceSystemCode, String applicationNo,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, application_no, source_type_code, source_system_code, patient_reference, patient_name,
                       patient_sex_code, patient_birth_date, patient_info_source_code, patient_identity_no,
                       visit_card_no, contact_phone, age_value, age_unit_code, visit_reference, visit_type_code,
                       ward_reference, bed_reference, application_department,
                       applicant_reference, applied_at, clinical_diagnosis, medical_history, operation_finding,
                       surgery_name, examination_purpose, specimen_description, note, status_code, concurrency_version
                FROM pis_v2.pathology_application
                WHERE source_system_code = ? AND application_no = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(application(rs)) : Optional.empty(), sourceSystemCode,
                applicationNo, organizationReference);
    }

    public Optional<ApplicationItemRow> findItem(UUID itemId, UUID applicationId) {
        return jdbcTemplate.query("""
                SELECT i.id, i.application_id, i.external_item_code, i.item_name, i.mapping_id, i.business_type_id,
                       i.specimen_kind_code, i.specimen_description, i.sequence_no, i.status_code,
                       bt.business_type_code, ac.case_id, c.case_no
                FROM pis_v2.pathology_application_item i
                LEFT JOIN pis_v2.business_type bt ON bt.id = i.business_type_id
                LEFT JOIN pis_v2.pathology_application_case ac ON ac.application_item_id = i.id
                LEFT JOIN pis_v2.pathology_case c ON c.id = ac.case_id
                WHERE i.id = ? AND i.application_id = ?
                """, rs -> rs.next() ? Optional.of(item(rs)) : Optional.empty(), itemId, applicationId);
    }

    public Optional<ApplicationItemRow> findItemForUpdate(UUID itemId, UUID applicationId) {
        return jdbcTemplate.query("""
                SELECT i.id, i.application_id, i.external_item_code, i.item_name, i.mapping_id, i.business_type_id,
                       i.specimen_kind_code, i.specimen_description, i.sequence_no, i.status_code,
                       bt.business_type_code, ac.case_id, c.case_no
                FROM pis_v2.pathology_application_item i
                LEFT JOIN pis_v2.business_type bt ON bt.id = i.business_type_id
                LEFT JOIN pis_v2.pathology_application_case ac ON ac.application_item_id = i.id
                LEFT JOIN pis_v2.pathology_case c ON c.id = ac.case_id
                WHERE i.id = ? AND i.application_id = ?
                FOR UPDATE OF i
                """, rs -> rs.next() ? Optional.of(item(rs)) : Optional.empty(), itemId, applicationId);
    }

    public List<ApplicationItemRow> findItems(UUID applicationId) {
        return jdbcTemplate.query("""
                SELECT i.id, i.application_id, i.external_item_code, i.item_name, i.mapping_id, i.business_type_id,
                       i.specimen_kind_code, i.specimen_description, i.sequence_no, i.status_code,
                       bt.business_type_code, ac.case_id, c.case_no
                FROM pis_v2.pathology_application_item i
                LEFT JOIN pis_v2.business_type bt ON bt.id = i.business_type_id
                LEFT JOIN pis_v2.pathology_application_case ac ON ac.application_item_id = i.id
                LEFT JOIN pis_v2.pathology_case c ON c.id = ac.case_id
                WHERE i.application_id = ? ORDER BY i.sequence_no, i.id
                """, (rs, rowNum) -> item(rs), applicationId);
    }

    public List<ApplicationQueueRow> findQueue(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT a.id, a.application_no, a.source_type_code, a.source_system_code, a.patient_reference,
                       a.patient_name, a.patient_sex_code, a.patient_birth_date, a.visit_reference,
                       a.application_department, a.applicant_reference,
                       a.applied_at, a.status_code, i.id AS item_id, i.external_item_code, i.item_name,
                       i.specimen_kind_code, i.specimen_description, i.status_code AS item_status,
                       bt.business_type_code
                FROM pis_v2.pathology_application a
                JOIN pis_v2.pathology_application_item i ON i.application_id = a.id
                LEFT JOIN pis_v2.business_type bt ON bt.id = i.business_type_id
                WHERE a.organization_reference = ? AND a.status_code <> 'CANCELLED'
                  AND i.status_code = 'PENDING'
                ORDER BY a.applied_at, a.application_no, i.sequence_no
                """, (rs, rowNum) -> new ApplicationQueueRow(rs.getObject("id", UUID.class),
                rs.getString("application_no"), rs.getString("source_type_code"),
                rs.getString("source_system_code"), rs.getString("patient_reference"), rs.getString("patient_name"),
                rs.getString("patient_sex_code"), rs.getObject("patient_birth_date", java.time.LocalDate.class),
                rs.getString("visit_reference"), rs.getString("application_department"),
                rs.getString("applicant_reference"), rs.getTimestamp("applied_at").toInstant(),
                rs.getString("status_code"), rs.getObject("item_id", UUID.class), rs.getString("external_item_code"),
                rs.getString("item_name"), rs.getString("specimen_kind_code"), rs.getString("specimen_description"),
                rs.getString("item_status"), rs.getString("business_type_code")), organizationReference);
    }

    public boolean hasPendingItems(UUID applicationId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.pathology_application_item
                WHERE application_id = ? AND status_code = 'PENDING'
                """, Integer.class, applicationId);
        return count != null && count > 0;
    }

    public List<DeliveryRow> findDeliveries(UUID applicationId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.application_item_id, d.specimen_label_code, d.patient_reference,
                       d.actual_specimen_description, d.verification_status_code, d.rejection_reason,
                       d.delivered_by_ref, d.delivered_at
                FROM pis_v2.pathology_application_delivery d
                WHERE d.application_id = ? AND d.organization_reference = ?
                ORDER BY d.delivered_at, d.id
                """, (rs, rowNum) -> new DeliveryRow(rs.getObject("id", UUID.class),
                rs.getObject("application_item_id", UUID.class), rs.getString("specimen_label_code"),
                rs.getString("patient_reference"), rs.getString("actual_specimen_description"),
                rs.getString("verification_status_code"), rs.getString("rejection_reason"),
                rs.getString("delivered_by_ref"), rs.getTimestamp("delivered_at").toInstant()), applicationId,
                organizationReference);
    }

    public List<DeliverySearchRow> searchDeliveries(String organizationReference, String visitReference,
            Instant from, Instant to, String externalItemCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.application_id, d.application_item_id, a.application_no, a.visit_reference,
                       a.patient_reference, a.patient_name, i.external_item_code, i.item_name,
                       d.incoming_specimen_reference, d.specimen_label_code,
                       d.verification_status_code, d.rejection_reason, d.delivered_by_ref, d.delivered_at
                FROM pis_v2.pathology_application_delivery d
                JOIN pis_v2.pathology_application a ON a.id = d.application_id
                LEFT JOIN pis_v2.pathology_application_item i ON i.id = d.application_item_id
                WHERE d.organization_reference = ?
                """);
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(organizationReference);
        if (visitReference != null && !visitReference.isBlank()) {
            sql.append(" AND LOWER(a.visit_reference) LIKE LOWER(?)");
            parameters.add("%" + visitReference.trim() + "%");
        }
        if (from != null) {
            sql.append(" AND d.delivered_at >= ?");
            parameters.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND d.delivered_at <= ?");
            parameters.add(Timestamp.from(to));
        }
        if (externalItemCode != null && !externalItemCode.isBlank()) {
            sql.append(" AND i.external_item_code = ?");
            parameters.add(externalItemCode.trim());
        }
        sql.append(" ORDER BY d.delivered_at DESC, d.id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DeliverySearchRow(
                rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getObject("application_item_id", UUID.class), rs.getString("application_no"),
                rs.getString("visit_reference"), rs.getString("patient_reference"), rs.getString("patient_name"),
                rs.getString("external_item_code"), rs.getString("item_name"),
                rs.getString("incoming_specimen_reference"), rs.getString("specimen_label_code"),
                rs.getString("verification_status_code"), rs.getString("rejection_reason"),
                rs.getString("delivered_by_ref"), rs.getTimestamp("delivered_at").toInstant()),
                parameters.toArray());
    }

    public List<BarcodePrintRow> findBarcodePrints(UUID applicationId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT p.id, p.application_item_id, p.barcode_value, p.print_version, p.operation_code, p.copies,
                       p.rendered_label, p.printer_profile_code, p.result_code, p.failure_reason,
                       p.requested_at, p.requested_by_ref
                FROM pis_v2.pathology_application_barcode_print p
                JOIN pis_v2.pathology_application a ON a.id = p.application_id
                LEFT JOIN pis_v2.pathology_application_item i ON i.id = p.application_item_id
                WHERE p.application_id = ? AND a.organization_reference = ?
                ORDER BY p.requested_at, i.sequence_no, p.id
                """, (rs, rowNum) -> new BarcodePrintRow(rs.getObject("id", UUID.class),
                rs.getObject("application_item_id", UUID.class), rs.getString("barcode_value"),
                rs.getInt("print_version"), rs.getString("operation_code"), rs.getInt("copies"),
                rs.getString("rendered_label"), rs.getString("printer_profile_code"), rs.getString("result_code"),
                rs.getString("failure_reason"), rs.getTimestamp("requested_at").toInstant(),
                rs.getString("requested_by_ref")), applicationId, organizationReference);
    }

    public boolean updateApplication(ApplicationRow application, long expectedVersion, String organizationReference,
            String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application
                   SET source_type_code = ?, source_system_code = ?, patient_reference = ?, patient_name = ?,
                       patient_sex_code = ?, patient_birth_date = ?, patient_info_source_code = ?,
                       patient_identity_no = ?, visit_card_no = ?, contact_phone = ?, age_value = ?, age_unit_code = ?,
                       visit_reference = ?, visit_type_code = ?, ward_reference = ?, bed_reference = ?,
                       application_department = ?, applicant_reference = ?, applied_at = ?, clinical_diagnosis = ?,
                       medical_history = ?, operation_finding = ?, surgery_name = ?, examination_purpose = ?, specimen_description = ?,
                       note = ?, concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND status_code <> 'CANCELLED'
                   AND concurrency_version = ?
                """, application.sourceTypeCode(), application.sourceSystemCode(), application.patientReference(),
                application.patientName(), application.patientSexCode(), application.patientBirthDate() == null ? null
                        : Date.valueOf(application.patientBirthDate()), application.patientInfoSourceCode(),
                application.patientIdentityNo(), application.visitCardNo(), application.contactPhone(),
                application.ageValue(), application.ageUnitCode(), application.visitReference(), application.visitTypeCode(),
                application.wardReference(), application.bedReference(), application.applicationDepartment(),
                application.applicantReference(), Timestamp.from(application.appliedAt()),
                application.clinicalDiagnosis(), application.medicalHistory(), application.operationFinding(),
                application.surgeryName(), application.examinationPurpose(), application.specimenDescription(), application.note(), Timestamp.from(now),
                actorReference, application.id(), organizationReference, expectedVersion) == 1;
    }

    public void cancelPendingItems(UUID applicationId, String reason, String actorReference, Instant now) {
        jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application_item
                   SET status_code = 'CANCELLED', cancelled_at = ?, cancelled_by_ref = ?, cancellation_reason = ?
                 WHERE application_id = ? AND status_code = 'PENDING'
                """, Timestamp.from(now), actorReference, reason, applicationId);
    }

    public void insertItems(UUID applicationId, List<ApplicationItemInput> items, String actorReference, Instant now) {
        for (ApplicationItemInput item : items) {
            jdbcTemplate.update("""
                    INSERT INTO pis_v2.pathology_application_item
                        (id, application_id, external_item_code, item_name, mapping_id, business_type_id,
                         specimen_kind_code, specimen_description, sequence_no, status_code, created_at, created_by_ref)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                    """, item.id(), applicationId, item.externalItemCode(), item.itemName(), item.mappingId(),
                    item.businessTypeId(), item.specimenKindCode(), item.specimenDescription(), item.sequenceNo(),
                    Timestamp.from(now), actorReference);
        }
    }

    public boolean cancelApplication(UUID applicationId, long expectedVersion, String reason,
            String organizationReference, String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application
                   SET status_code = 'CANCELLED', cancelled_at = ?, cancelled_by_ref = ?, cancellation_reason = ?,
                       concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND status_code = 'RECEIVED'
                   AND concurrency_version = ?
                """, Timestamp.from(now), actorReference, reason, Timestamp.from(now), actorReference, applicationId,
                organizationReference, expectedVersion) == 1;
    }

    public boolean cancelApplicationAnyOpenState(UUID applicationId, long expectedVersion, String reason,
            String organizationReference, String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application
                   SET status_code = 'CANCELLED', cancelled_at = ?, cancelled_by_ref = ?, cancellation_reason = ?,
                       concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND status_code <> 'CANCELLED'
                   AND concurrency_version = ?
                """, Timestamp.from(now), actorReference, reason, Timestamp.from(now), actorReference, applicationId,
                organizationReference, expectedVersion) == 1;
    }

    public boolean cancelItem(UUID applicationId, UUID itemId, String reason, String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application_item
                   SET status_code = 'CANCELLED', cancelled_at = ?, cancelled_by_ref = ?, cancellation_reason = ?
                 WHERE application_id = ? AND id = ? AND status_code = 'PENDING'
                """, Timestamp.from(now), actorReference, reason, applicationId, itemId) == 1;
    }

    public boolean rejectItem(UUID applicationId, UUID itemId, String reasonCode, String reasonText,
            String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application_item
                   SET status_code = 'REJECTED', rejected_at = ?, rejected_by_ref = ?,
                       rejection_reason_code = ?, rejection_reason_text = ?
                 WHERE application_id = ? AND id = ? AND status_code = 'PENDING'
                """, Timestamp.from(now), actorReference, reasonCode, reasonText, applicationId, itemId) == 1;
    }

    public boolean hasLinkedCase(UUID applicationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pis_v2.pathology_application_case WHERE application_id = ?)
                """, Boolean.class, applicationId));
    }

    public UUID insertDelivery(UUID applicationId, UUID itemId, String incomingSpecimenReference,
            String specimenLabelCode, String patientReference, String actualSpecimenDescription,
            String verificationStatusCode, String reasonCode, String rejectionReason,
            boolean patientMatch, boolean applicationMatch, boolean quantityMatch, boolean specimenMatch,
            boolean containerMatch, boolean fixationMatch,
            String organizationReference, String actorReference, Instant deliveredAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_application_delivery
                    (id, application_id, application_item_id, incoming_specimen_reference, specimen_label_code,
                     patient_reference, actual_specimen_description, verification_status_code, reason_code,
                     rejection_reason, patient_match, application_match, quantity_match, specimen_match,
                     container_match, fixation_match, delivered_by_ref, delivered_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, applicationId, itemId, incomingSpecimenReference, specimenLabelCode, patientReference,
                actualSpecimenDescription, verificationStatusCode, reasonCode, rejectionReason,
                patientMatch, applicationMatch, quantityMatch, specimenMatch, containerMatch, fixationMatch,
                actorReference, Timestamp.from(deliveredAt), organizationReference);
        return id;
    }

    public boolean hasAcceptedDelivery(UUID itemId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pis_v2.pathology_application_delivery
                               WHERE application_item_id = ? AND verification_status_code = 'ACCEPTED')
                """, Boolean.class, itemId));
    }

    public Optional<DeliveryRow> findAcceptedDelivery(UUID itemId) {
        return jdbcTemplate.query("""
                SELECT d.id, d.application_item_id, d.specimen_label_code, d.patient_reference,
                       d.actual_specimen_description, d.verification_status_code, d.rejection_reason,
                       d.delivered_by_ref, d.delivered_at
                FROM pis_v2.pathology_application_delivery d
                WHERE d.application_item_id = ? AND d.verification_status_code = 'ACCEPTED'
                ORDER BY d.delivered_at DESC FETCH FIRST 1 ROW ONLY
                """, rs -> rs.next() ? Optional.of(new DeliveryRow(rs.getObject("id", UUID.class),
                        rs.getObject("application_item_id", UUID.class), rs.getString("specimen_label_code"),
                        rs.getString("patient_reference"), rs.getString("actual_specimen_description"),
                        rs.getString("verification_status_code"), rs.getString("rejection_reason"),
                        rs.getString("delivered_by_ref"), rs.getTimestamp("delivered_at").toInstant()))
                        : Optional.empty(), itemId);
    }

    public Optional<DeliveryRow> findAcceptedDeliveryByReference(String incomingReference,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.application_item_id, d.specimen_label_code, d.patient_reference,
                       d.actual_specimen_description, d.verification_status_code, d.rejection_reason,
                       d.delivered_by_ref, d.delivered_at
                FROM pis_v2.pathology_application_delivery d
                WHERE d.incoming_specimen_reference = ? AND d.organization_reference = ?
                  AND d.verification_status_code = 'ACCEPTED'
                ORDER BY d.delivered_at DESC FETCH FIRST 1 ROW ONLY
                """, rs -> rs.next() ? Optional.of(new DeliveryRow(rs.getObject("id", UUID.class),
                        rs.getObject("application_item_id", UUID.class), rs.getString("specimen_label_code"),
                        rs.getString("patient_reference"), rs.getString("actual_specimen_description"),
                        rs.getString("verification_status_code"), rs.getString("rejection_reason"),
                        rs.getString("delivered_by_ref"), rs.getTimestamp("delivered_at").toInstant()))
                        : Optional.empty(), incomingReference, organizationReference);
    }

    public Optional<BarcodeContextRow> findBarcodeContext(String barcode, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT a.id AS application_id, a.application_no, a.patient_reference, a.patient_name,
                       i.id AS item_id, i.item_name, i.specimen_description, i.status_code,
                       EXISTS (SELECT 1 FROM pis_v2.pathology_application_delivery d
                               WHERE d.application_item_id = i.id AND d.verification_status_code = 'ACCEPTED')
                           AS delivered
                FROM pis_v2.pathology_application_barcode_print p
                JOIN pis_v2.pathology_application a ON a.id = p.application_id
                JOIN pis_v2.pathology_application_item i ON i.id = p.application_item_id
                WHERE p.barcode_value = ? AND a.organization_reference = ?
                ORDER BY p.requested_at DESC FETCH FIRST 1 ROW ONLY
                """, rs -> rs.next() ? Optional.of(new BarcodeContextRow(
                        rs.getObject("application_id", UUID.class), rs.getString("application_no"),
                        rs.getString("patient_reference"), rs.getString("patient_name"),
                        rs.getObject("item_id", UUID.class), rs.getString("item_name"),
                        rs.getString("specimen_description"), rs.getString("status_code"),
                        rs.getBoolean("delivered"))) : Optional.empty(), barcode, organizationReference);
    }

    public int nextBarcodePrintVersion(UUID applicationId, UUID itemId) {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(print_version), 0) + 1
                FROM pis_v2.pathology_application_barcode_print
                WHERE application_id = ? AND ((application_item_id = ?) OR (application_item_id IS NULL AND ? IS NULL))
                """, Integer.class, applicationId, itemId, itemId);
        return value == null ? 1 : value;
    }

    public void insertBarcodePrint(UUID applicationId, UUID itemId, String barcodeValue, int printVersion,
            String operationCode, int copies, String printerProfileCode, String renderedLabel,
            String resultCode, String failureReason, String actorReference, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_application_barcode_print
                    (id, application_id, application_item_id, barcode_value, print_version, printer_profile_code,
                     operation_code, copies, rendered_label, result_code, failure_reason, requested_at, requested_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), applicationId, itemId, barcodeValue, printVersion, printerProfileCode,
                operationCode, copies, renderedLabel, resultCode, failureReason, Timestamp.from(now), actorReference);
    }

    public void insertReceiptPrint(UUID applicationId, UUID caseId, String kindCode, String operationCode,
            int copies, String printerProfileCode, String renderedReceipt, String resultCode, String failureReason,
            String actorReference, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_registration_receipt_print
                    (id, application_id, case_id, receipt_kind_code, printer_profile_code, result_code,
                     failure_reason, operation_code, copies, rendered_receipt, requested_at, requested_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), applicationId, caseId, kindCode, printerProfileCode, resultCode,
                failureReason, operationCode, copies, renderedReceipt, Timestamp.from(now), actorReference);
    }

    public List<ReceiptPrintRow> findReceiptPrints(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT p.id, p.application_id, p.case_id, p.receipt_kind_code, p.operation_code, p.copies,
                       p.printer_profile_code, p.result_code, p.failure_reason, p.requested_at, p.requested_by_ref
                FROM pis_v2.pathology_registration_receipt_print p
                JOIN pis_v2.pathology_application a ON a.id = p.application_id
                WHERE p.case_id = ? AND a.organization_reference = ?
                ORDER BY p.requested_at, p.id
                """, (rs, rowNum) -> new ReceiptPrintRow(rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("receipt_kind_code"), rs.getString("operation_code"), rs.getInt("copies"),
                        rs.getString("printer_profile_code"), rs.getString("result_code"),
                        rs.getString("failure_reason"), rs.getTimestamp("requested_at").toInstant(),
                        rs.getString("requested_by_ref")), caseId, organizationReference);
    }

    public Optional<UUID> findApplicationIdByCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT ac.application_id FROM pis_v2.pathology_application_case ac
                JOIN pis_v2.pathology_application a ON a.id = ac.application_id
                WHERE ac.case_id = ? AND a.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(),
                caseId, organizationReference);
    }

    public Optional<Instant> findRegistrationTime(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT ac.linked_at
                FROM pis_v2.pathology_application_case ac
                JOIN pis_v2.pathology_application a ON a.id = ac.application_id
                WHERE ac.case_id = ? AND a.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(rs.getTimestamp("linked_at").toInstant()) : Optional.empty(),
                caseId, organizationReference);
    }

    public boolean linkCase(UUID applicationId, UUID itemId, UUID caseId, String actorReference, Instant now) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO pis_v2.pathology_application_case
                        (id, application_id, application_item_id, case_id, linked_at, linked_by_ref)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), applicationId, itemId, caseId, Timestamp.from(now), actorReference) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean markItemRegistered(UUID itemId) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application_item SET status_code = 'REGISTERED'
                 WHERE id = ? AND status_code = 'PENDING'
                """, itemId) == 1;
    }

    public void updateApplicationStatus(UUID applicationId, String statusCode, String actorReference, Instant now) {
        jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application
                   SET status_code = ?, updated_at = ?, updated_by_ref = ?, concurrency_version = concurrency_version + 1
                 WHERE id = ?
                """, statusCode, Timestamp.from(now), actorReference, applicationId);
    }

    private static ApplicationRow application(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date birthDate = rs.getDate("patient_birth_date");
        return new ApplicationRow(rs.getObject("id", UUID.class), rs.getString("application_no"),
                rs.getString("source_type_code"), rs.getString("source_system_code"),
                rs.getString("patient_reference"), rs.getString("patient_name"), rs.getString("patient_sex_code"),
                birthDate == null ? null : birthDate.toLocalDate(), rs.getString("patient_info_source_code"),
                rs.getString("patient_identity_no"), rs.getString("visit_card_no"), rs.getString("contact_phone"),
                (Integer) rs.getObject("age_value"), rs.getString("age_unit_code"), rs.getString("visit_reference"),
                rs.getString("visit_type_code"), rs.getString("ward_reference"), rs.getString("bed_reference"),
                rs.getString("application_department"),
                rs.getString("applicant_reference"), rs.getTimestamp("applied_at").toInstant(),
                rs.getString("clinical_diagnosis"), rs.getString("medical_history"), rs.getString("operation_finding"),
                rs.getString("surgery_name"), rs.getString("examination_purpose"), rs.getString("specimen_description"), rs.getString("note"),
                rs.getString("status_code"), rs.getLong("concurrency_version"));
    }

    private static ApplicationItemRow item(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ApplicationItemRow(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getString("external_item_code"), rs.getString("item_name"), rs.getObject("mapping_id", UUID.class),
                rs.getObject("business_type_id", UUID.class), rs.getString("specimen_kind_code"),
                rs.getString("specimen_description"), rs.getInt("sequence_no"), rs.getString("status_code"),
                rs.getString("business_type_code"), rs.getObject("case_id", UUID.class), rs.getString("case_no"));
    }

    public record ApplicationRow(UUID id, String applicationNo, String sourceTypeCode, String sourceSystemCode,
            String patientReference, String patientName, String patientSexCode, LocalDate patientBirthDate,
            String patientInfoSourceCode, String patientIdentityNo, String visitCardNo, String contactPhone,
            Integer ageValue, String ageUnitCode, String visitReference, String visitTypeCode,
            String wardReference, String bedReference, String applicationDepartment, String applicantReference,
            Instant appliedAt, String clinicalDiagnosis, String medicalHistory, String operationFinding, String surgeryName,
            String examinationPurpose, String specimenDescription, String note, String statusCode,
            long concurrencyVersion) { }

    public record ApplicationItemInput(UUID id, String externalItemCode, String itemName, UUID mappingId,
            UUID businessTypeId, String specimenKindCode, String specimenDescription, int sequenceNo) { }

    public record ApplicationItemRow(UUID id, UUID applicationId, String externalItemCode, String itemName,
            UUID mappingId, UUID businessTypeId, String specimenKindCode, String specimenDescription,
            int sequenceNo, String statusCode, String businessTypeCode, UUID caseId, String pathologyNo) { }

    public record ApplicationQueueRow(UUID applicationId, String applicationNo, String sourceTypeCode,
            String sourceSystemCode, String patientReference, String patientName, String patientSexCode,
            java.time.LocalDate patientBirthDate, String visitReference,
            String applicationDepartment, String applicantReference, Instant appliedAt, String statusCode,
            UUID itemId, String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, String itemStatusCode, String businessTypeCode) { }

    public record DeliveryRow(UUID deliveryId, UUID applicationItemId, String specimenLabelCode,
            String patientReference, String actualSpecimenDescription, String verificationStatusCode,
            String rejectionReason, String deliveredByRef, Instant deliveredAt) { }

    public record DeliverySearchRow(UUID deliveryId, UUID applicationId, UUID applicationItemId,
            String applicationNo, String visitReference, String patientReference, String patientName,
            String externalItemCode, String itemName, String incomingSpecimenReference, String specimenLabelCode,
            String statusCode, String reason, String deliveredBy, Instant deliveredAt) { }

    public record BarcodePrintRow(UUID printId, UUID applicationItemId, String barcodeValue, int printVersion,
            String operationCode, int copies, String renderedLabel, String printerProfileCode,
            String resultCode, String failureReason, Instant requestedAt,
            String requestedByRef) { }

    public record ReceiptPrintRow(UUID printId, UUID applicationId, UUID caseId, String receiptKindCode,
            String operationCode, int copies, String printerProfileCode, String resultCode, String failureReason,
            Instant requestedAt, String requestedByRef) { }

    public record BarcodeContextRow(UUID applicationId, String applicationNo, String patientReference,
            String patientName, UUID applicationItemId, String itemName, String specimenDescription,
            String itemStatusCode, boolean delivered) { }
}

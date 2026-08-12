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
                     patient_sex_code, patient_birth_date, visit_reference, visit_type_code, application_department,
                     applicant_reference, applied_at, clinical_diagnosis, medical_history, operation_finding,
                     examination_purpose, specimen_description, note, status_code, organization_reference,
                     concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?, 0, ?, ?, ?, ?)
                """, application.id(), application.applicationNo(), application.sourceTypeCode(),
                application.sourceSystemCode(), application.patientReference(), application.patientName(),
                application.patientSexCode(), application.patientBirthDate() == null ? null : Date.valueOf(application.patientBirthDate()),
                application.visitReference(), application.visitTypeCode(), application.applicationDepartment(),
                application.applicantReference(), Timestamp.from(application.appliedAt()), application.clinicalDiagnosis(),
                application.medicalHistory(), application.operationFinding(), application.examinationPurpose(),
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
                       patient_sex_code, patient_birth_date, visit_reference, visit_type_code, application_department,
                       applicant_reference, applied_at, clinical_diagnosis, medical_history, operation_finding,
                       examination_purpose, specimen_description, note, status_code, concurrency_version
                FROM pis_v2.pathology_application
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(application(rs)) : Optional.empty(), applicationId,
                organizationReference);
    }

    public Optional<ApplicationItemRow> findItem(UUID itemId, UUID applicationId) {
        return jdbcTemplate.query("""
                SELECT id, application_id, external_item_code, item_name, mapping_id, business_type_id,
                       specimen_kind_code, specimen_description, sequence_no, status_code
                FROM pis_v2.pathology_application_item
                WHERE id = ? AND application_id = ?
                """, rs -> rs.next() ? Optional.of(item(rs)) : Optional.empty(), itemId, applicationId);
    }

    public List<ApplicationItemRow> findItems(UUID applicationId) {
        return jdbcTemplate.query("""
                SELECT id, application_id, external_item_code, item_name, mapping_id, business_type_id,
                       specimen_kind_code, specimen_description, sequence_no, status_code
                FROM pis_v2.pathology_application_item
                WHERE application_id = ? ORDER BY sequence_no, id
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

    public List<BarcodePrintRow> findBarcodePrints(UUID applicationId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT p.id, p.application_item_id, p.barcode_value, p.print_version,
                       p.printer_profile_code, p.result_code, p.failure_reason, p.requested_at, p.requested_by_ref
                FROM pis_v2.pathology_application_barcode_print p
                JOIN pis_v2.pathology_application a ON a.id = p.application_id
                WHERE p.application_id = ? AND a.organization_reference = ?
                ORDER BY p.requested_at, p.id
                """, (rs, rowNum) -> new BarcodePrintRow(rs.getObject("id", UUID.class),
                rs.getObject("application_item_id", UUID.class), rs.getString("barcode_value"),
                rs.getInt("print_version"), rs.getString("printer_profile_code"), rs.getString("result_code"),
                rs.getString("failure_reason"), rs.getTimestamp("requested_at").toInstant(),
                rs.getString("requested_by_ref")), applicationId, organizationReference);
    }

    public boolean updateApplication(ApplicationRow application, long expectedVersion, String organizationReference,
            String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application
                   SET source_type_code = ?, source_system_code = ?, patient_reference = ?, patient_name = ?,
                       patient_sex_code = ?, patient_birth_date = ?, visit_reference = ?, visit_type_code = ?,
                       application_department = ?, applicant_reference = ?, applied_at = ?, clinical_diagnosis = ?,
                       medical_history = ?, operation_finding = ?, examination_purpose = ?, specimen_description = ?,
                       note = ?, concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND status_code = 'RECEIVED'
                   AND concurrency_version = ?
                """, application.sourceTypeCode(), application.sourceSystemCode(), application.patientReference(),
                application.patientName(), application.patientSexCode(), application.patientBirthDate() == null ? null
                        : Date.valueOf(application.patientBirthDate()), application.visitReference(), application.visitTypeCode(),
                application.applicationDepartment(), application.applicantReference(), Timestamp.from(application.appliedAt()),
                application.clinicalDiagnosis(), application.medicalHistory(), application.operationFinding(),
                application.examinationPurpose(), application.specimenDescription(), application.note(), Timestamp.from(now),
                actorReference, application.id(), organizationReference, expectedVersion) == 1;
    }

    public void rejectPendingItems(UUID applicationId) {
        jdbcTemplate.update("""
                UPDATE pis_v2.pathology_application_item
                   SET status_code = 'REJECTED'
                 WHERE application_id = ? AND status_code = 'PENDING'
                """, applicationId);
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

    public boolean hasLinkedCase(UUID applicationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pis_v2.pathology_application_case WHERE application_id = ?)
                """, Boolean.class, applicationId));
    }

    public void insertDelivery(UUID applicationId, UUID itemId, String specimenLabelCode, String patientReference,
            String actualSpecimenDescription, String verificationStatusCode, String rejectionReason,
            String organizationReference, String actorReference, Instant deliveredAt) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_application_delivery
                    (id, application_id, application_item_id, specimen_label_code, patient_reference,
                     actual_specimen_description, verification_status_code, rejection_reason, delivered_by_ref,
                     delivered_at, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), applicationId, itemId, specimenLabelCode, patientReference,
                actualSpecimenDescription, verificationStatusCode, rejectionReason, actorReference,
                Timestamp.from(deliveredAt), organizationReference);
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
            String printerProfileCode, String resultCode, String failureReason, String actorReference, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_application_barcode_print
                    (id, application_id, application_item_id, barcode_value, print_version, printer_profile_code,
                     result_code, failure_reason, requested_at, requested_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), applicationId, itemId, barcodeValue, printVersion, printerProfileCode,
                resultCode, failureReason, Timestamp.from(now), actorReference);
    }

    public void insertReceiptPrint(UUID applicationId, UUID caseId, String kindCode, String printerProfileCode,
            String resultCode, String failureReason, String actorReference, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_registration_receipt_print
                    (id, application_id, case_id, receipt_kind_code, printer_profile_code, result_code,
                     failure_reason, requested_at, requested_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), applicationId, caseId, kindCode, printerProfileCode, resultCode,
                failureReason, Timestamp.from(now), actorReference);
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

    public void markItemRegistered(UUID itemId) {
        jdbcTemplate.update("UPDATE pis_v2.pathology_application_item SET status_code = 'REGISTERED' WHERE id = ?",
                itemId);
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
                birthDate == null ? null : birthDate.toLocalDate(), rs.getString("visit_reference"),
                rs.getString("visit_type_code"), rs.getString("application_department"),
                rs.getString("applicant_reference"), rs.getTimestamp("applied_at").toInstant(),
                rs.getString("clinical_diagnosis"), rs.getString("medical_history"), rs.getString("operation_finding"),
                rs.getString("examination_purpose"), rs.getString("specimen_description"), rs.getString("note"),
                rs.getString("status_code"), rs.getLong("concurrency_version"));
    }

    private static ApplicationItemRow item(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ApplicationItemRow(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getString("external_item_code"), rs.getString("item_name"), rs.getObject("mapping_id", UUID.class),
                rs.getObject("business_type_id", UUID.class), rs.getString("specimen_kind_code"),
                rs.getString("specimen_description"), rs.getInt("sequence_no"), rs.getString("status_code"));
    }

    public record ApplicationRow(UUID id, String applicationNo, String sourceTypeCode, String sourceSystemCode,
            String patientReference, String patientName, String patientSexCode, LocalDate patientBirthDate,
            String visitReference, String visitTypeCode, String applicationDepartment, String applicantReference,
            Instant appliedAt, String clinicalDiagnosis, String medicalHistory, String operationFinding,
            String examinationPurpose, String specimenDescription, String note, String statusCode,
            long concurrencyVersion) { }

    public record ApplicationItemInput(UUID id, String externalItemCode, String itemName, UUID mappingId,
            UUID businessTypeId, String specimenKindCode, String specimenDescription, int sequenceNo) { }

    public record ApplicationItemRow(UUID id, UUID applicationId, String externalItemCode, String itemName,
            UUID mappingId, UUID businessTypeId, String specimenKindCode, String specimenDescription,
            int sequenceNo, String statusCode) { }

    public record ApplicationQueueRow(UUID applicationId, String applicationNo, String sourceTypeCode,
            String sourceSystemCode, String patientReference, String patientName, String patientSexCode,
            java.time.LocalDate patientBirthDate, String visitReference,
            String applicationDepartment, String applicantReference, Instant appliedAt, String statusCode,
            UUID itemId, String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, String itemStatusCode, String businessTypeCode) { }

    public record DeliveryRow(UUID deliveryId, UUID applicationItemId, String specimenLabelCode,
            String patientReference, String actualSpecimenDescription, String verificationStatusCode,
            String rejectionReason, String deliveredByRef, Instant deliveredAt) { }

    public record BarcodePrintRow(UUID printId, UUID applicationItemId, String barcodeValue, int printVersion,
            String printerProfileCode, String resultCode, String failureReason, Instant requestedAt,
            String requestedByRef) { }
}

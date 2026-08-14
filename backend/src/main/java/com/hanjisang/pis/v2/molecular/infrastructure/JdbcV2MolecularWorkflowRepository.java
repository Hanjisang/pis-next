package com.hanjisang.pis.v2.molecular.infrastructure;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2MolecularWorkflowRepository {
    private final JdbcTemplate jdbc;

    public JdbcV2MolecularWorkflowRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<ProjectRow> project(UUID id, String organization) {
        return jdbc.query("SELECT id, project_code, project_name, project_type_code, enabled FROM pis_v2.molecular_project WHERE id=? AND organization_reference=?",
                rs -> rs.next() ? Optional.of(new ProjectRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5))) : Optional.empty(), id, organization);
    }

    public boolean lockCase(UUID caseId, String organization) {
        return jdbc.query("SELECT id FROM pis_v2.pathology_case WHERE id=? AND organization_reference=? FOR UPDATE",
                (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(), caseId, organization);
    }

    public Optional<InstrumentRow> instrument(UUID id, String organization) {
        return jdbc.query("SELECT id, instrument_code, name, adapter_code, enabled FROM pis_v2.molecular_instrument WHERE id=? AND organization_reference=?",
                rs -> rs.next() ? Optional.of(new InstrumentRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5))) : Optional.empty(), id, organization);
    }

    public Optional<ReagentRow> reagent(UUID id, String organization) {
        return jdbc.query("SELECT id, kit_code, manufacturer, batch_no, expiry_date, enabled FROM pis_v2.molecular_reagent_kit WHERE id=? AND organization_reference=?",
                rs -> rs.next() ? Optional.of(new ReagentRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(), rs.getBoolean(6))) : Optional.empty(), id, organization);
    }

    public UUID insertTest(UUID caseId, UUID specimenId, UUID projectId, String detectionNo, UUID instrumentId,
            UUID reagentId, String rawReference, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_test
                  (id, case_id, specimen_id, project_id, detection_no, instrument_id, reagent_kit_id,
                   raw_data_reference, status_code, created_at, created_by_ref, organization_reference, concurrency_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED', ?, ?, ?, 0)
                """, id, caseId, specimenId, projectId, detectionNo, instrumentId, reagentId, rawReference,
                Timestamp.from(now), actor, organization);
        return id;
    }

    public Optional<TestRow> lockTest(UUID id, String organization) {
        return jdbc.query("""
                SELECT mt.id, mt.case_id, mt.specimen_id, mt.project_id, mp.project_code, mt.detection_no,
                       mt.instrument_id, mi.instrument_code, mi.adapter_code, mt.reagent_kit_id,
                       mt.raw_data_reference, mt.structured_result, mt.analysis_result, mt.status_code,
                       mt.result_id, mt.created_at, mt.started_at, mt.completed_at, mt.concurrency_version
                FROM pis_v2.molecular_test mt JOIN pis_v2.molecular_project mp ON mp.id=mt.project_id
                LEFT JOIN pis_v2.molecular_instrument mi ON mi.id=mt.instrument_id
                WHERE mt.id=? AND mt.organization_reference=? FOR UPDATE
                """, rs -> rs.next() ? Optional.of(testRow(rs)) : Optional.empty(), id, organization);
    }

    public boolean start(UUID id, long version, String actor, Instant now, String organization) {
        return jdbc.update("""
                UPDATE pis_v2.molecular_test SET status_code='RUNNING', started_at=?, started_by_ref=?, concurrency_version=concurrency_version+1
                WHERE id=? AND organization_reference=? AND status_code='REQUESTED' AND concurrency_version=?
                """, Timestamp.from(now), actor, id, organization, version) == 1;
    }

    public boolean complete(UUID id, long version, String structured, String analysis, UUID resultId,
            String actor, Instant now, String organization) {
        return jdbc.update("""
                UPDATE pis_v2.molecular_test SET status_code='COMPLETED', structured_result=?, analysis_result=?,
                       result_id=?, completed_at=?, completed_by_ref=?, concurrency_version=concurrency_version+1
                WHERE id=? AND organization_reference=? AND status_code='RUNNING' AND result_id IS NULL AND concurrency_version=?
                """, structured, analysis, resultId, Timestamp.from(now), actor, id, organization, version) == 1;
    }

    public UUID insertAttempt(TestRow test, int attemptNo, String requestReference, boolean accepted,
            String responseReference, String errorCode, String errorMessage, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_instrument_attempt
                  (id, molecular_test_id, instrument_id, adapter_code, attempt_no, request_reference, status_code,
                   response_reference, error_code, error_message, requested_at, completed_at, requested_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, test.id(), test.instrumentId(), test.adapterCode(), attemptNo, requestReference,
                accepted ? "ACCEPTED" : "FAILED", responseReference, errorCode, errorMessage,
                Timestamp.from(now), Timestamp.from(now), actor, organization);
        return id;
    }

    public int nextAttempt(UUID testId) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*)+1 FROM pis_v2.molecular_instrument_attempt WHERE molecular_test_id=?", Integer.class, testId);
        return value == null ? 1 : value;
    }

    public UUID insertAttachment(UUID testId, UUID digitalSlideId, String attachmentReference, String description,
            String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.molecular_test_attachment
                  (id, molecular_test_id, digital_slide_id, attachment_reference, description, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, testId, digitalSlideId, attachmentReference, description, organization, Timestamp.from(now), actor);
        return id;
    }

    public boolean digitalSlideBelongsToCase(UUID slideId, UUID caseId, String organization) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.digital_slide ds JOIN pis_v2.pathology_case pc ON pc.id=ds.case_id
                WHERE ds.id=? AND ds.case_id=? AND pc.organization_reference=?
                """, Integer.class, slideId, caseId, organization);
        return count != null && count == 1;
    }

    public List<ProjectRow> projects(String organization) {
        return jdbc.query("SELECT id, project_code, project_name, project_type_code, enabled FROM pis_v2.molecular_project WHERE organization_reference=? ORDER BY project_code",
                (rs,n)->new ProjectRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5)), organization);
    }
    public List<InstrumentRow> instruments(String organization) {
        return jdbc.query("SELECT id, instrument_code, name, adapter_code, enabled FROM pis_v2.molecular_instrument WHERE organization_reference=? ORDER BY instrument_code",
                (rs,n)->new InstrumentRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5)), organization);
    }
    public List<ReagentRow> reagents(String organization) {
        return jdbc.query("SELECT id, kit_code, manufacturer, batch_no, expiry_date, enabled FROM pis_v2.molecular_reagent_kit WHERE organization_reference=? ORDER BY kit_code,batch_no",
                (rs,n)->new ReagentRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getDate(5)==null?null:rs.getDate(5).toLocalDate(),rs.getBoolean(6)), organization);
    }
    public List<TestRow> tests(String organization) {
        return jdbc.query("""
                SELECT mt.id, mt.case_id, mt.specimen_id, mt.project_id, mp.project_code, mt.detection_no,
                       mt.instrument_id, mi.instrument_code, mi.adapter_code, mt.reagent_kit_id,
                       mt.raw_data_reference, mt.structured_result, mt.analysis_result, mt.status_code,
                       mt.result_id, mt.created_at, mt.started_at, mt.completed_at, mt.concurrency_version
                FROM pis_v2.molecular_test mt JOIN pis_v2.molecular_project mp ON mp.id=mt.project_id
                LEFT JOIN pis_v2.molecular_instrument mi ON mi.id=mt.instrument_id
                WHERE mt.organization_reference=? ORDER BY CASE mt.status_code WHEN 'RUNNING' THEN 0 WHEN 'REQUESTED' THEN 1 ELSE 2 END, mt.created_at
                """, (rs,n)->testRow(rs), organization);
    }
    public List<AttachmentRow> attachments(String organization) {
        return jdbc.query("""
                SELECT id, molecular_test_id, digital_slide_id, attachment_reference, description, created_at, created_by_ref
                FROM pis_v2.molecular_test_attachment WHERE organization_reference=? ORDER BY created_at
                """, (rs,n)->new AttachmentRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getString(7)), organization);
    }
    public List<AttemptRow> attempts(String organization) {
        return jdbc.query("""
                SELECT id, molecular_test_id, instrument_id, adapter_code, attempt_no, request_reference, status_code,
                       response_reference, error_code, error_message, requested_at, completed_at, requested_by_ref
                FROM pis_v2.molecular_instrument_attempt WHERE organization_reference=? ORDER BY requested_at
                """, (rs,n)->new AttemptRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),
                rs.getString(4),rs.getInt(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),
                rs.getString(10),rs.getTimestamp(11).toInstant(),rs.getTimestamp(12).toInstant(),rs.getString(13)), organization);
    }

    public Optional<CommandReplay> commandReplay(String operation, String key, String organization) {
        return jdbc.query("SELECT payload_digest, result_entity_id FROM pis_v2.molecular_command_idempotency WHERE organization_reference=? AND operation_code=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new CommandReplay(rs.getString(1), rs.getObject(2, UUID.class))) : Optional.empty(), organization, operation, key);
    }

    public void insertCommand(String operation, String key, String digest, UUID resultId, String organization,
            String actor, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.molecular_command_idempotency
                  (id, organization_reference, operation_code, idempotency_key, payload_digest, result_entity_id, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), organization, operation, key, digest, resultId, Timestamp.from(now), actor);
    }

    private static TestRow testRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TestRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),
                rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getObject(7,UUID.class),rs.getString(8),
                rs.getString(9),rs.getObject(10,UUID.class),rs.getString(11),rs.getString(12),rs.getString(13),
                rs.getString(14),rs.getObject(15,UUID.class),rs.getTimestamp(16).toInstant(),
                rs.getTimestamp(17)==null?null:rs.getTimestamp(17).toInstant(),
                rs.getTimestamp(18)==null?null:rs.getTimestamp(18).toInstant(),rs.getLong(19));
    }

    public record ProjectRow(UUID id,String projectCode,String projectName,String projectTypeCode,boolean enabled) { }
    public record InstrumentRow(UUID id,String instrumentCode,String name,String adapterCode,boolean enabled) { }
    public record ReagentRow(UUID id,String kitCode,String manufacturer,String batchNo,LocalDate expiryDate,boolean enabled) { }
    public record TestRow(UUID id,UUID caseId,UUID specimenId,UUID projectId,String projectCode,String detectionNo,
            UUID instrumentId,String instrumentCode,String adapterCode,UUID reagentKitId,String rawDataReference,
            String structuredResult,String analysisResult,String statusCode,UUID resultId,Instant createdAt,
            Instant startedAt,Instant completedAt,long concurrencyVersion) { }
    public record CommandReplay(String payloadDigest, UUID resultEntityId) { }
    public record AttachmentRow(UUID id, UUID testId, UUID digitalSlideId, String attachmentReference,
            String description, Instant createdAt, String createdBy) { }
    public record AttemptRow(UUID id, UUID testId, UUID instrumentId, String adapterCode, int attemptNo,
            String requestReference, String statusCode, String responseReference, String errorCode,
            String errorMessage, Instant requestedAt, Instant completedAt, String requestedBy) { }
}

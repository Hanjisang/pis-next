package com.hanjisang.pis.accession.infrastructure;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.accession.domain.PathologyCase;
import com.hanjisang.pis.accession.domain.PathologyRequest;

@Repository
public class JdbcRegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> findRequestByExternal(String sourceSystemCode, String externalRequestId) {
        return queryUuid("SELECT request_id FROM pis.external_request_reference WHERE source_system_code = ? AND external_request_id = ?",
                sourceSystemCode, externalRequestId);
    }

    public Optional<String> findExternalDigest(String sourceSystemCode, String externalRequestId) {
        return jdbcTemplate.query("SELECT idempotency_digest FROM pis.external_request_reference WHERE source_system_code = ? AND external_request_id = ?",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), sourceSystemCode, externalRequestId);
    }

    public Optional<String> findRawDigest(String sourceSystemCode, String sourceMessageIdentity) {
        return jdbcTemplate.query("SELECT payload_digest FROM pis.inbound_raw_message WHERE source_system_code = ? AND source_message_identity = ?",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), sourceSystemCode, sourceMessageIdentity);
    }

    public PathologyRequest insertExternal(String sourceSystemCode, String externalRequestId, String messageIdentity,
            String messageVersion, String digest, String rawReference, String patientId, String visitId,
            String modalityCode, String content, String applicationNo, String actorRef, Instant now) {
        UUID patientReferenceId = ensurePatient(sourceSystemCode, patientId, actorRef, now);
        UUID visitReferenceId = visitId == null || visitId.isBlank() ? null
                : ensureVisit(sourceSystemCode, visitId, patientReferenceId, actorRef, now);
        UUID requestId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO pis.inbound_raw_message
                    (id, source_system_code, source_message_identity, source_message_version, payload_digest,
                     raw_payload_reference, received_at, processing_state_code, created_by_ref)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)
                    """, UUID.randomUUID(), sourceSystemCode, messageIdentity, messageVersion, digest, rawReference,
                    Timestamp.from(now), actorRef);
            jdbcTemplate.update("""
                    INSERT INTO pis.pathology_request
                    (id, source_system_code, application_no, application_lifecycle_state_code, patient_reference_id,
                     visit_reference_id, request_received_at, request_channel_code, request_content_text,
                     pathology_modality_code, record_version_no, concurrency_version, source_message_identity,
                     source_message_digest, created_at, created_by_ref)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'EXTERNAL', ?, ?, 1, 0, ?, ?, ?, ?)
                    """, requestId, sourceSystemCode, applicationNo, PathologyRequest.WAITING_VERIFICATION,
                    patientReferenceId, visitReferenceId, Timestamp.from(now), content, modalityCode, messageIdentity, digest, Timestamp.from(now),
                    actorRef);
            jdbcTemplate.update("""
                    INSERT INTO pis.external_request_reference
                    (id, request_id, source_system_code, external_request_id, external_request_kind_code,
                     idempotency_digest, first_received_at, created_at, created_by_ref)
                    VALUES (?, ?, ?, ?, 'PATHOLOGY_APPLICATION', ?, ?, ?, ?)
                    """, UUID.randomUUID(), requestId, sourceSystemCode, externalRequestId, digest, Timestamp.from(now), Timestamp.from(now),
                    actorRef);
            jdbcTemplate.update("""
                    INSERT INTO pis.inbox_consumption
                    (id, source_system_code, source_message_identity, payload_digest, first_result_reference, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), sourceSystemCode, messageIdentity, digest, requestId.toString(), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
        return PathologyRequest.received(requestId, applicationNo, sourceSystemCode, modalityCode, now);
    }

    public PathologyRequest insertManual(String modalityCode, String content, String reason, String applicationNo,
            String actorRef, Instant now) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis.pathology_request
                (id, source_system_code, application_no, application_lifecycle_state_code, request_received_at,
                 request_channel_code, request_content_text, pathology_modality_code, record_version_no,
                 concurrency_version, source_message_digest, manual_reason, created_at, created_by_ref)
                VALUES (?, 'MANUAL', ?, ?, ?, 'MANUAL', ?, ?, 1, 0, ?, ?, ?, ?)
                """, requestId, applicationNo, PathologyRequest.WAITING_VERIFICATION, Timestamp.from(now), content, modalityCode,
                UUID.randomUUID().toString(), reason, Timestamp.from(now), actorRef);
        return PathologyRequest.received(requestId, applicationNo, "MANUAL", modalityCode, now);
    }

    public Optional<PathologyRequest> findRequest(UUID requestId) {
        return jdbcTemplate.query("""
                SELECT id, application_no, source_system_code, pathology_modality_code,
                       application_lifecycle_state_code, concurrency_version, request_received_at
                FROM pis.pathology_request WHERE id = ?
                """, rs -> rs.next()
                ? Optional.of(PathologyRequest.received(rs.getObject("id", UUID.class), rs.getString("application_no"),
                        rs.getString("source_system_code"), rs.getString("pathology_modality_code"),
                        rs.getTimestamp("request_received_at").toInstant()))
                : Optional.empty(), requestId);
    }

    public long currentRequestVersion(UUID requestId) {
        Long value = jdbcTemplate.queryForObject("SELECT concurrency_version FROM pis.pathology_request WHERE id = ?",
                Long.class, requestId);
        return value == null ? -1 : value;
    }

    public String findRequestState(UUID requestId) {
        return jdbcTemplate.query("SELECT application_lifecycle_state_code FROM pis.pathology_request WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, requestId);
    }

    public boolean acceptRequest(UUID requestId, long expectedVersion, String actorRef, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis.pathology_request
                   SET application_lifecycle_state_code = ?, concurrency_version = concurrency_version + 1,
                       record_version_no = record_version_no + 1
                 WHERE id = ? AND application_lifecycle_state_code = ? AND concurrency_version = ?
                """, PathologyRequest.ESTABLISHED, requestId, PathologyRequest.WAITING_VERIFICATION, expectedVersion) == 1;
    }

    public Optional<UUID> findCaseByRequest(UUID requestId) {
        return queryUuid("SELECT id FROM pis.pathology_case WHERE request_id = ?", requestId);
    }

    public PathologyCase insertCase(UUID requestId, String caseNo, String modalityCode, UUID snapshotId,
            String organizationReference, String actorRef, Instant now) {
        UUID caseId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis.pathology_case
                (id, case_no, case_lifecycle_state_code, request_id, patient_visit_snapshot_id,
                 pathology_modality_code, case_source_code, case_established_at, case_effective_at,
                 record_version_no, concurrency_version, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, 'LOCAL_REGISTRATION', ?, ?, 1, 0, ?, ?, ?)
                """, caseId, caseNo, PathologyCase.ESTABLISHED, requestId, snapshotId, modalityCode, Timestamp.from(now), Timestamp.from(now), organizationReference, Timestamp.from(now),
                actorRef);
        return PathologyCase.establish(caseId, caseNo, requestId, snapshotId, modalityCode, now);
    }

    public UUID createSnapshot(String patientId, String visitId, String actorRef, Instant now) {
        UUID patientReferenceId = ensurePatient("P15-SYNTHETIC", patientId, actorRef, now);
        UUID visitReferenceId = visitId == null || visitId.isBlank() ? null
                : ensureVisit("P15-SYNTHETIC", visitId, patientReferenceId, actorRef, now);
        UUID snapshotId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis.patient_visit_snapshot
                (id, patient_reference_id, visit_reference_id, snapshot_version_no, snapshot_digest, created_at, created_by_ref)
                VALUES (?, ?, ?, 1, ?, ?, ?)
                """, snapshotId, patientReferenceId, visitReferenceId, UUID.randomUUID().toString(), Timestamp.from(now), actorRef);
        return snapshotId;
    }

    public Optional<PathologyCase> findCase(UUID caseId) {
        return jdbcTemplate.query("SELECT id, case_no, request_id, patient_visit_snapshot_id, pathology_modality_code, case_established_at FROM pis.pathology_case WHERE id = ?",
                rs -> rs.next() ? Optional.of(PathologyCase.establish(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                        rs.getTimestamp(6).toInstant())) : Optional.empty(), caseId);
    }

    private UUID ensurePatient(String sourceSystemCode, String externalPatientId, String actorRef, Instant now) {
        if (externalPatientId == null || externalPatientId.isBlank()) {
            return null;
        }
        UUID id = UUID.nameUUIDFromBytes((sourceSystemCode + ":patient:" + externalPatientId).getBytes());
        jdbcTemplate.update("""
                INSERT INTO pis.patient_context_reference
                (id, source_system_code, external_patient_id, patient_namespace_code, reference_state_code, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P15-SYNTHETIC', 'VERIFIED', ?, ?)
                ON CONFLICT (source_system_code, external_patient_id, patient_namespace_code) DO NOTHING
                """, id, sourceSystemCode, externalPatientId, Timestamp.from(now), actorRef);
        return id;
    }

    private UUID ensureVisit(String sourceSystemCode, String externalVisitId, UUID patientReferenceId, String actorRef,
            Instant now) {
        UUID id = UUID.nameUUIDFromBytes((sourceSystemCode + ":visit:" + externalVisitId).getBytes());
        jdbcTemplate.update("""
                INSERT INTO pis.visit_context_reference
                (id, source_system_code, external_visit_id, visit_namespace_code, patient_reference_id,
                 reference_state_code, created_at, created_by_ref)
                VALUES (?, ?, ?, 'P15-SYNTHETIC', ?, 'VERIFIED', ?, ?)
                ON CONFLICT (source_system_code, external_visit_id, visit_namespace_code) DO NOTHING
                """, id, sourceSystemCode, externalVisitId, patientReferenceId, Timestamp.from(now), actorRef);
        return id;
    }

    private Optional<UUID> queryUuid(String sql, Object... args) {
        return jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), args);
    }
}

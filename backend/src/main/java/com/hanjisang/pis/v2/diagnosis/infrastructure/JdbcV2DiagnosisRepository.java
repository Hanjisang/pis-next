package com.hanjisang.pis.v2.diagnosis.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.diagnosis.domain.AssignmentRule;
import com.hanjisang.pis.v2.diagnosis.domain.AssignmentSource;
import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisContextType;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisTemplate;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisTemplateVersion;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityRole;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityUnit;

@Repository
public class JdbcV2DiagnosisRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public JdbcV2DiagnosisRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        Boolean databaseIsPostgres = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres"));
        this.postgres = Boolean.TRUE.equals(databaseIsPostgres);
    }

    public boolean lockCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.pathology_case
                WHERE id = ? AND organization_reference = ?
                FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), caseId, organizationReference);
    }

    public boolean lockDiagnosis(UUID diagnosisId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.diagnosis
                WHERE id = ? AND organization_reference = ?
                FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), diagnosisId, organizationReference);
    }

    public boolean lockTemplate(UUID templateId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id FROM pis_v2.diagnosis_template
                WHERE id = ? AND organization_reference = ?
                FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), templateId, organizationReference);
    }

    public Optional<Diagnosis> findDiagnosisByCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.case_id, d.context_type, d.context_id, d.template_version_id,
                       d.structured_data, d.microscopic_description, d.diagnosis_text, d.comment_text,
                       d.concurrency_version, d.created_at, d.created_by_ref, d.updated_at, d.updated_by_ref
                FROM pis_v2.diagnosis d
                WHERE d.case_id = ? AND d.organization_reference = ?
                  AND d.context_type = 'CASE' AND d.context_id = d.case_id
                """, rs -> rs.next() ? Optional.of(toDiagnosis(rs)) : Optional.empty(), caseId, organizationReference);
    }

    public Optional<Diagnosis> findDiagnosis(UUID diagnosisId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.case_id, d.context_type, d.context_id, d.template_version_id,
                       d.structured_data, d.microscopic_description, d.diagnosis_text, d.comment_text,
                       d.concurrency_version, d.created_at, d.created_by_ref, d.updated_at, d.updated_by_ref
                FROM pis_v2.diagnosis d
                WHERE d.id = ? AND d.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toDiagnosis(rs)) : Optional.empty(), diagnosisId,
                organizationReference);
    }

    public Optional<Diagnosis> findDiagnosisByContext(DiagnosisContextType contextType, UUID contextId,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.case_id, d.context_type, d.context_id, d.template_version_id,
                       d.structured_data, d.microscopic_description, d.diagnosis_text, d.comment_text,
                       d.concurrency_version, d.created_at, d.created_by_ref, d.updated_at, d.updated_by_ref
                FROM pis_v2.diagnosis d
                WHERE d.context_type = ? AND d.context_id = ? AND d.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toDiagnosis(rs)) : Optional.empty(), contextType.name(), contextId,
                organizationReference);
    }

    public void insertDiagnosis(Diagnosis diagnosis, String organizationReference, Instant now, String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.diagnosis
                    (id, case_id, context_type, context_id, template_version_id, structured_data,
                     microscopic_description, diagnosis_text, comment_text, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, diagnosis.id(), diagnosis.caseId(), diagnosis.contextType().name(), diagnosis.contextId(),
                diagnosis.templateVersionId(), json(diagnosis.structuredData()), diagnosis.microscopicDescription(),
                diagnosis.diagnosisText(), diagnosis.comment(), diagnosis.version(), organizationReference,
                Timestamp.from(now), actorRef, Timestamp.from(now), actorRef);
    }

    public boolean updateDiagnosis(Diagnosis diagnosis, String organizationReference, long expectedVersion,
            Instant now, String actorRef) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.diagnosis
                   SET template_version_id = ?, structured_data = ?, microscopic_description = ?,
                       diagnosis_text = ?, comment_text = ?, concurrency_version = ?,
                       updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                """, diagnosis.templateVersionId(), json(diagnosis.structuredData()),
                diagnosis.microscopicDescription(), diagnosis.diagnosisText(), diagnosis.comment(), diagnosis.version(),
                Timestamp.from(now), actorRef, diagnosis.id(), organizationReference, expectedVersion) == 1;
    }

    public Optional<DiagnosisTemplate> findTemplate(UUID templateId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, template_code, template_name, business_type_id, scope_code, enabled,
                       concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref
                FROM pis_v2.diagnosis_template
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toTemplate(rs)) : Optional.empty(), templateId,
                organizationReference);
    }

    public Optional<DiagnosisTemplateVersion> findPublishedTemplateVersion(UUID businessTypeId,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT tv.id, tv.template_id, tv.version_no, CAST(tv.schema_definition AS VARCHAR) AS schema_definition,
                       tv.status_code, tv.published_at, tv.published_by_ref, tv.created_at, tv.created_by_ref,
                       tv.concurrency_version
                FROM pis_v2.diagnosis_template_version tv
                JOIN pis_v2.diagnosis_template t ON t.id = tv.template_id
                WHERE t.business_type_id = ? AND t.organization_reference = ? AND t.enabled = TRUE
                  AND tv.status_code = 'PUBLISHED'
                ORDER BY tv.version_no DESC
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(toTemplateVersion(rs)) : Optional.empty(), businessTypeId,
                organizationReference);
    }

    public Optional<DiagnosisTemplateVersion> findTemplateVersion(UUID versionId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT tv.id, tv.template_id, tv.version_no, CAST(tv.schema_definition AS VARCHAR) AS schema_definition,
                       tv.status_code, tv.published_at, tv.published_by_ref, tv.created_at, tv.created_by_ref,
                       tv.concurrency_version
                FROM pis_v2.diagnosis_template_version tv
                JOIN pis_v2.diagnosis_template t ON t.id = tv.template_id
                WHERE tv.id = ? AND t.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toTemplateVersion(rs)) : Optional.empty(), versionId,
                organizationReference);
    }

    public Optional<DiagnosisTemplateVersion> findTemplateVersionForUpdate(UUID versionId,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT tv.id, tv.template_id, tv.version_no, CAST(tv.schema_definition AS VARCHAR) AS schema_definition,
                       tv.status_code, tv.published_at, tv.published_by_ref, tv.created_at, tv.created_by_ref,
                       tv.concurrency_version
                FROM pis_v2.diagnosis_template_version tv
                JOIN pis_v2.diagnosis_template t ON t.id = tv.template_id
                WHERE tv.id = ? AND t.organization_reference = ?
                FOR UPDATE
                """, rs -> rs.next() ? Optional.of(toTemplateVersion(rs)) : Optional.empty(), versionId,
                organizationReference);
    }

    public void insertTemplate(DiagnosisTemplate template, String organizationReference, Instant now,
            String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.diagnosis_template
                    (id, organization_reference, template_code, template_name, business_type_id, scope_code,
                     enabled, concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, template.id(), organizationReference, template.code(), template.name(), template.businessTypeId(),
                template.scope(), template.enabled(), template.version(), Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef);
    }

    public boolean updateTemplate(DiagnosisTemplate template, String organizationReference, long expectedVersion,
            Instant now, String actorRef) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.diagnosis_template
                   SET template_name = ?, enabled = ?, concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND concurrency_version = ?
                """, template.name(), template.enabled(), template.version(), Timestamp.from(now), actorRef,
                template.id(), organizationReference, expectedVersion) == 1;
    }

    public int nextTemplateVersion(UUID templateId) {
        Integer next = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) + 1
                FROM pis_v2.diagnosis_template_version
                WHERE template_id = ?
                """, Integer.class, templateId);
        return next == null ? 1 : next;
    }

    public void insertTemplateVersion(DiagnosisTemplateVersion version) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.diagnosis_template_version
                    (id, template_id, version_no, schema_definition, status_code, published_at, published_by_ref,
                     created_at, created_by_ref, concurrency_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, version.id(), version.templateId(), version.versionNo(), json(version.schemaDefinition()),
                version.status(), nullableTimestamp(version.publishedAt()), version.publishedBy(),
                Timestamp.from(version.createdAt()), version.createdBy(), version.version());
    }

    public boolean publishTemplateVersion(UUID versionId, String organizationReference, Instant now, String actorRef) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.diagnosis_template_version tv
                   SET status_code = 'PUBLISHED', published_at = ?, published_by_ref = ?
                 WHERE tv.id = ? AND tv.status_code = 'DRAFT'
                   AND EXISTS (SELECT 1 FROM pis_v2.diagnosis_template t
                               WHERE t.id = tv.template_id AND t.organization_reference = ?)
                """, Timestamp.from(now), actorRef, versionId, organizationReference) == 1;
    }

    public int nextResponsibilitySequence(UUID diagnosisId) {
        Integer next = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0) + 1
                FROM pis_v2.responsibility_unit
                WHERE diagnosis_id = ?
                """, Integer.class, diagnosisId);
        return next == null ? 1 : next;
    }

    public void insertResponsibility(ResponsibilityUnit responsibility) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.responsibility_unit
                    (id, diagnosis_id, role_code, doctor_id, sequence_no, accepted_at, completed_at, ended_at,
                     end_reason, assignment_source_code, assignment_reason, created_at, created_by_ref,
                     concurrency_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, responsibility.id(), responsibility.diagnosisId(), responsibility.role().name(),
                responsibility.doctorId(), responsibility.sequence(), Timestamp.from(responsibility.acceptedAt()),
                nullableTimestamp(responsibility.completedAt()), nullableTimestamp(responsibility.endedAt()),
                responsibility.endReason(), responsibility.assignmentSource().name(), responsibility.assignmentReason(),
                Timestamp.from(responsibility.createdAt()), responsibility.createdBy(), responsibility.version());
    }

    public List<ResponsibilityUnit> findResponsibilities(UUID diagnosisId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT r.id, r.diagnosis_id, r.role_code, r.doctor_id, r.sequence_no, r.accepted_at,
                       r.completed_at, r.ended_at, r.end_reason, r.assignment_source_code, r.assignment_reason,
                       r.created_at, r.created_by_ref, r.concurrency_version
                FROM pis_v2.responsibility_unit r
                JOIN pis_v2.diagnosis d ON d.id = r.diagnosis_id
                WHERE r.diagnosis_id = ? AND d.organization_reference = ?
                ORDER BY r.sequence_no
                """, (rs, rowNum) -> toResponsibility(rs), diagnosisId, organizationReference);
    }

    public Optional<ResponsibilityUnit> findCurrentResponsibility(UUID diagnosisId, ResponsibilityRole role,
            String organizationReference) {
        return jdbcTemplate.query("""
                SELECT r.id, r.diagnosis_id, r.role_code, r.doctor_id, r.sequence_no, r.accepted_at,
                       r.completed_at, r.ended_at, r.end_reason, r.assignment_source_code, r.assignment_reason,
                       r.created_at, r.created_by_ref, r.concurrency_version
                FROM pis_v2.responsibility_unit r
                JOIN pis_v2.diagnosis d ON d.id = r.diagnosis_id
                WHERE r.diagnosis_id = ? AND d.organization_reference = ? AND r.role_code = ?
                  AND r.completed_at IS NULL AND r.ended_at IS NULL
                ORDER BY r.sequence_no DESC
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(toResponsibility(rs)) : Optional.empty(), diagnosisId,
                organizationReference, role.name());
    }

    public boolean completeResponsibility(ResponsibilityUnit responsibility, String organizationReference,
            long expectedVersion) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.responsibility_unit r
                   SET completed_at = ?, concurrency_version = ?
                 WHERE r.id = ? AND r.concurrency_version = ? AND r.completed_at IS NULL AND r.ended_at IS NULL
                   AND EXISTS (SELECT 1 FROM pis_v2.diagnosis d
                               WHERE d.id = r.diagnosis_id AND d.organization_reference = ?)
                """, Timestamp.from(responsibility.completedAt()), responsibility.version(), responsibility.id(),
                expectedVersion, organizationReference) == 1;
    }

    /** Reopens only the last non-ended AUDIT node after a report withdrawal. */
    public boolean reopenLastAuditResponsibility(UUID diagnosisId, String organizationReference) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.responsibility_unit r
                   SET completed_at = NULL, concurrency_version = concurrency_version + 1
                 WHERE r.id = (
                     SELECT candidate.id FROM pis_v2.responsibility_unit candidate
                     JOIN pis_v2.diagnosis d ON d.id = candidate.diagnosis_id
                     WHERE candidate.diagnosis_id = ? AND candidate.role_code = 'AUDIT'
                       AND candidate.ended_at IS NULL AND candidate.completed_at IS NOT NULL
                       AND d.organization_reference = ?
                     ORDER BY candidate.sequence_no DESC
                     LIMIT 1
                 )
                   AND r.completed_at IS NOT NULL AND r.ended_at IS NULL
                """, diagnosisId, organizationReference) == 1;
    }

    public boolean endResponsibility(ResponsibilityUnit responsibility, String organizationReference,
            long expectedVersion) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.responsibility_unit r
                   SET ended_at = ?, end_reason = ?, concurrency_version = ?
                 WHERE r.id = ? AND r.concurrency_version = ? AND r.completed_at IS NULL AND r.ended_at IS NULL
                   AND EXISTS (SELECT 1 FROM pis_v2.diagnosis d
                               WHERE d.id = r.diagnosis_id AND d.organization_reference = ?)
                """, Timestamp.from(responsibility.endedAt()), responsibility.endReason(), responsibility.version(),
                responsibility.id(), expectedVersion, organizationReference) == 1;
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String key) {
        return jdbcTemplate.query("""
                SELECT payload_digest, result_kind_code, result_entity_id
                FROM pis_v2.diagnosis_command_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getString(1), rs.getString(2),
                rs.getObject(3, UUID.class))) : Optional.empty(), operationCode, key);
    }

    public boolean insertIdempotency(String operationCode, String key, String digest, String resultKind,
            UUID resultEntityId, String actorRef, Instant now) {
        return jdbcTemplate.update("""
                INSERT INTO pis_v2.diagnosis_command_idempotency
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code, result_entity_id,
                     created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operationCode, key, digest, resultKind, resultEntityId,
                Timestamp.from(now), actorRef) == 1;
    }

    public List<AssignmentRule> findAssignmentRules(String organizationReference, String businessTypeCode) {
        return jdbcTemplate.query("""
                SELECT id, organization_reference, campus_code, business_type_code, department_code, site_code,
                       diagnosis_group_code, doctor_id, priority, enabled, concurrency_version, created_at,
                       created_by_ref, updated_at, updated_by_ref
                FROM pis_v2.assignment_rule
                WHERE organization_reference = ? AND business_type_code = ? AND enabled = TRUE
                ORDER BY priority, id
                """, (rs, rowNum) -> new AssignmentRule(rs.getObject("id", UUID.class),
                rs.getString("organization_reference"), rs.getString("campus_code"),
                rs.getString("business_type_code"), rs.getString("department_code"), rs.getString("site_code"),
                rs.getString("diagnosis_group_code"), rs.getString("doctor_id"), rs.getInt("priority"),
                rs.getBoolean("enabled"), rs.getLong("concurrency_version"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by_ref")), organizationReference, businessTypeCode);
    }

    public List<PublicPoolCase> findPublicPoolCases(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT c.id, c.case_no, bt.business_type_code
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND EXISTS (
                      SELECT 1 FROM pis_v2.slide s
                      WHERE s.case_id = c.id AND s.source_context_type = 'INITIAL'
                        AND s.required = TRUE AND s.completed_at IS NOT NULL AND s.deleted_at IS NULL
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM pis_v2.slide s
                      WHERE s.case_id = c.id AND s.source_context_type = 'INITIAL'
                        AND s.required = TRUE AND s.completed_at IS NULL AND s.deleted_at IS NULL
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM pis_v2.diagnosis d
                      JOIN pis_v2.responsibility_unit r ON r.diagnosis_id = d.id
                      WHERE d.case_id = c.id AND d.organization_reference = ? AND r.role_code = 'INITIAL'
                  )
                ORDER BY c.created_at, c.id
                """, (rs, rowNum) -> new PublicPoolCase(rs.getObject("id", UUID.class), rs.getString("case_no"),
                rs.getString("business_type_code")), organizationReference, organizationReference);
    }

    public void insertAssignmentRule(AssignmentRule rule) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.assignment_rule
                    (id, organization_reference, campus_code, business_type_code, department_code, site_code,
                     diagnosis_group_code, doctor_id, priority, enabled, concurrency_version, created_at,
                     created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rule.id(), rule.organizationReference(), rule.campus(), rule.businessTypeCode(),
                rule.department(), rule.site(), rule.diagnosisGroup(), rule.doctorId(), rule.priority(), rule.enabled(),
                rule.version(), Timestamp.from(rule.createdAt()), rule.createdBy(), Timestamp.from(rule.updatedAt()),
                rule.updatedBy());
    }

    public record IdempotencyResult(String payloadDigest, String resultKindCode, UUID resultEntityId) { }

    public record PublicPoolCase(UUID caseId, String pathologyNo, String businessTypeCode) { }

    private Diagnosis toDiagnosis(java.sql.ResultSet rs) throws SQLException {
        return Diagnosis.persisted(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                DiagnosisContextType.valueOf(rs.getString("context_type")), rs.getObject("context_id", UUID.class),
                rs.getObject("template_version_id", UUID.class), rs.getString("structured_data"),
                rs.getString("microscopic_description"), rs.getString("diagnosis_text"), rs.getString("comment_text"),
                rs.getLong("concurrency_version"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by_ref"));
    }

    private DiagnosisTemplate toTemplate(java.sql.ResultSet rs) throws SQLException {
        return DiagnosisTemplate.persisted(rs.getObject("id", UUID.class), rs.getString("template_code"),
                rs.getString("template_name"), rs.getObject("business_type_id", UUID.class), rs.getString("scope_code"),
                rs.getBoolean("enabled"), rs.getLong("concurrency_version"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by_ref"));
    }

    private DiagnosisTemplateVersion toTemplateVersion(java.sql.ResultSet rs) throws SQLException {
        return DiagnosisTemplateVersion.persisted(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                rs.getInt("version_no"), rs.getString("schema_definition"), rs.getString("status_code"),
                instant(rs, "published_at"), rs.getString("published_by_ref"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"), rs.getLong("concurrency_version"));
    }

    private ResponsibilityUnit toResponsibility(java.sql.ResultSet rs) throws SQLException {
        return ResponsibilityUnit.persisted(rs.getObject("id", UUID.class), rs.getObject("diagnosis_id", UUID.class),
                ResponsibilityRole.valueOf(rs.getString("role_code")), rs.getString("doctor_id"),
                rs.getInt("sequence_no"), rs.getTimestamp("accepted_at").toInstant(), instant(rs, "completed_at"),
                instant(rs, "ended_at"), rs.getString("end_reason"),
                AssignmentSource.valueOf(rs.getString("assignment_source_code")), rs.getString("assignment_reason"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by_ref"),
                rs.getLong("concurrency_version"));
    }

    private Object json(String value) {
        if (!postgres) { return value; }
        return new SqlParameterValue(Types.OTHER, value);
    }

    private static Timestamp nullableTimestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    private static Instant instant(java.sql.ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}

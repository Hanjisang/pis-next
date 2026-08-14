package com.hanjisang.pis.v2.report.infrastructure;

import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.report.domain.Report;
import com.hanjisang.pis.v2.report.domain.ReportNature;
import com.hanjisang.pis.v2.report.domain.ReportStatus;
import com.hanjisang.pis.v2.report.domain.ReportTemplate;
import com.hanjisang.pis.v2.report.domain.ReportTemplateVersion;

@Repository
public class JdbcV2ReportRepository {

    private final JdbcTemplate jdbc;
    private final boolean postgres;

    public JdbcV2ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        Boolean isPostgres = jdbc.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres"));
        this.postgres = Boolean.TRUE.equals(isPostgres);
    }

    public Optional<ReportTemplateVersion> findPublishedTemplateForBusinessType(UUID businessTypeId,
            String organizationReference) {
        return jdbc.query("""
                SELECT v.id, v.template_id, v.version_no, CAST(v.definition AS VARCHAR) AS definition,
                       v.status_code, v.published_at, v.published_by_ref, v.created_at, v.created_by_ref,
                       v.concurrency_version
                FROM pis_v2.report_template_version v
                JOIN pis_v2.report_template t ON t.id = v.template_id
                WHERE t.business_type_id = ? AND t.organization_reference = ? AND t.enabled = TRUE
                  AND v.status_code = 'PUBLISHED'
                ORDER BY v.version_no DESC
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(toTemplateVersion(rs)) : Optional.empty(), businessTypeId,
                organizationReference);
    }

    public Optional<ReportTemplateVersion> findTemplateVersion(UUID versionId, String organizationReference) {
        return jdbc.query("""
                SELECT v.id, v.template_id, v.version_no, CAST(v.definition AS VARCHAR) AS definition,
                       v.status_code, v.published_at, v.published_by_ref, v.created_at, v.created_by_ref,
                       v.concurrency_version
                FROM pis_v2.report_template_version v
                JOIN pis_v2.report_template t ON t.id = v.template_id
                WHERE v.id = ? AND t.organization_reference = ? AND t.enabled = TRUE
                """, rs -> rs.next() ? Optional.of(toTemplateVersion(rs)) : Optional.empty(), versionId,
                organizationReference);
    }

    public boolean templateVersionMatchesBusinessType(UUID versionId, UUID businessTypeId,
            String organizationReference) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pis_v2.report_template_version v
                JOIN pis_v2.report_template t ON t.id = v.template_id
                WHERE v.id = ? AND t.business_type_id = ? AND t.organization_reference = ? AND t.enabled = TRUE
                """, Integer.class, versionId, businessTypeId, organizationReference);
        return count != null && count == 1;
    }

    public Optional<ReportTemplateVersion> findTemplateVersionForUpdate(UUID versionId,
            String organizationReference) {
        return jdbc.query("""
                SELECT v.id, v.template_id, v.version_no, CAST(v.definition AS VARCHAR) AS definition,
                       v.status_code, v.published_at, v.published_by_ref, v.created_at, v.created_by_ref,
                       v.concurrency_version
                FROM pis_v2.report_template_version v
                JOIN pis_v2.report_template t ON t.id = v.template_id
                WHERE v.id = ? AND t.organization_reference = ?
                FOR UPDATE
                """, rs -> rs.next() ? Optional.of(toTemplateVersion(rs)) : Optional.empty(), versionId,
                organizationReference);
    }

    public Optional<ReportTemplate> findTemplate(UUID templateId, String organizationReference) {
        return jdbc.query("""
                SELECT id, organization_reference, business_type_id, template_code, template_name, enabled,
                       configuration_version, created_at, created_by_ref, updated_at, updated_by_ref,
                       source_preset_code
                FROM pis_v2.report_template
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(new ReportTemplate(rs.getObject("id", UUID.class),
                        rs.getString("organization_reference"), rs.getObject("business_type_id", UUID.class),
                        rs.getString("template_code"), rs.getString("template_name"), rs.getBoolean("enabled"),
                        rs.getInt("configuration_version"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("created_by_ref"), rs.getTimestamp("updated_at").toInstant(),
                        rs.getString("updated_by_ref"), rs.getString("source_preset_code"))) : Optional.empty(),
                templateId, organizationReference);
    }

    public List<TemplateCatalogRow> findTemplateCatalog(String organizationReference) {
        return jdbc.query("""
                SELECT t.id AS template_id, t.template_code, t.template_name, t.business_type_id,
                       b.business_type_code, b.display_name AS business_type_name, t.enabled,
                       t.configuration_version, t.source_preset_code,
                       v.id AS version_id, v.version_no, CAST(v.definition AS VARCHAR) AS definition,
                       v.status_code, v.published_at, v.created_at
                FROM pis_v2.report_template t
                JOIN pis_v2.business_type b ON b.id = t.business_type_id
                LEFT JOIN pis_v2.report_template_version v ON v.template_id = t.id
                WHERE t.organization_reference = ?
                ORDER BY t.template_name, t.id, v.version_no DESC
                """, (rs, rowNum) -> new TemplateCatalogRow(rs.getObject("template_id", UUID.class),
                        rs.getString("template_code"), rs.getString("template_name"),
                        rs.getObject("business_type_id", UUID.class), rs.getString("business_type_code"),
                        rs.getString("business_type_name"), rs.getBoolean("enabled"),
                        rs.getInt("configuration_version"), rs.getString("source_preset_code"),
                        rs.getObject("version_id", UUID.class), (Integer) rs.getObject("version_no"),
                        rs.getString("definition"), rs.getString("status_code"), instant(rs, "published_at"),
                instant(rs, "created_at")), organizationReference);
    }

    public List<TemplateCatalogRow> findPublishedTemplateCatalog(UUID businessTypeId, String organizationReference) {
        return findTemplateCatalog(organizationReference).stream()
                .filter(item -> businessTypeId.equals(item.businessTypeId()))
                .filter(item -> item.enabled() && "PUBLISHED".equals(item.status()))
                .toList();
    }

    public List<TemplatePreset> findTemplatePresets() {
        return jdbc.query("""
                SELECT preset_code, preset_name, tumor_site_code, CAST(definition AS VARCHAR) AS definition,
                       preset_version
                FROM pis_v2.report_template_preset
                WHERE enabled = TRUE
                ORDER BY preset_name
                """, (rs, rowNum) -> new TemplatePreset(rs.getString("preset_code"), rs.getString("preset_name"),
                        rs.getString("tumor_site_code"), rs.getString("definition"), rs.getInt("preset_version")));
    }

    public Optional<TemplatePreset> findTemplatePreset(String presetCode) {
        return findTemplatePresets().stream().filter(item -> item.presetCode().equals(presetCode)).findFirst();
    }

    public int nextTemplateVersion(UUID templateId) {
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) + 1 FROM pis_v2.report_template_version WHERE template_id = ?
                """, Integer.class, templateId);
        return next == null ? 1 : next;
    }

    public void insertTemplate(ReportTemplate template, Instant now, String actorRef) {
        jdbc.update("""
                INSERT INTO pis_v2.report_template
                    (id, organization_reference, business_type_id, template_code, template_name, enabled,
                     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref,
                     source_preset_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, template.id(), template.organizationReference(), template.businessTypeId(), template.code(),
                template.name(), template.enabled(), template.configurationVersion(), Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef, template.sourcePresetCode());
    }

    public void insertTemplateVersion(ReportTemplateVersion version) {
        jdbc.update("""
                INSERT INTO pis_v2.report_template_version
                    (id, template_id, version_no, definition, status_code, published_at, published_by_ref,
                     created_at, created_by_ref, concurrency_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, version.id(), version.templateId(), version.versionNo(), json(version.definition()), version.status(),
                timestamp(version.publishedAt()), version.publishedBy(), Timestamp.from(version.createdAt()),
                version.createdBy(), version.version());
    }

    public boolean publishTemplateVersion(UUID versionId, String organizationReference, Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.report_template_version v
                   SET status_code = 'PUBLISHED', published_at = ?, published_by_ref = ?
                 WHERE v.id = ? AND v.status_code = 'DRAFT'
                   AND EXISTS (SELECT 1 FROM pis_v2.report_template t
                               WHERE t.id = v.template_id AND t.organization_reference = ?)
                """, Timestamp.from(now), actorRef, versionId, organizationReference) == 1;
    }

    public boolean lockReport(UUID reportId, String organizationReference) {
        return jdbc.query("""
                SELECT id FROM pis_v2.report WHERE id = ? AND organization_reference = ? FOR UPDATE
                """, (ResultSetExtractor<Boolean>) rs -> rs.next(), reportId, organizationReference);
    }

    public int nextReportSerial(UUID caseId, ReportNature nature, String organizationReference) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.report
                WHERE case_id = ? AND organization_reference = ? AND report_nature_code = ?
                """, Integer.class, caseId, organizationReference, nature.name());
        return (count == null ? 0 : count) + 1;
    }

    public void insertReport(Report report) {
        jdbc.update("""
                INSERT INTO pis_v2.report
                    (id, report_no, organization_reference, case_id, diagnosis_id, template_version_id,
                     report_nature_code, prior_report_id, status_code, diagnosis_snapshot, responsibility_snapshot,
                     case_snapshot, material_snapshot, technical_result_snapshot, supplemental_content,
                     rendered_content, rendered_content_hash, pdf_file_reference, pdf_content_hash, signed_by_ref,
                     signed_at, withdrawn_by_ref, withdrawn_at, withdrawal_reason, concurrency_version,
                     created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, report.id(), report.reportNo(), report.organizationReference(), report.caseId(), report.diagnosisId(),
                report.templateVersionId(), report.nature().name(), report.priorReportId(), report.status().name(),
                json(report.diagnosisSnapshot()), json(report.responsibilitySnapshot()), json(report.caseSnapshot()),
                json(report.materialSnapshot()), json(report.technicalResultSnapshot()), report.supplementalContent(),
                report.renderedContent(), report.renderedContentHash(), report.pdfFileReference(), report.pdfContentHash(),
                report.signedBy(), Timestamp.from(report.signedAt()), report.withdrawnBy(), timestamp(report.withdrawnAt()),
                report.withdrawalReason(), report.version(), Timestamp.from(report.createdAt()), report.createdBy());
    }

    public void insertPdf(UUID reportId, String fileReference, byte[] content, String contentHash, Instant now,
            String actorRef) {
        jdbc.update("""
                INSERT INTO pis_v2.report_pdf_output
                    (id, report_id, file_reference, content, content_hash, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reportId, fileReference, content, contentHash, Timestamp.from(now), actorRef);
    }

    public Optional<Report> findReport(UUID reportId, String organizationReference) {
        return jdbc.query(reportSelect() + " WHERE r.id = ? AND r.organization_reference = ?",
                rs -> rs.next() ? Optional.of(toReport(rs)) : Optional.empty(), reportId, organizationReference);
    }

    public List<Report> findReportsByCase(UUID caseId, String organizationReference) {
        return jdbc.query(reportSelect() + " WHERE r.case_id = ? AND r.organization_reference = ?"
                + " ORDER BY r.signed_at DESC, r.id DESC", (rs, rowNum) -> toReport(rs), caseId,
                organizationReference);
    }

    public List<Report> findReportsByDiagnosis(UUID diagnosisId, String organizationReference) {
        return jdbc.query(reportSelect() + " WHERE r.diagnosis_id = ? AND r.organization_reference = ?"
                + " ORDER BY r.signed_at DESC, r.id DESC", (rs, rowNum) -> toReport(rs), diagnosisId,
                organizationReference);
    }

    public Optional<Report> findEffectiveOriginal(UUID diagnosisId, String organizationReference) {
        return jdbc.query(reportSelect() + " WHERE r.diagnosis_id = ? AND r.organization_reference = ?"
                + " AND r.report_nature_code = 'ORIGINAL' AND r.status_code = 'EFFECTIVE'"
                + " ORDER BY r.signed_at DESC LIMIT 1", rs -> rs.next() ? Optional.of(toReport(rs)) : Optional.empty(),
                diagnosisId, organizationReference);
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String key) {
        return jdbc.query("""
                SELECT payload_digest, result_report_id FROM pis_v2.report_command_idempotency
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getString(1),
                rs.getObject(2, UUID.class))) : Optional.empty(), operationCode, key);
    }

    public boolean insertIdempotency(String operationCode, String key, String digest, UUID reportId,
            Instant now, String actorRef) {
        return jdbc.update("""
                INSERT INTO pis_v2.report_command_idempotency
                    (id, operation_code, idempotency_key, payload_digest, result_report_id, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operationCode, key, digest, reportId, Timestamp.from(now), actorRef) == 1;
    }

    public boolean withdraw(UUID reportId, String organizationReference, String actorRef, String reason, Instant now) {
        return jdbc.update("""
                UPDATE pis_v2.report
                   SET status_code = 'WITHDRAWN', withdrawn_by_ref = ?, withdrawn_at = ?, withdrawal_reason = ?,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND organization_reference = ? AND status_code = 'EFFECTIVE'
                """, actorRef, Timestamp.from(now), reason, reportId, organizationReference) == 1;
    }

    public byte[] pdf(UUID reportId, String organizationReference) {
        return jdbc.queryForObject("""
                SELECT p.content FROM pis_v2.report_pdf_output p
                JOIN pis_v2.report r ON r.id = p.report_id
                WHERE p.report_id = ? AND r.organization_reference = ?
                """, byte[].class, reportId, organizationReference);
    }

    private String reportSelect() {
        return """
                SELECT r.id, r.report_no, r.organization_reference, r.case_id, r.diagnosis_id,
                       r.template_version_id, r.report_nature_code, r.prior_report_id, r.status_code,
                       CAST(r.diagnosis_snapshot AS VARCHAR) AS diagnosis_snapshot,
                       CAST(r.responsibility_snapshot AS VARCHAR) AS responsibility_snapshot,
                       CAST(r.case_snapshot AS VARCHAR) AS case_snapshot,
                       CAST(r.material_snapshot AS VARCHAR) AS material_snapshot,
                       CAST(r.technical_result_snapshot AS VARCHAR) AS technical_result_snapshot,
                       r.supplemental_content, r.rendered_content, r.rendered_content_hash,
                       r.pdf_file_reference, r.pdf_content_hash, r.signed_by_ref, r.signed_at,
                       r.withdrawn_by_ref, r.withdrawn_at, r.withdrawal_reason, r.concurrency_version,
                       r.created_at, r.created_by_ref
                FROM pis_v2.report r
                """;
    }

    private ReportTemplateVersion toTemplateVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReportTemplateVersion(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                rs.getInt("version_no"), rs.getString("definition"), rs.getString("status_code"),
                instant(rs, "published_at"), rs.getString("published_by_ref"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"), rs.getLong("concurrency_version"));
    }

    public record TemplateCatalogRow(UUID templateId, String templateCode, String templateName,
            UUID businessTypeId, String businessTypeCode, String businessTypeName, boolean enabled,
            int configurationVersion, String sourcePresetCode, UUID versionId, Integer versionNo, String definition,
            String status, Instant publishedAt, Instant createdAt) { }

    public record TemplatePreset(String presetCode, String presetName, String tumorSiteCode, String definition,
            int presetVersion) { }

    private Report toReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Report(rs.getObject("id", UUID.class), rs.getString("report_no"),
                rs.getString("organization_reference"), rs.getObject("case_id", UUID.class),
                rs.getObject("diagnosis_id", UUID.class), rs.getObject("template_version_id", UUID.class),
                ReportNature.valueOf(rs.getString("report_nature_code")), rs.getObject("prior_report_id", UUID.class),
                ReportStatus.valueOf(rs.getString("status_code")), rs.getString("diagnosis_snapshot"),
                rs.getString("responsibility_snapshot"), rs.getString("case_snapshot"),
                rs.getString("material_snapshot"), rs.getString("technical_result_snapshot"),
                rs.getString("supplemental_content"), rs.getString("rendered_content"),
                rs.getString("rendered_content_hash"), rs.getString("pdf_file_reference"),
                rs.getString("pdf_content_hash"), rs.getString("signed_by_ref"), rs.getTimestamp("signed_at").toInstant(),
                rs.getString("withdrawn_by_ref"), instant(rs, "withdrawn_at"), rs.getString("withdrawal_reason"),
                rs.getLong("concurrency_version"), rs.getTimestamp("created_at").toInstant(), rs.getString("created_by_ref"));
    }

    private Object json(String value) {
        return postgres ? new SqlParameterValue(Types.OTHER, value) : value;
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record IdempotencyResult(String payloadDigest, UUID reportId) { }
}

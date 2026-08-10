package com.hanjisang.pis.v2.configuration.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2ConfigurationRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2ConfigurationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Snapshot snapshot(String organizationReference) {
        return new Snapshot(
                jdbc.query("""
                        SELECT id, business_type_code, display_name, modality_code, active, configuration_version
                        FROM pis_v2.business_type ORDER BY business_type_code
                        """, (rs, rowNum) -> new BusinessTypeConfig(rs.getObject("id", UUID.class),
                        rs.getString("business_type_code"), rs.getString("display_name"),
                        rs.getString("modality_code"), rs.getBoolean("active"), rs.getInt("configuration_version"))),
                jdbc.query("""
                        SELECT m.id, m.application_item_code, m.default_specimen_kind_code, m.required,
                               m.sequence_no, m.active, m.configuration_version, bt.business_type_code,
                               bt.display_name
                        FROM pis_v2.application_item_mapping m JOIN pis_v2.business_type bt ON bt.id = m.business_type_id
                        ORDER BY m.sequence_no, m.application_item_code
                        """, (rs, rowNum) -> new ApplicationMappingConfig(rs.getObject("id", UUID.class),
                        rs.getString("application_item_code"), rs.getString("default_specimen_kind_code"),
                        rs.getBoolean("required"), rs.getInt("sequence_no"), rs.getBoolean("active"),
                        rs.getInt("configuration_version"), rs.getString("business_type_code"),
                        rs.getString("display_name"))),
                jdbc.query("""
                        SELECT r.id, r.number_kind_code, r.prefix, r.scope_code, r.padding_width,
                               r.next_serial, r.active, r.configuration_version, bt.business_type_code, bt.display_name
                        FROM pis_v2.pathology_number_rule r JOIN pis_v2.business_type bt ON bt.id = r.business_type_id
                        WHERE r.organization_reference = ? ORDER BY bt.business_type_code, r.number_kind_code
                        """, (rs, rowNum) -> new PathologyNumberRuleConfig(rs.getObject("id", UUID.class),
                        rs.getString("number_kind_code"), rs.getString("prefix"), rs.getString("scope_code"),
                        rs.getInt("padding_width"), rs.getLong("next_serial"), rs.getBoolean("active"),
                        rs.getInt("configuration_version"), rs.getString("business_type_code"),
                        rs.getString("display_name")), organizationReference),
                jdbc.query("""
                        SELECT p.id, p.project_code, p.project_name, p.enabled, p.configuration_version,
                               bt.business_type_code, bt.display_name, p.required_before_sign_out_default
                        FROM pis_v2.technical_project p LEFT JOIN pis_v2.business_type bt ON bt.id = p.business_type_id
                        WHERE p.organization_reference = ? ORDER BY p.project_code
                        """, (rs, rowNum) -> new TechnicalProjectConfig(rs.getObject("id", UUID.class),
                        rs.getString("project_code"), rs.getString("project_name"), rs.getBoolean("enabled"),
                        rs.getInt("configuration_version"), rs.getString("business_type_code"),
                        rs.getString("display_name"), rs.getBoolean("required_before_sign_out_default")),
                        organizationReference),
                jdbc.query("""
                        SELECT t.id, t.template_code, t.template_name, t.enabled, t.concurrency_version,
                               bt.business_type_code, bt.display_name,
                               (SELECT COUNT(*) FROM pis_v2.diagnosis_template_version v WHERE v.template_id = t.id) AS version_count
                        FROM pis_v2.diagnosis_template t LEFT JOIN pis_v2.business_type bt ON bt.id = t.business_type_id
                        WHERE t.organization_reference = ? ORDER BY t.template_code
                        """, (rs, rowNum) -> new DiagnosisTemplateConfig(rs.getObject("id", UUID.class),
                        rs.getString("template_code"), rs.getString("template_name"), rs.getBoolean("enabled"),
                        rs.getLong("concurrency_version"), rs.getString("business_type_code"),
                        rs.getString("display_name"), rs.getInt("version_count")), organizationReference),
                jdbc.query("""
                        SELECT t.id, t.template_code, t.template_name, t.enabled, t.configuration_version,
                               bt.business_type_code, bt.display_name,
                               (SELECT COUNT(*) FROM pis_v2.report_template_version v WHERE v.template_id = t.id) AS version_count
                        FROM pis_v2.report_template t LEFT JOIN pis_v2.business_type bt ON bt.id = t.business_type_id
                        WHERE t.organization_reference = ? ORDER BY t.template_code
                        """, (rs, rowNum) -> new ReportTemplateConfig(rs.getObject("id", UUID.class),
                        rs.getString("template_code"), rs.getString("template_name"), rs.getBoolean("enabled"),
                        rs.getInt("configuration_version"), rs.getString("business_type_code"),
                        rs.getString("display_name"), rs.getInt("version_count")), organizationReference));
    }

    public boolean updateBusinessType(UUID id, String displayName, boolean enabled, Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.business_type SET display_name = ?, active = ?, configuration_version = configuration_version + 1,
                       created_by_ref = COALESCE(created_by_ref, ?)
                 WHERE id = ?
                """, displayName, enabled, actorRef, id) == 1;
    }

    public boolean updateMapping(UUID id, String specimenKind, boolean required, int sequence, boolean active) {
        return jdbc.update("""
                UPDATE pis_v2.application_item_mapping
                   SET default_specimen_kind_code = ?, required = ?, sequence_no = ?, active = ?,
                       configuration_version = configuration_version + 1
                 WHERE id = ?
                """, specimenKind, required, sequence, active, id) == 1;
    }

    public boolean updateNumberRule(UUID id, String prefix, int paddingWidth, boolean active) {
        return jdbc.update("""
                UPDATE pis_v2.pathology_number_rule SET prefix = ?, padding_width = ?, active = ?,
                       configuration_version = configuration_version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, prefix, paddingWidth, active, id) == 1;
    }

    public boolean updateTechnicalProject(UUID id, String projectName, boolean enabled,
            boolean requiredBeforeSignOut, Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.technical_project SET project_name = ?, enabled = ?,
                       required_before_sign_out_default = ?, configuration_version = configuration_version + 1,
                       updated_at = ?, updated_by_ref = ?
                 WHERE id = ?
                """, projectName, enabled, requiredBeforeSignOut, Timestamp.from(now), actorRef, id) == 1;
    }

    public boolean updateDiagnosisTemplate(UUID id, String templateName, boolean enabled,
            Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.diagnosis_template SET template_name = ?, enabled = ?,
                       concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ?
                """, templateName, enabled, Timestamp.from(now), actorRef, id) == 1;
    }

    public boolean updateReportTemplate(UUID id, String templateName, boolean enabled,
            Instant now, String actorRef) {
        return jdbc.update("""
                UPDATE pis_v2.report_template SET template_name = ?, enabled = ?,
                       configuration_version = configuration_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ?
                """, templateName, enabled, Timestamp.from(now), actorRef, id) == 1;
    }

    public record Snapshot(List<BusinessTypeConfig> businessTypes, List<ApplicationMappingConfig> applicationItemMappings,
            List<PathologyNumberRuleConfig> pathologyNumberRules, List<TechnicalProjectConfig> technicalProjects,
            List<DiagnosisTemplateConfig> diagnosisTemplates, List<ReportTemplateConfig> reportTemplates) { }
    public record BusinessTypeConfig(UUID id, String code, String displayName, String modalityCode, boolean enabled,
            int configurationVersion) { }
    public record ApplicationMappingConfig(UUID id, String applicationItemCode, String defaultSpecimenKindCode,
            boolean required, int sequenceNo, boolean active, int configurationVersion, String businessTypeCode,
            String businessTypeName) { }
    public record PathologyNumberRuleConfig(UUID id, String numberKindCode, String prefix, String scopeCode,
            int paddingWidth, long nextSerial, boolean active, int configurationVersion, String businessTypeCode,
            String businessTypeName) { }
    public record TechnicalProjectConfig(UUID id, String projectCode, String projectName, boolean enabled,
            int configurationVersion, String businessTypeCode, String businessTypeName,
            boolean requiredBeforeSignOutDefault) { }
    public record DiagnosisTemplateConfig(UUID id, String templateCode, String templateName, boolean enabled,
            long concurrencyVersion, String businessTypeCode, String businessTypeName, int versionCount) { }
    public record ReportTemplateConfig(UUID id, String templateCode, String templateName, boolean enabled,
            int configurationVersion, String businessTypeCode, String businessTypeName, int versionCount) { }
}

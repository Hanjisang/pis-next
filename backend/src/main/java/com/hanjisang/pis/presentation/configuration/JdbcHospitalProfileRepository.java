package com.hanjisang.pis.presentation.configuration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.BusinessTypeConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.CampusConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.DepartmentConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.DeviceConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.IntegrationConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.LabelTemplateConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.PathologyNumberConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.PrinterMappingConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.PrintStrategyConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.ReportConfiguration;
import com.hanjisang.pis.presentation.configuration.HospitalProfileSnapshot.WorkflowConfiguration;

@Repository
public class JdbcHospitalProfileRepository {

    private final JdbcTemplate jdbc;

    public JdbcHospitalProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<HospitalProfileSnapshot> findByCode(String profileCode) {
        ProfileRow profile = jdbc.query("""
                SELECT id, profile_code, display_name, legal_name, timezone_id, locale_code,
                       enabled, configuration_version
                FROM pis_v2.hospital_profile
                WHERE profile_code = ?
                """, rs -> rs.next() ? new ProfileRow(rs.getObject("id", UUID.class),
                rs.getString("profile_code"), rs.getString("display_name"), rs.getString("legal_name"),
                rs.getString("timezone_id"), rs.getString("locale_code"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")) : null, profileCode);
        if (profile == null) return Optional.empty();
        UUID profileId = profile.id();
        return Optional.of(new HospitalProfileSnapshot(profile.id(), profile.profileCode(), profile.displayName(),
                profile.legalName(), profile.timezoneId(), profile.localeCode(), profile.enabled(),
                profile.configurationVersion(), campuses(profileId), departments(profileId), businessTypes(profileId),
                workflows(profileId), numberRules(profile.profileCode()), labelTemplates(profileId),
                printerMappings(profileId), printStrategies(profileId), reports(profileId), devices(profileId),
                integrations(profileId)));
    }

    private List<CampusConfiguration> campuses(UUID profileId) {
        return jdbc.query("""
                SELECT campus_code, campus_name, enabled, configuration_version
                FROM pis_v2.hospital_campus WHERE hospital_profile_id = ? ORDER BY campus_code
                """, (rs, rowNum) -> new CampusConfiguration(rs.getString("campus_code"),
                rs.getString("campus_name"), rs.getBoolean("enabled"), rs.getInt("configuration_version")),
                profileId);
    }

    private List<DepartmentConfiguration> departments(UUID profileId) {
        return jdbc.query("""
                SELECT c.campus_code, d.department_code, d.department_name, d.department_type_code,
                       d.enabled, d.configuration_version
                FROM pis_v2.hospital_department d
                LEFT JOIN pis_v2.hospital_campus c ON c.id = d.campus_id
                WHERE d.hospital_profile_id = ? ORDER BY d.department_code
                """, (rs, rowNum) -> new DepartmentConfiguration(rs.getString("campus_code"),
                rs.getString("department_code"), rs.getString("department_name"),
                rs.getString("department_type_code"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<BusinessTypeConfiguration> businessTypes(UUID profileId) {
        return jdbc.query("""
                SELECT canonical_business_type_code, core_business_type_code, enabled, configuration_version
                FROM pis_v2.hospital_business_type_configuration
                WHERE hospital_profile_id = ? ORDER BY canonical_business_type_code
                """, (rs, rowNum) -> new BusinessTypeConfiguration(rs.getString("canonical_business_type_code"),
                rs.getString("core_business_type_code"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<WorkflowConfiguration> workflows(UUID profileId) {
        return jdbc.query("""
                SELECT canonical_business_type_code, require_review, require_audit, allow_direct_slide,
                       enabled, configuration_version
                FROM pis_v2.hospital_workflow_configuration
                WHERE hospital_profile_id = ? ORDER BY canonical_business_type_code
                """, (rs, rowNum) -> new WorkflowConfiguration(rs.getString("canonical_business_type_code"),
                rs.getBoolean("require_review"), rs.getBoolean("require_audit"),
                rs.getBoolean("allow_direct_slide"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<PathologyNumberConfiguration> numberRules(String profileCode) {
        return jdbc.query("""
                SELECT cfg.canonical_business_type_code, nr.number_kind_code, nr.prefix, nr.scope_code,
                       nr.padding_width, nr.active, nr.configuration_version
                FROM pis_v2.pathology_number_rule nr
                JOIN pis_v2.business_type bt ON bt.id = nr.business_type_id
                JOIN pis_v2.hospital_profile hp ON hp.profile_code = nr.organization_reference
                JOIN pis_v2.hospital_business_type_configuration cfg
                  ON cfg.hospital_profile_id = hp.id AND cfg.core_business_type_code = bt.business_type_code
                WHERE nr.organization_reference = ?
                ORDER BY cfg.canonical_business_type_code, nr.number_kind_code
                """, (rs, rowNum) -> new PathologyNumberConfiguration(
                rs.getString("canonical_business_type_code"), rs.getString("number_kind_code"),
                rs.getString("prefix"), rs.getString("scope_code"), rs.getInt("padding_width"),
                rs.getBoolean("active"), rs.getInt("configuration_version")), profileCode);
    }

    private List<LabelTemplateConfiguration> labelTemplates(UUID profileId) {
        return jdbc.query("""
                SELECT template_code, template_name, entity_kind_code, renderer_code, content_template,
                       enabled, configuration_version
                FROM pis_v2.label_template WHERE hospital_profile_id = ? ORDER BY template_code
                """, (rs, rowNum) -> new LabelTemplateConfiguration(rs.getString("template_code"),
                rs.getString("template_name"), rs.getString("entity_kind_code"), rs.getString("renderer_code"),
                rs.getString("content_template"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<PrinterMappingConfiguration> printerMappings(UUID profileId) {
        return jdbc.query("""
                SELECT c.campus_code, d.department_code, pm.logical_printer_code, pm.adapter_code,
                       pm.endpoint_reference, pm.enabled, pm.configuration_version
                FROM pis_v2.printer_mapping pm
                LEFT JOIN pis_v2.hospital_campus c ON c.id = pm.campus_id
                LEFT JOIN pis_v2.hospital_department d ON d.id = pm.department_id
                WHERE pm.hospital_profile_id = ? ORDER BY pm.logical_printer_code
                """, (rs, rowNum) -> new PrinterMappingConfiguration(rs.getString("campus_code"),
                rs.getString("department_code"), rs.getString("logical_printer_code"),
                rs.getString("adapter_code"), rs.getString("endpoint_reference"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<PrintStrategyConfiguration> printStrategies(UUID profileId) {
        return jdbc.query("""
                SELECT entity_kind_code, trigger_code, label_template_code, logical_printer_code,
                       copies, retry_limit, enabled, configuration_version
                FROM pis_v2.print_strategy WHERE hospital_profile_id = ?
                ORDER BY entity_kind_code, trigger_code
                """, (rs, rowNum) -> new PrintStrategyConfiguration(rs.getString("entity_kind_code"),
                rs.getString("trigger_code"), rs.getString("label_template_code"),
                rs.getString("logical_printer_code"), rs.getInt("copies"), rs.getInt("retry_limit"),
                rs.getBoolean("enabled"), rs.getInt("configuration_version")), profileId);
    }

    private List<ReportConfiguration> reports(UUID profileId) {
        return jdbc.query("""
                SELECT canonical_business_type_code, default_report_template_code, signature_display_mode,
                       hospital_logo_reference, footer_text, enabled, configuration_version
                FROM pis_v2.hospital_report_configuration WHERE hospital_profile_id = ?
                ORDER BY canonical_business_type_code
                """, (rs, rowNum) -> new ReportConfiguration(rs.getString("canonical_business_type_code"),
                rs.getString("default_report_template_code"), rs.getString("signature_display_mode"),
                rs.getString("hospital_logo_reference"), rs.getString("footer_text"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<DeviceConfiguration> devices(UUID profileId) {
        return jdbc.query("""
                SELECT device_code, device_type_code, adapter_code, endpoint_reference, settings::text,
                       enabled, configuration_version
                FROM pis_v2.device_configuration WHERE hospital_profile_id = ? ORDER BY device_code
                """, (rs, rowNum) -> new DeviceConfiguration(rs.getString("device_code"),
                rs.getString("device_type_code"), rs.getString("adapter_code"),
                rs.getString("endpoint_reference"), rs.getString("settings"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private List<IntegrationConfiguration> integrations(UUID profileId) {
        return jdbc.query("""
                SELECT system_code, system_type_code, adapter_code, endpoint_reference, settings::text,
                       enabled, configuration_version
                FROM pis_v2.integration_configuration WHERE hospital_profile_id = ? ORDER BY system_code
                """, (rs, rowNum) -> new IntegrationConfiguration(rs.getString("system_code"),
                rs.getString("system_type_code"), rs.getString("adapter_code"),
                rs.getString("endpoint_reference"), rs.getString("settings"), rs.getBoolean("enabled"),
                rs.getInt("configuration_version")), profileId);
    }

    private record ProfileRow(UUID id, String profileCode, String displayName, String legalName,
            String timezoneId, String localeCode, boolean enabled, int configurationVersion) { }
}

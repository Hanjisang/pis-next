package com.hanjisang.pis.presentation.configuration;

import java.util.List;
import java.util.UUID;

public record HospitalProfileSnapshot(
        UUID id,
        String profileCode,
        String displayName,
        String legalName,
        String timezoneId,
        String localeCode,
        boolean enabled,
        int configurationVersion,
        List<CampusConfiguration> campuses,
        List<DepartmentConfiguration> departments,
        List<BusinessTypeConfiguration> businessTypes,
        List<WorkflowConfiguration> workflows,
        List<PathologyNumberConfiguration> pathologyNumberRules,
        List<LabelTemplateConfiguration> labelTemplates,
        List<PrinterMappingConfiguration> printerMappings,
        List<PrintStrategyConfiguration> printStrategies,
        List<ReportConfiguration> reports,
        List<DeviceConfiguration> devices,
        List<IntegrationConfiguration> integrations) {

    public HospitalProfileSnapshot {
        campuses = List.copyOf(campuses);
        departments = List.copyOf(departments);
        businessTypes = List.copyOf(businessTypes);
        workflows = List.copyOf(workflows);
        pathologyNumberRules = List.copyOf(pathologyNumberRules);
        labelTemplates = List.copyOf(labelTemplates);
        printerMappings = List.copyOf(printerMappings);
        printStrategies = List.copyOf(printStrategies);
        reports = List.copyOf(reports);
        devices = List.copyOf(devices);
        integrations = List.copyOf(integrations);
    }

    public record CampusConfiguration(String campusCode, String campusName, boolean enabled,
            int configurationVersion) { }

    public record DepartmentConfiguration(String campusCode, String departmentCode, String departmentName,
            String departmentTypeCode, boolean enabled, int configurationVersion) { }

    public record BusinessTypeConfiguration(String canonicalBusinessTypeCode, String coreBusinessTypeCode,
            boolean enabled, int configurationVersion) { }

    public record WorkflowConfiguration(String canonicalBusinessTypeCode, boolean requireReview,
            boolean requireAudit, boolean allowDirectSlide, boolean enabled, int configurationVersion) { }

    public record PathologyNumberConfiguration(String canonicalBusinessTypeCode, String numberKindCode,
            String prefix, String scopeCode, int paddingWidth, boolean enabled, int configurationVersion) { }

    public record LabelTemplateConfiguration(String templateCode, String templateName, String entityKindCode,
            String rendererCode, String contentTemplate, boolean enabled, int configurationVersion) { }

    public record PrinterMappingConfiguration(String campusCode, String departmentCode, String logicalPrinterCode,
            String adapterCode, String endpointReference, boolean enabled, int configurationVersion) { }

    public record PrintStrategyConfiguration(String entityKindCode, String triggerCode, String labelTemplateCode,
            String logicalPrinterCode, int copies, int retryLimit, boolean enabled, int configurationVersion) { }

    public record ReportConfiguration(String canonicalBusinessTypeCode, String defaultReportTemplateCode,
            String signatureDisplayMode, String hospitalLogoReference, String footerText, boolean enabled,
            int configurationVersion) { }

    public record DeviceConfiguration(String deviceCode, String deviceTypeCode, String adapterCode,
            String endpointReference, String settings, boolean enabled, int configurationVersion) { }

    public record IntegrationConfiguration(String systemCode, String systemTypeCode, String adapterCode,
            String endpointReference, String settings, boolean enabled, int configurationVersion) { }
}

package com.hanjisang.pis.v2.technical.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TechnicalProject {

    private final UUID id;
    private final String organizationReference;
    private final UUID businessTypeId;
    private final String code;
    private final String name;
    private final String capabilityCode;
    private final String outputTypeCode;
    private final boolean enabled;
    private final Set<TechnicalTargetType> allowedTargetTypes;
    private final boolean producesSlide;
    private final boolean producesBlock;
    private final boolean producesStructuredResult;
    private final boolean requiresResult;
    private final String deviceTypeCode;
    private final boolean consumableRequired;
    private final String defaultSlideType;
    private final String parametersSchema;
    private final String resultSchema;
    private final String feeMapping;
    private final String displayConfiguration;
    private final boolean requiredBeforeSignOutDefault;
    private final int configurationVersion;

    private TechnicalProject(UUID id, String organizationReference, UUID businessTypeId, String code, String name,
            String capabilityCode, String outputTypeCode, boolean enabled,
            Set<TechnicalTargetType> allowedTargetTypes, boolean producesSlide,
            boolean producesBlock, boolean producesStructuredResult, boolean requiresResult,
            String deviceTypeCode, boolean consumableRequired, String defaultSlideType,
            String parametersSchema, String resultSchema, String feeMapping, String displayConfiguration,
            boolean requiredBeforeSignOutDefault, int configurationVersion) {
        this.id = Objects.requireNonNull(id, "技术项目内部ID不能为空");
        this.organizationReference = required(organizationReference, "组织范围不能为空");
        this.businessTypeId = Objects.requireNonNull(businessTypeId, "业务类型不能为空");
        this.code = required(code, "技术项目编码不能为空");
        this.name = required(name, "技术项目名称不能为空");
        this.capabilityCode = required(capabilityCode, "技术项目能力编码不能为空");
        this.outputTypeCode = required(outputTypeCode, "技术项目输出类型不能为空");
        this.enabled = enabled;
        this.allowedTargetTypes = allowedTargetTypes == null || allowedTargetTypes.isEmpty()
                ? EnumSet.noneOf(TechnicalTargetType.class) : EnumSet.copyOf(allowedTargetTypes);
        this.producesSlide = producesSlide;
        this.producesBlock = producesBlock;
        this.producesStructuredResult = producesStructuredResult;
        this.requiresResult = requiresResult;
        this.deviceTypeCode = optional(deviceTypeCode);
        this.consumableRequired = consumableRequired;
        if (!producesSlide && !producesBlock && !producesStructuredResult) {
            throw new IllegalArgumentException("技术项目至少必须声明一种输出能力");
        }
        this.defaultSlideType = optional(defaultSlideType);
        this.parametersSchema = optional(parametersSchema);
        this.resultSchema = optional(resultSchema);
        this.feeMapping = optional(feeMapping);
        this.displayConfiguration = optional(displayConfiguration);
        this.requiredBeforeSignOutDefault = requiredBeforeSignOutDefault;
        if (configurationVersion < 1) {
            throw new IllegalArgumentException("技术项目配置版本必须为正数");
        }
        this.configurationVersion = configurationVersion;
    }

    public static TechnicalProject create(UUID id, String organizationReference, UUID businessTypeId, String code,
            String name, String capabilityCode, String outputTypeCode, boolean enabled,
            String allowedTargetTypes, boolean producesSlide, boolean producesBlock,
            boolean producesStructuredResult, boolean requiresResult, String deviceTypeCode,
            boolean consumableRequired, String defaultSlideType, String parametersSchema, String resultSchema,
            String feeMapping, String displayConfiguration, boolean requiredBeforeSignOutDefault,
            int configurationVersion) {
        return new TechnicalProject(id, organizationReference, businessTypeId, code, name, capabilityCode,
                outputTypeCode, enabled, parseTargets(allowedTargetTypes), producesSlide, producesBlock,
                producesStructuredResult, requiresResult, deviceTypeCode, consumableRequired, defaultSlideType,
                parametersSchema, resultSchema, feeMapping, displayConfiguration,
                requiredBeforeSignOutDefault, configurationVersion);
    }

    public boolean supportsTarget(TechnicalTargetType targetType) {
        return allowedTargetTypes.contains(targetType);
    }

    public String allowedTargetTypesCode() {
        return allowedTargetTypes.stream().map(Enum::name).sorted().reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    public UUID id() { return id; }
    public String organizationReference() { return organizationReference; }
    public UUID businessTypeId() { return businessTypeId; }
    public String code() { return code; }
    public String name() { return name; }
    public String capabilityCode() { return capabilityCode; }
    public String outputTypeCode() { return outputTypeCode; }
    public boolean enabled() { return enabled; }
    public Set<TechnicalTargetType> allowedTargetTypes() { return Set.copyOf(allowedTargetTypes); }
    public boolean producesSlide() { return producesSlide; }
    public boolean producesBlock() { return producesBlock; }
    public boolean producesStructuredResult() { return producesStructuredResult; }
    public boolean requiresResult() { return requiresResult; }
    public String deviceTypeCode() { return deviceTypeCode; }
    public boolean consumableRequired() { return consumableRequired; }
    public String defaultSlideType() { return defaultSlideType; }
    public String parametersSchema() { return parametersSchema; }
    public String resultSchema() { return resultSchema; }
    public String feeMapping() { return feeMapping; }
    public String displayConfiguration() { return displayConfiguration; }
    public boolean requiredBeforeSignOutDefault() { return requiredBeforeSignOutDefault; }
    public int configurationVersion() { return configurationVersion; }

    private static Set<TechnicalTargetType> parseTargets(String value) {
        if (value == null || value.isBlank()) return EnumSet.noneOf(TechnicalTargetType.class);
        EnumSet<TechnicalTargetType> result = EnumSet.noneOf(TechnicalTargetType.class);
        Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank())
                .map(TechnicalTargetType::valueOf).forEach(result::add);
        return result;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

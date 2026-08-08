package com.hanjisang.pis.v2.registration.domain;

import java.util.Objects;
import java.util.UUID;

public final class ApplicationItemMapping {

    private final UUID id;
    private final String applicationItemCode;
    private final UUID businessTypeId;
    private final String businessTypeCode;
    private final String defaultSpecimenKindCode;
    private final boolean required;
    private final int sequenceNo;
    private final boolean active;

    private ApplicationItemMapping(UUID id, String applicationItemCode, UUID businessTypeId, String businessTypeCode,
            String defaultSpecimenKindCode, boolean required, int sequenceNo, boolean active) {
        this.id = Objects.requireNonNull(id, "申请项目映射内部ID不能为空");
        this.applicationItemCode = required(applicationItemCode, "申请项目编码不能为空");
        this.businessTypeId = Objects.requireNonNull(businessTypeId, "业务类型ID不能为空");
        this.businessTypeCode = required(businessTypeCode, "业务类型编码不能为空");
        this.defaultSpecimenKindCode = defaultSpecimenKindCode == null ? null : defaultSpecimenKindCode.trim();
        if (sequenceNo < 1) {
            throw new IllegalArgumentException("申请项目顺序必须为正数");
        }
        this.required = required;
        this.sequenceNo = sequenceNo;
        this.active = active;
    }

    public static ApplicationItemMapping map(UUID id, String applicationItemCode, UUID businessTypeId,
            String businessTypeCode, String defaultSpecimenKindCode, boolean required, int sequenceNo,
            boolean active) {
        return new ApplicationItemMapping(id, applicationItemCode, businessTypeId, businessTypeCode,
                defaultSpecimenKindCode, required, sequenceNo, active);
    }

    public boolean canRouteNewCase() { return active; }
    public UUID id() { return id; }
    public String applicationItemCode() { return applicationItemCode; }
    public UUID businessTypeId() { return businessTypeId; }
    public String businessTypeCode() { return businessTypeCode; }
    public String defaultSpecimenKindCode() { return defaultSpecimenKindCode; }
    public boolean required() { return required; }
    public int sequenceNo() { return sequenceNo; }
    public boolean active() { return active; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

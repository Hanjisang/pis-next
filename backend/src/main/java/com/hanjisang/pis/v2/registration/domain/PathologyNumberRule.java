package com.hanjisang.pis.v2.registration.domain;

import java.util.Locale;

public final class PathologyNumberRule {

    private final String organizationReference;
    private final String businessTypeCode;
    private final String numberKindCode;
    private final String prefix;
    private final String scopeCode;
    private final int paddingWidth;
    private final boolean active;

    private PathologyNumberRule(String organizationReference, String businessTypeCode, String numberKindCode,
            String prefix, String scopeCode, int paddingWidth, boolean active) {
        this.organizationReference = required(organizationReference, "编号机构范围不能为空");
        this.businessTypeCode = required(businessTypeCode, "编号业务类型不能为空");
        this.numberKindCode = required(numberKindCode, "编号对象类型不能为空");
        this.prefix = required(prefix, "编号前缀不能为空");
        this.scopeCode = required(scopeCode, "编号唯一范围不能为空");
        if (paddingWidth < 1 || paddingWidth > 12) {
            throw new IllegalArgumentException("编号补零宽度必须在1到12之间");
        }
        this.paddingWidth = paddingWidth;
        this.active = active;
    }

    public static PathologyNumberRule configure(String organizationReference, String businessTypeCode,
            String numberKindCode, String prefix, String scopeCode, int paddingWidth, boolean active) {
        return new PathologyNumberRule(organizationReference, businessTypeCode, numberKindCode, prefix, scopeCode,
                paddingWidth, active);
    }

    public String format(long serial) {
        if (!active) {
            throw new IllegalStateException("编号规则未生效");
        }
        if (serial < 1) {
            throw new IllegalArgumentException("编号序号必须为正数");
        }
        return prefix + String.format(Locale.ROOT, "%0" + paddingWidth + "d", serial);
    }

    public String organizationReference() { return organizationReference; }
    public String businessTypeCode() { return businessTypeCode; }
    public String numberKindCode() { return numberKindCode; }
    public String prefix() { return prefix; }
    public String scopeCode() { return scopeCode; }
    public int paddingWidth() { return paddingWidth; }
    public boolean active() { return active; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

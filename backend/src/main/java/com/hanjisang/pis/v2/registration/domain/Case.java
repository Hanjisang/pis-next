package com.hanjisang.pis.v2.registration.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Case {

    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELLED = "CANCELLED";

    private final UUID id;
    private final String caseNo;
    private final String sourceSystemCode;
    private final String externalApplicationId;
    private final String applicationItemCode;
    private final UUID businessTypeId;
    private final String businessTypeCode;
    private final String patientReference;
    private final String visitReference;
    private String lifecycleStateCode;
    private boolean numberBindingActive;
    private long concurrencyVersion;

    private Case(UUID id, String caseNo, String sourceSystemCode, String externalApplicationId,
            String applicationItemCode, UUID businessTypeId, String businessTypeCode, String patientReference,
            String visitReference, String lifecycleStateCode, boolean numberBindingActive, long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "病例内部ID不能为空");
        this.caseNo = required(caseNo, "病例病理号不能为空");
        this.sourceSystemCode = required(sourceSystemCode, "申请来源系统不能为空");
        this.externalApplicationId = required(externalApplicationId, "外部申请标识不能为空");
        this.applicationItemCode = required(applicationItemCode, "申请项目不能为空");
        this.businessTypeId = Objects.requireNonNull(businessTypeId, "业务类型ID不能为空");
        this.businessTypeCode = required(businessTypeCode, "业务类型编码不能为空");
        this.patientReference = required(patientReference, "患者上下文引用不能为空");
        this.visitReference = visitReference == null || visitReference.isBlank() ? null : visitReference.trim();
        if (!ACTIVE.equals(lifecycleStateCode) && !CANCELLED.equals(lifecycleStateCode)) {
            throw new IllegalArgumentException("病例生命周期只能是ACTIVE或CANCELLED");
        }
        if (CANCELLED.equals(lifecycleStateCode) && numberBindingActive) {
            throw new IllegalArgumentException("已取消病例不能保留有效病理号绑定");
        }
        if (concurrencyVersion < 0) {
            throw new IllegalArgumentException("病例并发版本不能为负数");
        }
        this.lifecycleStateCode = lifecycleStateCode;
        this.numberBindingActive = numberBindingActive;
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Case active(UUID id, String caseNo, String sourceSystemCode, String externalApplicationId,
            String applicationItemCode, UUID businessTypeId, String businessTypeCode, String patientReference,
            String visitReference) {
        return new Case(id, caseNo, sourceSystemCode, externalApplicationId, applicationItemCode, businessTypeId,
                businessTypeCode, patientReference, visitReference, ACTIVE, true, 0);
    }

    public static Case persisted(UUID id, String caseNo, String sourceSystemCode, String externalApplicationId,
            String applicationItemCode, UUID businessTypeId, String businessTypeCode, String patientReference,
            String visitReference, String lifecycleStateCode, boolean numberBindingActive, long concurrencyVersion) {
        return new Case(id, caseNo, sourceSystemCode, externalApplicationId, applicationItemCode, businessTypeId,
                businessTypeCode, patientReference, visitReference, lifecycleStateCode, numberBindingActive,
                concurrencyVersion);
    }

    public void cancel(String reason, Instant cancelledAt) {
        if (!ACTIVE.equals(lifecycleStateCode)) {
            throw new IllegalStateException("只有ACTIVE病例可以取消");
        }
        required(reason, "病例取消原因不能为空");
        Objects.requireNonNull(cancelledAt, "病例取消时间不能为空");
        lifecycleStateCode = CANCELLED;
        numberBindingActive = false;
        concurrencyVersion++;
    }

    public UUID id() { return id; }
    public String caseNo() { return caseNo; }
    public String sourceSystemCode() { return sourceSystemCode; }
    public String externalApplicationId() { return externalApplicationId; }
    public String applicationItemCode() { return applicationItemCode; }
    public UUID businessTypeId() { return businessTypeId; }
    public String businessTypeCode() { return businessTypeCode; }
    public String patientReference() { return patientReference; }
    public String visitReference() { return visitReference; }
    public String lifecycleStateCode() { return lifecycleStateCode; }
    public boolean numberBindingActive() { return numberBindingActive; }
    public long concurrencyVersion() { return concurrencyVersion; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

package com.hanjisang.pis.specimen.domain;

import java.util.UUID;

public final class Specimen {

    public static final String EXPECTED = "P08-SM-003-ST-01";
    public static final String WAITING_VERIFICATION = "P08-SM-003-ST-02";
    public static final String RECEIVED = "P08-SM-003-ST-03";
    public static final String ISOLATED = "P08-SM-003-ST-04";

    private final UUID id;
    private final UUID caseId;
    private final String specimenNo;
    private final String specimenKindCode;
    private final String collectionSite;
    private String lifecycleStateCode;
    private long concurrencyVersion;

    private Specimen(UUID id, UUID caseId, String specimenNo, String specimenKindCode, String collectionSite,
            String lifecycleStateCode, long concurrencyVersion) {
        this.id = id;
        this.caseId = caseId;
        this.specimenNo = specimenNo;
        this.specimenKindCode = specimenKindCode;
        this.collectionSite = collectionSite;
        this.lifecycleStateCode = lifecycleStateCode;
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Specimen expected(UUID id, UUID caseId, String specimenNo, String specimenKindCode,
            String collectionSite) {
        return new Specimen(id, caseId, specimenNo, specimenKindCode, collectionSite, EXPECTED, 0);
    }

    public static Specimen persisted(UUID id, UUID caseId, String specimenNo, String specimenKindCode,
            String collectionSite, String lifecycleStateCode, long concurrencyVersion) {
        return new Specimen(id, caseId, specimenNo, specimenKindCode, collectionSite, lifecycleStateCode,
                concurrencyVersion);
    }

    public void receive() {
        if (!EXPECTED.equals(lifecycleStateCode) && !WAITING_VERIFICATION.equals(lifecycleStateCode)) {
            throw new IllegalStateException("当前标本状态不允许接收");
        }
        lifecycleStateCode = RECEIVED;
        concurrencyVersion++;
    }

    public void isolate() {
        if (RECEIVED.equals(lifecycleStateCode) || WAITING_VERIFICATION.equals(lifecycleStateCode)) {
            lifecycleStateCode = ISOLATED;
            concurrencyVersion++;
            return;
        }
        throw new IllegalStateException("当前标本状态不允许隔离");
    }

    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String specimenNo() { return specimenNo; }
    public String specimenKindCode() { return specimenKindCode; }
    public String collectionSite() { return collectionSite; }
    public String lifecycleStateCode() { return lifecycleStateCode; }
    public long concurrencyVersion() { return concurrencyVersion; }
}

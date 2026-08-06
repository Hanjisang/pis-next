package com.hanjisang.pis.technical.domain;

import java.util.UUID;

public final class TissueBlock {

    public static final String PLANNED = "P08-SM-004-ST-01";
    public static final String GROSSING_RECORDED = "P08-SM-004-ST-02";
    public static final String VOIDED = "P08-SM-004-ST-04";

    private final UUID id;
    private final String stateCode;
    private final long version;

    private TissueBlock(UUID id, String stateCode, long version) {
        this.id = id;
        this.stateCode = stateCode;
        this.version = version;
    }

    public static TissueBlock planned(UUID id) { return new TissueBlock(id, PLANNED, 0); }

    public static TissueBlock persisted(UUID id, String stateCode, long version) {
        return new TissueBlock(id, stateCode, version);
    }

    public TissueBlock markGrossingRecorded() {
        if (!PLANNED.equals(stateCode)) throw new IllegalStateException("蜡块计划不可记录取材");
        return new TissueBlock(id, GROSSING_RECORDED, version + 1);
    }

    public TissueBlock voidPlan() {
        if (VOIDED.equals(stateCode)) throw new IllegalStateException("蜡块计划已作废");
        return new TissueBlock(id, VOIDED, version + 1);
    }

    public UUID id() { return id; }
    public String stateCode() { return stateCode; }
    public long version() { return version; }
}

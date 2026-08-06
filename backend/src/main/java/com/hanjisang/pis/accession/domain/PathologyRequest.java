package com.hanjisang.pis.accession.domain;

import java.time.Instant;
import java.util.UUID;

public final class PathologyRequest {

    public static final String WAITING_VERIFICATION = "P08-SM-001-ST-02";
    public static final String ESTABLISHED = "P08-SM-001-ST-03";

    private final UUID id;
    private final String applicationNo;
    private final String sourceSystemCode;
    private final String pathologyModalityCode;
    private String lifecycleStateCode;
    private long concurrencyVersion;
    private final Instant receivedAt;

    private PathologyRequest(UUID id, String applicationNo, String sourceSystemCode, String pathologyModalityCode,
            String lifecycleStateCode, long concurrencyVersion, Instant receivedAt) {
        this.id = id;
        this.applicationNo = applicationNo;
        this.sourceSystemCode = sourceSystemCode;
        this.pathologyModalityCode = pathologyModalityCode;
        this.lifecycleStateCode = lifecycleStateCode;
        this.concurrencyVersion = concurrencyVersion;
        this.receivedAt = receivedAt;
    }

    public static PathologyRequest received(UUID id, String applicationNo, String sourceSystemCode,
            String pathologyModalityCode, Instant receivedAt) {
        return new PathologyRequest(id, applicationNo, sourceSystemCode, pathologyModalityCode, WAITING_VERIFICATION,
                0, receivedAt);
    }

    public void accept() {
        if (!WAITING_VERIFICATION.equals(lifecycleStateCode)) {
            throw new IllegalStateException("只有待核对申请可以被接受");
        }
        lifecycleStateCode = ESTABLISHED;
        concurrencyVersion++;
    }

    public UUID id() { return id; }
    public String applicationNo() { return applicationNo; }
    public String sourceSystemCode() { return sourceSystemCode; }
    public String pathologyModalityCode() { return pathologyModalityCode; }
    public String lifecycleStateCode() { return lifecycleStateCode; }
    public long concurrencyVersion() { return concurrencyVersion; }
    public Instant receivedAt() { return receivedAt; }
}

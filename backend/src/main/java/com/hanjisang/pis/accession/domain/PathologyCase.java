package com.hanjisang.pis.accession.domain;

import java.time.Instant;
import java.util.UUID;

public final class PathologyCase {

    public static final String PENDING_EFFECTIVE = "P08-SM-002-ST-02";
    public static final String ESTABLISHED = "P08-SM-002-ST-03";

    private final UUID id;
    private final String caseNo;
    private final UUID requestId;
    private final UUID patientVisitSnapshotId;
    private final String modalityCode;
    private final Instant establishedAt;

    private PathologyCase(UUID id, String caseNo, UUID requestId, UUID patientVisitSnapshotId, String modalityCode,
            Instant establishedAt) {
        this.id = id;
        this.caseNo = caseNo;
        this.requestId = requestId;
        this.patientVisitSnapshotId = patientVisitSnapshotId;
        this.modalityCode = modalityCode;
        this.establishedAt = establishedAt;
    }

    public static PathologyCase establish(UUID id, String caseNo, UUID requestId, UUID snapshotId, String modalityCode,
            Instant establishedAt) {
        return new PathologyCase(id, caseNo, requestId, snapshotId, modalityCode, establishedAt);
    }

    public UUID id() { return id; }
    public String caseNo() { return caseNo; }
    public UUID requestId() { return requestId; }
    public UUID patientVisitSnapshotId() { return patientVisitSnapshotId; }
    public String modalityCode() { return modalityCode; }
    public Instant establishedAt() { return establishedAt; }
}

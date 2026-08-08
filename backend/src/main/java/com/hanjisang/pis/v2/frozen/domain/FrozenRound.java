package com.hanjisang.pis.v2.frozen.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class FrozenRound {

    public static final String OPEN = "OPEN";
    public static final String PRODUCTION_COMPLETE = "PRODUCTION_COMPLETE";
    public static final String SIGNED = "SIGNED";
    public static final String ENDED = "ENDED";

    private final UUID id;
    private final UUID caseId;
    private final int roundNo;
    private String status;
    private final Instant arrivalTime;
    private final Instant registeredAt;
    private Instant grossingStartTime;
    private Instant slideCompletedTime;
    private Instant diagnosisSignedTime;
    private Instant endedAt;
    private String endedBy;
    private long version;

    private FrozenRound(UUID id, UUID caseId, int roundNo, String status, Instant arrivalTime, Instant registeredAt,
            Instant grossingStartTime, Instant slideCompletedTime, Instant diagnosisSignedTime, Instant endedAt,
            String endedBy, long version) {
        this.id = Objects.requireNonNull(id, "冰冻轮次ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "冰冻轮次必须关联病例");
        if (roundNo < 1) throw new IllegalArgumentException("冰冻轮次编号必须为正数");
        this.roundNo = roundNo;
        if (!SetOfStatuses.contains(status)) throw new IllegalArgumentException("冰冻轮次状态不受支持");
        this.status = status;
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "冰冻到达时间不能为空");
        this.registeredAt = Objects.requireNonNull(registeredAt, "冰冻登记时间不能为空");
        this.grossingStartTime = grossingStartTime;
        this.slideCompletedTime = slideCompletedTime;
        this.diagnosisSignedTime = diagnosisSignedTime;
        this.endedAt = endedAt;
        this.endedBy = endedBy;
        if (version < 0) throw new IllegalArgumentException("冰冻轮次版本不能为负数");
        this.version = version;
    }

    private static final java.util.Set<String> SetOfStatuses = java.util.Set.of(OPEN, PRODUCTION_COMPLETE, SIGNED, ENDED);

    public static FrozenRound open(UUID id, UUID caseId, int roundNo, Instant arrivalTime, Instant registeredAt) {
        return new FrozenRound(id, caseId, roundNo, OPEN, arrivalTime, registeredAt, null, null, null, null, null, 0);
    }

    public static FrozenRound persisted(UUID id, UUID caseId, int roundNo, String status, Instant arrivalTime,
            Instant registeredAt, Instant grossingStartTime, Instant slideCompletedTime, Instant diagnosisSignedTime,
            Instant endedAt, String endedBy, long version) {
        return new FrozenRound(id, caseId, roundNo, status, arrivalTime, registeredAt, grossingStartTime,
                slideCompletedTime, diagnosisSignedTime, endedAt, endedBy, version);
    }

    public void markGrossingStarted(Instant at) {
        if (ENDED.equals(status) || SIGNED.equals(status)) throw new IllegalStateException("已签发或结束的冰冻轮次不能新增取材");
        grossingStartTime = Objects.requireNonNull(at, "取材开始时间不能为空");
        version++;
    }

    public void markProductionComplete(Instant at) {
        if (ENDED.equals(status) || SIGNED.equals(status)) return;
        slideCompletedTime = Objects.requireNonNull(at, "制片完成时间不能为空");
        status = PRODUCTION_COMPLETE;
        version++;
    }

    public void markSigned(Instant at) {
        if (ENDED.equals(status)) throw new IllegalStateException("已结束冰冻轮次不能签发");
        diagnosisSignedTime = Objects.requireNonNull(at, "诊断签发时间不能为空");
        status = SIGNED;
        version++;
    }

    public void end(Instant at, String actorRef) {
        if (ENDED.equals(status)) return;
        endedAt = Objects.requireNonNull(at, "冰冻结束时间不能为空");
        endedBy = required(actorRef, "冰冻结束人不能为空");
        status = ENDED;
        version++;
    }

    public boolean acceptsSpecimen() { return OPEN.equals(status) || PRODUCTION_COMPLETE.equals(status); }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public int roundNo() { return roundNo; }
    public String status() { return status; }
    public Instant arrivalTime() { return arrivalTime; }
    public Instant registeredAt() { return registeredAt; }
    public Instant grossingStartTime() { return grossingStartTime; }
    public Instant slideCompletedTime() { return slideCompletedTime; }
    public Instant diagnosisSignedTime() { return diagnosisSignedTime; }
    public Instant endedAt() { return endedAt; }
    public String endedBy() { return endedBy; }
    public long version() { return version; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}

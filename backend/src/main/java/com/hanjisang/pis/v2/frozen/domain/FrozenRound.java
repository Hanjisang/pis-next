package com.hanjisang.pis.v2.frozen.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A frozen round is a business fact inside one frozen Case, not a second Case. */
public final class FrozenRound {

    public static final String OPEN = "OPEN";
    public static final String PRODUCTION_COMPLETE = "PRODUCTION_COMPLETE";
    public static final String SIGNED = "SIGNED";
    public static final String ENDED = "ENDED";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> STATUSES = Set.of(OPEN, PRODUCTION_COMPLETE, SIGNED, ENDED, CANCELLED);

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
    private Instant cancelledAt;
    private String cancelledBy;
    private String cancellationReason;
    private long version;

    private FrozenRound(UUID id, UUID caseId, int roundNo, String status, Instant arrivalTime, Instant registeredAt,
            Instant grossingStartTime, Instant slideCompletedTime, Instant diagnosisSignedTime, Instant endedAt,
            String endedBy, Instant cancelledAt, String cancelledBy, String cancellationReason, long version) {
        this.id = Objects.requireNonNull(id, "Frozen round id is required");
        this.caseId = Objects.requireNonNull(caseId, "Frozen round case is required");
        if (roundNo < 1) throw new IllegalArgumentException("Frozen round number must be positive");
        this.roundNo = roundNo;
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Unsupported frozen round status");
        this.status = status;
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "Frozen arrival time is required");
        this.registeredAt = Objects.requireNonNull(registeredAt, "Frozen registration time is required");
        this.grossingStartTime = grossingStartTime;
        this.slideCompletedTime = slideCompletedTime;
        this.diagnosisSignedTime = diagnosisSignedTime;
        this.endedAt = endedAt;
        this.endedBy = endedBy;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = cancellationReason;
        if (version < 0) throw new IllegalArgumentException("Frozen round version cannot be negative");
        this.version = version;
    }

    public static FrozenRound open(UUID id, UUID caseId, int roundNo, Instant arrivalTime, Instant registeredAt) {
        return new FrozenRound(id, caseId, roundNo, OPEN, arrivalTime, registeredAt, null, null, null, null, null,
                null, null, null, 0);
    }

    public static FrozenRound persisted(UUID id, UUID caseId, int roundNo, String status, Instant arrivalTime,
            Instant registeredAt, Instant grossingStartTime, Instant slideCompletedTime, Instant diagnosisSignedTime,
            Instant endedAt, String endedBy, Instant cancelledAt, String cancelledBy, String cancellationReason,
            long version) {
        return new FrozenRound(id, caseId, roundNo, status, arrivalTime, registeredAt, grossingStartTime,
                slideCompletedTime, diagnosisSignedTime, endedAt, endedBy, cancelledAt, cancelledBy,
                cancellationReason, version);
    }

    /** Compatibility overload for callers that predate round cancellation facts. */
    public static FrozenRound persisted(UUID id, UUID caseId, int roundNo, String status, Instant arrivalTime,
            Instant registeredAt, Instant grossingStartTime, Instant slideCompletedTime, Instant diagnosisSignedTime,
            Instant endedAt, String endedBy, long version) {
        return persisted(id, caseId, roundNo, status, arrivalTime, registeredAt, grossingStartTime,
                slideCompletedTime, diagnosisSignedTime, endedAt, endedBy, null, null, null, version);
    }

    public void markGrossingStarted(Instant at) {
        if (ENDED.equals(status) || SIGNED.equals(status) || CANCELLED.equals(status)) {
            throw new IllegalStateException("已签发、取消或结束的冰冻轮次不能新增取材");
        }
        grossingStartTime = Objects.requireNonNull(at, "Grossing start time is required");
        version++;
    }

    public void markProductionComplete(Instant at) {
        if (ENDED.equals(status) || SIGNED.equals(status) || CANCELLED.equals(status)) return;
        slideCompletedTime = Objects.requireNonNull(at, "Production completion time is required");
        status = PRODUCTION_COMPLETE;
        version++;
    }

    public void markSigned(Instant at) {
        if (ENDED.equals(status) || CANCELLED.equals(status)) {
            throw new IllegalStateException("已取消或结束的冰冻轮次不能签发诊断");
        }
        diagnosisSignedTime = Objects.requireNonNull(at, "Diagnosis sign-off time is required");
        status = SIGNED;
        version++;
    }

    public void end(Instant at, String actorRef) {
        if (ENDED.equals(status) || CANCELLED.equals(status)) return;
        endedAt = Objects.requireNonNull(at, "Frozen end time is required");
        endedBy = required(actorRef, "Frozen end actor is required");
        status = ENDED;
        version++;
    }

    public void cancel(Instant at, String actorRef, String reason) {
        if (SIGNED.equals(status) || ENDED.equals(status)) {
            throw new IllegalStateException("已签发或结束的冰冻轮次不能取消");
        }
        if (CANCELLED.equals(status)) return;
        cancelledAt = Objects.requireNonNull(at, "Round cancellation time is required");
        cancelledBy = required(actorRef, "Round cancellation actor is required");
        cancellationReason = required(reason, "Round cancellation reason is required");
        status = CANCELLED;
        version++;
    }

    public boolean acceptsSpecimen() { return OPEN.equals(status) || PRODUCTION_COMPLETE.equals(status); }
    public boolean cancelled() { return CANCELLED.equals(status); }
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
    public Instant cancelledAt() { return cancelledAt; }
    public String cancelledBy() { return cancelledBy; }
    public String cancellationReason() { return cancellationReason; }
    public long version() { return version; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}

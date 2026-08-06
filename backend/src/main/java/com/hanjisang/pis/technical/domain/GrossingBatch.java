package com.hanjisang.pis.technical.domain;

import java.util.Set;
import java.util.UUID;

public final class GrossingBatch {

    public static final String PLANNED = "P16-GROSSING-PLANNED";
    public static final String ASSIGNED = "P16-GROSSING-ASSIGNED";
    public static final String IN_PROGRESS = "P16-GROSSING-IN-PROGRESS";
    public static final String PAUSED = "P16-GROSSING-PAUSED";
    public static final String COMPLETED = "P16-GROSSING-COMPLETED";
    public static final String HANDED_OFF = "P16-GROSSING-HANDED-OFF";
    public static final String CANCELLED = "P16-GROSSING-CANCELLED";
    public static final String TERMINATED = "P16-GROSSING-TERMINATED";

    private static final Set<String> TERMINAL = Set.of(COMPLETED, HANDED_OFF, CANCELLED, TERMINATED);
    private final UUID id;
    private final String stateCode;
    private final long version;

    private GrossingBatch(UUID id, String stateCode, long version) {
        this.id = id;
        this.stateCode = stateCode;
        this.version = version;
    }

    public static GrossingBatch planned(UUID id) {
        return new GrossingBatch(id, PLANNED, 0);
    }

    public static GrossingBatch persisted(UUID id, String stateCode, long version) {
        return new GrossingBatch(id, stateCode, version);
    }

    public GrossingBatch transition(String target) {
        boolean allowed = switch (stateCode) {
            case PLANNED -> ASSIGNED.equals(target) || IN_PROGRESS.equals(target) || CANCELLED.equals(target);
            case ASSIGNED -> IN_PROGRESS.equals(target) || CANCELLED.equals(target);
            case IN_PROGRESS -> PAUSED.equals(target) || COMPLETED.equals(target) || CANCELLED.equals(target)
                    || TERMINATED.equals(target);
            case PAUSED -> IN_PROGRESS.equals(target) || CANCELLED.equals(target) || TERMINATED.equals(target);
            case COMPLETED -> HANDED_OFF.equals(target);
            default -> false;
        };
        if (!allowed || TERMINAL.contains(stateCode) && !HANDED_OFF.equals(target)) {
            throw new IllegalStateException("非法取材批次状态转换");
        }
        return new GrossingBatch(id, target, version + 1);
    }

    public UUID id() { return id; }
    public String stateCode() { return stateCode; }
    public long version() { return version; }
}

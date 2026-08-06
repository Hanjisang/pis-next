package com.hanjisang.pis.technical.domain;

import java.util.Set;
import java.util.UUID;

public final class ProcessingBatch {

    public static final String PLANNED = "P17-PROCESSING-BATCH-PLANNED";
    public static final String ASSIGNED = "P17-PROCESSING-BATCH-ASSIGNED";
    public static final String IN_PROGRESS = "P17-PROCESSING-BATCH-IN-PROGRESS";
    public static final String PAUSED = "P17-PROCESSING-BATCH-PAUSED";
    public static final String COMPLETED = "P17-PROCESSING-BATCH-COMPLETED";
    public static final String PARTIAL = "P17-PROCESSING-BATCH-PARTIAL";
    public static final String FAILED = "P17-PROCESSING-BATCH-FAILED";
    public static final String INTERRUPTED = "P17-PROCESSING-BATCH-INTERRUPTED";
    public static final String HANDED_OFF = "P17-PROCESSING-BATCH-HANDED-OFF";
    public static final String CANCELLED = "P17-PROCESSING-BATCH-CANCELLED";

    private static final Set<String> TERMINAL = Set.of(COMPLETED, PARTIAL, FAILED, HANDED_OFF, CANCELLED);
    private final UUID id;
    private final String stateCode;
    private final long version;

    private ProcessingBatch(UUID id, String stateCode, long version) {
        this.id = id;
        this.stateCode = stateCode;
        this.version = version;
    }

    public static ProcessingBatch planned(UUID id) { return new ProcessingBatch(id, PLANNED, 0); }

    public static ProcessingBatch persisted(UUID id, String stateCode, long version) {
        return new ProcessingBatch(id, stateCode, version);
    }

    public ProcessingBatch transition(String target) {
        boolean allowed = switch (stateCode) {
            case PLANNED -> ASSIGNED.equals(target) || IN_PROGRESS.equals(target) || CANCELLED.equals(target);
            case ASSIGNED -> IN_PROGRESS.equals(target) || CANCELLED.equals(target);
            case IN_PROGRESS -> PAUSED.equals(target) || COMPLETED.equals(target) || PARTIAL.equals(target)
                    || FAILED.equals(target) || INTERRUPTED.equals(target);
            case PAUSED -> IN_PROGRESS.equals(target) || CANCELLED.equals(target) || INTERRUPTED.equals(target);
            case INTERRUPTED -> IN_PROGRESS.equals(target) || FAILED.equals(target);
            case PARTIAL -> COMPLETED.equals(target) || HANDED_OFF.equals(target);
            case COMPLETED -> HANDED_OFF.equals(target);
            default -> false;
        };
        if (!allowed || TERMINAL.contains(stateCode) && !Set.of(HANDED_OFF, COMPLETED).contains(target)) {
            throw new IllegalStateException("非法组织处理批次状态转换");
        }
        return new ProcessingBatch(id, target, version + 1);
    }

    public UUID id() { return id; }
    public String stateCode() { return stateCode; }
    public long version() { return version; }
}

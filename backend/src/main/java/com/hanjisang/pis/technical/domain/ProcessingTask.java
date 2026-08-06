package com.hanjisang.pis.technical.domain;

import java.util.Set;
import java.util.UUID;

public final class ProcessingTask {

    public static final String PLANNED = "P17-PROCESSING-TASK-PLANNED";
    public static final String ASSIGNED = "P17-PROCESSING-TASK-ASSIGNED";
    public static final String IN_PROGRESS = "P17-PROCESSING-TASK-IN-PROGRESS";
    public static final String COMPLETED = "P17-PROCESSING-TASK-COMPLETED";
    public static final String PARTIAL = "P17-PROCESSING-TASK-PARTIAL";
    public static final String FAILED = "P17-PROCESSING-TASK-FAILED";
    public static final String INTERRUPTED = "P17-PROCESSING-TASK-INTERRUPTED";
    public static final String REPROCESSING = "P17-PROCESSING-TASK-REPROCESSING";
    public static final String CANCELLED = "P17-PROCESSING-TASK-CANCELLED";

    private static final Set<String> TERMINAL = Set.of(COMPLETED, PARTIAL, FAILED, CANCELLED);
    private final UUID id;
    private final String stateCode;
    private final long version;

    private ProcessingTask(UUID id, String stateCode, long version) {
        this.id = id;
        this.stateCode = stateCode;
        this.version = version;
    }

    public static ProcessingTask planned(UUID id) { return new ProcessingTask(id, PLANNED, 0); }

    public static ProcessingTask persisted(UUID id, String stateCode, long version) {
        return new ProcessingTask(id, stateCode, version);
    }

    public ProcessingTask transition(String target) {
        boolean allowed = switch (stateCode) {
            case PLANNED -> ASSIGNED.equals(target) || CANCELLED.equals(target);
            case ASSIGNED -> IN_PROGRESS.equals(target) || PLANNED.equals(target) || CANCELLED.equals(target);
            case IN_PROGRESS -> COMPLETED.equals(target) || PARTIAL.equals(target) || FAILED.equals(target)
                    || INTERRUPTED.equals(target);
            case INTERRUPTED -> IN_PROGRESS.equals(target) || REPROCESSING.equals(target) || FAILED.equals(target);
            case FAILED -> REPROCESSING.equals(target) || CANCELLED.equals(target);
            case REPROCESSING -> IN_PROGRESS.equals(target) || CANCELLED.equals(target);
            case PARTIAL -> COMPLETED.equals(target);
            default -> false;
        };
        if (!allowed || TERMINAL.contains(stateCode) && !Set.of(REPROCESSING, COMPLETED).contains(target)) {
            throw new IllegalStateException("非法组织处理任务状态转换");
        }
        return new ProcessingTask(id, target, version + 1);
    }

    public UUID id() { return id; }
    public String stateCode() { return stateCode; }
    public long version() { return version; }
}

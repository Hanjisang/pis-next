package com.hanjisang.pis.technical.domain;

import java.util.Set;
import java.util.UUID;

public final class EmbeddingTask {

    public static final String PLANNED = "P17-EMBEDDING-TASK-PLANNED";
    public static final String ASSIGNED = "P17-EMBEDDING-TASK-ASSIGNED";
    public static final String IN_PROGRESS = "P17-EMBEDDING-TASK-IN-PROGRESS";
    public static final String COMPLETED = "P17-EMBEDDING-TASK-COMPLETED";
    public static final String FAILED = "P17-EMBEDDING-TASK-FAILED";
    public static final String REWORK = "P17-EMBEDDING-TASK-REWORK";
    public static final String CANCELLED = "P17-EMBEDDING-TASK-CANCELLED";

    private final UUID id;
    private final String stateCode;
    private final long version;

    private EmbeddingTask(UUID id, String stateCode, long version) {
        this.id = id;
        this.stateCode = stateCode;
        this.version = version;
    }

    public static EmbeddingTask planned(UUID id) { return new EmbeddingTask(id, PLANNED, 0); }

    public static EmbeddingTask persisted(UUID id, String stateCode, long version) {
        return new EmbeddingTask(id, stateCode, version);
    }

    public EmbeddingTask transition(String target) {
        boolean allowed = switch (stateCode) {
            case PLANNED -> ASSIGNED.equals(target) || CANCELLED.equals(target);
            case ASSIGNED -> IN_PROGRESS.equals(target) || PLANNED.equals(target) || CANCELLED.equals(target);
            case IN_PROGRESS -> COMPLETED.equals(target) || FAILED.equals(target) || REWORK.equals(target);
            case FAILED -> REWORK.equals(target) || CANCELLED.equals(target);
            case REWORK -> ASSIGNED.equals(target) || IN_PROGRESS.equals(target) || CANCELLED.equals(target);
            default -> false;
        };
        if (!allowed) throw new IllegalStateException("非法包埋任务状态转换");
        return new EmbeddingTask(id, target, version + 1);
    }

    public UUID id() { return id; }
    public String stateCode() { return stateCode; }
    public long version() { return version; }
}

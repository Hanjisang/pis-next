package com.hanjisang.pis.v2.technical.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TechnicalOrderItemResult {
    private final UUID id;
    private final UUID itemId;
    private final String schemaSnapshot;
    private String data;
    private long version;
    private Instant enteredAt;
    private String enteredBy;

    private TechnicalOrderItemResult(UUID id, UUID itemId, String schemaSnapshot, String data, long version,
            Instant enteredAt, String enteredBy) {
        this.id = Objects.requireNonNull(id, "技术结果ID不能为空");
        this.itemId = Objects.requireNonNull(itemId, "技术医嘱项目ID不能为空");
        this.schemaSnapshot = schemaSnapshot;
        this.data = Objects.requireNonNull(data, "技术结果不能为空");
        this.version = version;
        this.enteredAt = Objects.requireNonNull(enteredAt, "结果录入时间不能为空");
        this.enteredBy = Objects.requireNonNull(enteredBy, "结果录入人不能为空");
    }

    public static TechnicalOrderItemResult create(UUID id, UUID itemId, String schemaSnapshot, String data,
            Instant enteredAt, String enteredBy) {
        return new TechnicalOrderItemResult(id, itemId, schemaSnapshot, data, 0, enteredAt, enteredBy);
    }

    public static TechnicalOrderItemResult persisted(UUID id, UUID itemId, String schemaSnapshot, String data,
            long version, Instant enteredAt, String enteredBy) {
        return new TechnicalOrderItemResult(id, itemId, schemaSnapshot, data, version, enteredAt, enteredBy);
    }

    public void update(String data, long expectedVersion, Instant enteredAt, String enteredBy) {
        if (version != expectedVersion) throw new IllegalStateException("技术结果版本冲突");
        this.data = Objects.requireNonNull(data, "技术结果不能为空");
        this.version++;
        this.enteredAt = Objects.requireNonNull(enteredAt, "结果录入时间不能为空");
        this.enteredBy = Objects.requireNonNull(enteredBy, "结果录入人不能为空");
    }

    public UUID id() { return id; }
    public UUID itemId() { return itemId; }
    public String schemaSnapshot() { return schemaSnapshot; }
    public String data() { return data; }
    public long version() { return version; }
    public Instant enteredAt() { return enteredAt; }
    public String enteredBy() { return enteredBy; }
}

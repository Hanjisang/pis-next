package com.hanjisang.pis.v2.molecular.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MolecularResult(UUID id, UUID caseId, UUID specimenId, String resultCode, String resultData,
        String statusCode, Instant completedAt, String completedBy, long concurrencyVersion) {

    public static final String COMPLETED = "COMPLETED";

    public MolecularResult {
        Objects.requireNonNull(id, "分子结果内部ID不能为空");
        Objects.requireNonNull(caseId, "分子结果病例ID不能为空");
        require(resultCode, "分子结果编码不能为空");
        require(resultData, "分子结果数据不能为空");
        if (!COMPLETED.equals(statusCode)) {
            throw new IllegalArgumentException("分子结果必须以已完成事实保存");
        }
        Objects.requireNonNull(completedAt, "分子结果完成时间不能为空");
        require(completedBy, "分子结果完成者不能为空");
        if (concurrencyVersion < 0) {
            throw new IllegalArgumentException("分子结果并发版本不能为负数");
        }
    }

    public static MolecularResult completed(UUID id, UUID caseId, UUID specimenId, String resultCode,
            String resultData, Instant completedAt, String completedBy) {
        return new MolecularResult(id, caseId, specimenId, resultCode, resultData, COMPLETED, completedAt,
                completedBy, 0);
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}

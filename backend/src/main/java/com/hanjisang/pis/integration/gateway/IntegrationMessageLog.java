package com.hanjisang.pis.integration.gateway;

import java.time.Instant;
import java.util.UUID;

public record IntegrationMessageLog(
        UUID id,
        IntegrationEnvelope envelope,
        Status status,
        String responseSummary,
        String errorCode,
        String errorMessage,
        int retryCount,
        int maxRetries,
        Instant nextRetryAt,
        Instant lastAttemptAt,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        PENDING,
        SUCCEEDED,
        RETRY_PENDING,
        DEAD_LETTER
    }
}

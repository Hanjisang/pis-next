package com.hanjisang.pis.integration.gateway;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter.AdapterResult;

public interface IntegrationMessageLogStore {

    Optional<IntegrationMessageLog> findByMessageIdentity(IntegrationEnvelope envelope);

    Optional<IntegrationMessageLog> findById(UUID id);

    Optional<IntegrationMessageLog> claimAttempt(UUID id, int expectedRetryCount, Instant now);

    List<IntegrationAttempt> findAttempts(UUID messageLogId);

    IntegrationMessageLog createPending(IntegrationEnvelope envelope, int maxRetries, Instant now);

    IntegrationMessageLog recordAttempt(IntegrationMessageLog current, String adapterCode, AdapterResult result,
            Instant startedAt, Instant completedAt);

    void requestReplay(UUID messageLogId, String requestedByRef, String reason, Instant now);

    record IntegrationAttempt(UUID attemptId, UUID messageLogId, int attemptNo, String adapterCode,
            Instant startedAt, Instant completedAt, String resultCode, String responseSummary,
            String errorCode, String errorMessage, boolean retryable) { }
}

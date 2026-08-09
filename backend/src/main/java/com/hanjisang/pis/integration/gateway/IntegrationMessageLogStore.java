package com.hanjisang.pis.integration.gateway;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter.AdapterResult;

public interface IntegrationMessageLogStore {

    Optional<IntegrationMessageLog> findByMessageIdentity(IntegrationEnvelope envelope);

    Optional<IntegrationMessageLog> findById(UUID id);

    IntegrationMessageLog createPending(IntegrationEnvelope envelope, int maxRetries, Instant now);

    IntegrationMessageLog recordAttempt(IntegrationMessageLog current, String adapterCode, AdapterResult result,
            Instant startedAt, Instant completedAt);

    void requestReplay(UUID messageLogId, String requestedByRef, String reason, Instant now);
}

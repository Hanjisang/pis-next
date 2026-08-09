package com.hanjisang.pis.integration.gateway;

import java.time.Instant;

public record IntegrationEnvelope(
        String hospitalProfileCode,
        Direction direction,
        String sourceSystemCode,
        String targetSystemCode,
        String messageId,
        IntegrationCapability capability,
        String businessKey,
        String requestReference,
        String requestDigest,
        Instant externalOccurredAt) {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }
}

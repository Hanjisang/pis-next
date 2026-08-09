package com.hanjisang.pis.integration.gateway;

import java.time.Instant;

/** External contract DTO. It must be mapped before an application command can be invoked. */
public record IntegrationRequestDto(
        String hospitalProfileCode,
        String directionCode,
        String sourceSystemCode,
        String targetSystemCode,
        String messageId,
        String capabilityCode,
        String businessKey,
        String requestReference,
        String requestDigest,
        Instant externalOccurredAt) {
}

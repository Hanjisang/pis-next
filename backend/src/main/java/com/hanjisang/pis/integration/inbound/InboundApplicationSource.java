package com.hanjisang.pis.integration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Boundary for applications arriving from an external or local source.
 * Core Case creation is deliberately outside this port.
 */
public interface InboundApplicationSource {

    String sourceSystemCode();

    List<InboundApplication> findApplications();

    default Optional<InboundApplication> find(UUID applicationId) {
        return findApplications().stream().filter(item -> item.applicationId().equals(applicationId)).findFirst();
    }

    default void markRegistered(UUID applicationId, UUID caseId, Instant registeredAt) { }

    record InboundApplication(UUID applicationId, String applicationNo, String patientReference,
            String visitReference, String department, String doctor, String applicationItemCode,
            Instant receivedAt, boolean cancelled, String sourceSystemCode, UUID registeredCaseId,
            Instant registeredAt) { }
}

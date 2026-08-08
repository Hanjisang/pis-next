package com.hanjisang.pis.v2.sendout.domain;

import java.time.Instant;
import java.util.UUID;

public record SendOut(UUID id, UUID caseId, String externalReference, String destinationName, String statusCode,
        Instant requestedAt, String requestedBy, String resultData, Instant resultReceivedAt,
        String resultReceivedBy) {

    public static final String REQUESTED = "REQUESTED";
    public static final String RESULT_RECEIVED = "RESULT_RECEIVED";

    public static SendOut requested(UUID id, UUID caseId, String externalReference, String destinationName,
            Instant requestedAt, String requestedBy) {
        return new SendOut(id, caseId, externalReference, destinationName, REQUESTED, requestedAt, requestedBy,
                null, null, null);
    }

    public SendOut withResult(String resultData, Instant receivedAt, String receivedBy) {
        return new SendOut(id, caseId, externalReference, destinationName, RESULT_RECEIVED, requestedAt,
                requestedBy, resultData, receivedAt, receivedBy);
    }
}

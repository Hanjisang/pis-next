package com.hanjisang.pis.v2.digital.domain;

import java.time.Instant;
import java.util.UUID;

public record DigitalSlide(UUID id, UUID caseId, UUID blockId, UUID slideId, String bindingModeCode,
        String statusCode, String viewerReference, String sourcePlatform, Instant createdAt, String createdBy,
        Instant updatedAt, String updatedBy) {

    public static final String ACTIVE = "ACTIVE";
    public static final String UNBOUND = "UNBOUND";
}

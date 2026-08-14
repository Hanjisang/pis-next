package com.hanjisang.pis.v2.report.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportTemplate(UUID id, String organizationReference, UUID businessTypeId, String code, String name,
        boolean enabled, int configurationVersion, Instant createdAt, String createdBy, Instant updatedAt,
        String updatedBy, String sourcePresetCode) { }

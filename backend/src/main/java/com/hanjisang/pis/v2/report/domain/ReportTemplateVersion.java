package com.hanjisang.pis.v2.report.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportTemplateVersion(UUID id, UUID templateId, int versionNo, String definition, String status,
        Instant publishedAt, String publishedBy, Instant createdAt, String createdBy, long version) {

    public boolean published() {
        return "PUBLISHED".equals(status) && publishedAt != null;
    }
}

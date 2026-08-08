package com.hanjisang.pis.v2.report.domain;

import java.time.Instant;
import java.util.UUID;

public record Report(UUID id, String reportNo, String organizationReference, UUID caseId, UUID diagnosisId,
        UUID templateVersionId, ReportNature nature, UUID priorReportId, ReportStatus status,
        String diagnosisSnapshot, String responsibilitySnapshot, String caseSnapshot, String materialSnapshot,
        String technicalResultSnapshot, String supplementalContent, String renderedContent,
        String renderedContentHash, String pdfFileReference, String pdfContentHash, String signedBy,
        Instant signedAt, String withdrawnBy, Instant withdrawnAt, String withdrawalReason, long version,
        Instant createdAt, String createdBy) { }

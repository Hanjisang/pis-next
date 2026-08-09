package com.hanjisang.pis.integration.device;

import java.time.Instant;
import java.util.UUID;

public interface ScannerAdapter extends DeviceAdapter {

    ScanSubmission submit(ScanRequest request);

    record ScanRequest(String scannerCode, UUID caseId, UUID blockId, UUID slideId, String correlationId) { }

    record ScanSubmission(String scannerJobReference, String statusCode) { }

    record ScanCompletedEvent(String scannerCode, String scannerJobReference, UUID caseId, UUID blockId,
            UUID slideId, String sourcePlatform, String externalImageId, String viewerReference,
            String contentDigest, Instant completedAt) { }
}

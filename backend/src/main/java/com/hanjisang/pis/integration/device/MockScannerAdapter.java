package com.hanjisang.pis.integration.device;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class MockScannerAdapter implements ScannerAdapter {

    @Override
    public String adapterCode() {
        return "MOCK_SCANNER";
    }

    @Override
    public String deviceTypeCode() {
        return "DIGITAL_SCANNER";
    }

    @Override
    public ScanSubmission submit(ScanRequest request) {
        return new ScanSubmission("MOCK-SCAN-" + UUID.randomUUID(), "ACCEPTED");
    }

    public ScanCompletedEvent complete(ScanRequest request, String scannerJobReference) {
        return new ScanCompletedEvent(request.scannerCode(), scannerJobReference, request.caseId(), request.blockId(),
                request.slideId(), "MOCK_WSI_PLATFORM", "MOCK-IMAGE-" + request.slideId(),
                "mock://viewer/" + request.slideId(), "synthetic-content-digest", Instant.now());
    }
}

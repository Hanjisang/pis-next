package com.hanjisang.pis.integration.device;

import java.time.Instant;
import java.util.UUID;

/** Boundary for a grossing capture station; production adapters are external. */
public interface GrossImagingDevicePort {

    CaptureResult capture(CaptureRequest request);

    DeviceStatus deviceStatus(String deviceReference);

    record CaptureRequest(UUID grossingId, UUID specimenId, String deviceReference, String operatorReference) { }

    record CaptureResult(String imageName, String mediaType, String storageReference, String metadataJson,
            Instant capturedAt, String deviceJobReference) { }

    record DeviceStatus(String deviceReference, String statusCode, String detail, Instant checkedAt) { }
}

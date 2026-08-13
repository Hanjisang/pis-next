package com.hanjisang.pis.integration.device;

import java.time.Instant;
import java.util.UUID;

/** Port for an IHC/special-stain device; production vendor transport remains external. */
public interface IhcDevicePort {

    Submission submit(Request request);

    record Request(UUID technicalItemId, String projectCode, String deviceTypeCode, String parametersJson,
            String operatorReference) { }

    record Submission(String adapterCode, String requestReference, String statusCode, String errorCode,
            String errorMessage, Instant completedAt) {
        public boolean succeeded() { return "SUCCEEDED".equals(statusCode); }
    }
}

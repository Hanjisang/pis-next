package com.hanjisang.pis.v2.molecular.api;

import java.util.UUID;

public interface MolecularInstrumentPort {
    boolean supports(String adapterCode);
    StartResponse start(StartRequest request);

    record StartRequest(UUID testId, String detectionNo, String projectCode, String rawDataReference,
            String requestReference) { }
    record StartResponse(boolean accepted, String responseReference, String errorCode, String errorMessage) { }
}

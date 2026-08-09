package com.hanjisang.pis.integration.gateway;

import java.util.Set;

public interface IntegrationAdapter {

    String adapterCode();

    Set<IntegrationCapability> capabilities();

    AdapterResult exchange(IntegrationEnvelope envelope);

    default boolean supports(IntegrationCapability capability) {
        return capabilities().contains(capability);
    }

    record AdapterResult(boolean succeeded, boolean retryable, String responseSummary, String errorCode,
            String errorMessage) {

        public static AdapterResult success(String responseSummary) {
            return new AdapterResult(true, false, responseSummary, null, null);
        }

        public static AdapterResult failure(boolean retryable, String errorCode, String errorMessage) {
            return new AdapterResult(false, retryable, null, errorCode, errorMessage);
        }
    }
}

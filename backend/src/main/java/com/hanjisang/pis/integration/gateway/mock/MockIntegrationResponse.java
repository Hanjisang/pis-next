package com.hanjisang.pis.integration.gateway.mock;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter.AdapterResult;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope;

final class MockIntegrationResponse {

    private MockIntegrationResponse() { }

    static AdapterResult respond(IntegrationEnvelope envelope, String successCode) {
        if (envelope.requestReference().startsWith("mock://reject")) {
            return AdapterResult.failure(false, "MOCK_BUSINESS_REJECTED", "合成外部业务拒绝");
        }
        if (envelope.requestReference().startsWith("mock://fail")) {
            return AdapterResult.failure(true, "MOCK_TARGET_UNAVAILABLE", "合成外部系统不可用");
        }
        return AdapterResult.success(successCode);
    }
}

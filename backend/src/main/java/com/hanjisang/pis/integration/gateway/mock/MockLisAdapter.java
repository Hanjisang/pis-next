package com.hanjisang.pis.integration.gateway.mock;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter;
import com.hanjisang.pis.integration.gateway.IntegrationCapability;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope;

@Component
public class MockLisAdapter implements IntegrationAdapter {

    @Override
    public String adapterCode() {
        return "MOCK_LIS";
    }

    @Override
    public Set<IntegrationCapability> capabilities() {
        return Set.of(IntegrationCapability.SPECIMEN_RECEIVE, IntegrationCapability.RESULT_RECEIVE);
    }

    @Override
    public AdapterResult exchange(IntegrationEnvelope envelope) {
        return MockIntegrationResponse.respond(envelope, "LIS_ACCEPTED");
    }
}

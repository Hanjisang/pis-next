package com.hanjisang.pis.integration.gateway.mock;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter;
import com.hanjisang.pis.integration.gateway.IntegrationCapability;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope;

/** Simulator only. Real OR/HIS delivery remains an external dependency. */
@Component
public class MockClinicalResultNotificationAdapter implements IntegrationAdapter {

    @Override
    public String adapterCode() { return "MOCK_FROZEN_NOTIFICATION"; }

    @Override
    public Set<IntegrationCapability> capabilities() {
        return Set.of(IntegrationCapability.CLINICAL_RESULT_NOTIFICATION);
    }

    @Override
    public AdapterResult exchange(IntegrationEnvelope envelope) {
        return MockIntegrationResponse.respond(envelope, "SIMULATED_NOTIFICATION_ACCEPTED");
    }
}

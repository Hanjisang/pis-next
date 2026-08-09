package com.hanjisang.pis.integration.gateway.mock;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter;
import com.hanjisang.pis.integration.gateway.IntegrationCapability;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope;

@Component
public class MockHisAdapter implements IntegrationAdapter {

    @Override
    public String adapterCode() {
        return "MOCK_HIS";
    }

    @Override
    public Set<IntegrationCapability> capabilities() {
        return Set.of(IntegrationCapability.PATIENT_QUERY, IntegrationCapability.ENCOUNTER_QUERY,
                IntegrationCapability.ORDER_RECEIVE, IntegrationCapability.FEE_STATUS_SYNC,
                IntegrationCapability.CLINICAL_INFORMATION_QUERY);
    }

    @Override
    public AdapterResult exchange(IntegrationEnvelope envelope) {
        return MockIntegrationResponse.respond(envelope, "HIS_ACCEPTED");
    }
}

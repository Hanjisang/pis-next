package com.hanjisang.pis.integration.gateway.mock;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter;
import com.hanjisang.pis.integration.gateway.IntegrationCapability;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope;

@Component
public class MockReportDeliveryAdapter implements IntegrationAdapter {

    @Override
    public String adapterCode() {
        return "MOCK_REPORT_DELIVERY";
    }

    @Override
    public Set<IntegrationCapability> capabilities() {
        return Set.of(IntegrationCapability.REPORT_DELIVERY, IntegrationCapability.REPORT_VIEW,
                IntegrationCapability.PROVINCE_PLATFORM, IntegrationCapability.REGIONAL_PLATFORM);
    }

    @Override
    public AdapterResult exchange(IntegrationEnvelope envelope) {
        return MockIntegrationResponse.respond(envelope, "REPORT_ACCEPTED");
    }
}

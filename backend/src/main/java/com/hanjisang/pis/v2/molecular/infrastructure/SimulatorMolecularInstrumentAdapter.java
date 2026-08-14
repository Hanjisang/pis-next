package com.hanjisang.pis.v2.molecular.infrastructure;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.v2.molecular.api.MolecularInstrumentPort;

@Component
public class SimulatorMolecularInstrumentAdapter implements MolecularInstrumentPort {
    @Override
    public boolean supports(String adapterCode) {
        return "SIMULATOR".equalsIgnoreCase(adapterCode);
    }

    @Override
    public StartResponse start(StartRequest request) {
        return new StartResponse(true, "SIM-RUN-" + request.testId(), null, null);
    }
}

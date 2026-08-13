package com.hanjisang.pis.integration.device;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class SimulatorIhcDeviceAdapter implements IhcDevicePort {

    @Override
    public Submission submit(Request request) {
        return new Submission("SIMULATOR_IHC", "SIM-IHC-" + UUID.randomUUID(), "SUCCEEDED", null, null,
                Instant.now());
    }
}

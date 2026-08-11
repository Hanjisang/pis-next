package com.hanjisang.pis.integration.device;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class SimulatorGrossImagingDeviceAdapter implements GrossImagingDevicePort, DeviceAdapter {

    @Override
    public String adapterCode() { return "SIMULATOR_GROSS_IMAGING"; }

    @Override
    public String deviceTypeCode() { return "GROSS_IMAGING_STATION"; }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        String job = "GROSS-IMG-SIM-" + UUID.randomUUID();
        return new CaptureResult("grossing-" + request.grossingId() + ".jpg", "image/jpeg",
                "simulator://grossing-image/" + job, "{\"source\":\"simulator\",\"job\":\"" + job + "\"}",
                Instant.now(), job);
    }

    @Override
    public DeviceStatus deviceStatus(String deviceReference) {
        return new DeviceStatus(deviceReference, "READY", "Simulator capture station is available", Instant.now());
    }
}

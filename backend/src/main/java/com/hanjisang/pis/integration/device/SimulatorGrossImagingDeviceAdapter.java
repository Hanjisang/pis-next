package com.hanjisang.pis.integration.device;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="540" viewBox="0 0 960 540">
                  <rect width="960" height="540" fill="#e9e3d7"/>
                  <rect x="90" y="70" width="780" height="400" rx="18" fill="#d7c5aa" stroke="#675c4d" stroke-width="8"/>
                  <ellipse cx="480" cy="270" rx="230" ry="125" fill="#a96f65" stroke="#75443f" stroke-width="10"/>
                  <path d="M300 260 C390 170 570 360 660 250" fill="none" stroke="#f2c8bb" stroke-width="22"/>
                  <text x="480" y="505" text-anchor="middle" font-family="sans-serif" font-size="26" fill="#40372d">大体拍摄模拟图像</text>
                </svg>
                """;
        String storageReference = "data:image/svg+xml;base64," + Base64.getEncoder()
                .encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptureResult("grossing-" + request.grossingId() + ".svg", "image/svg+xml",
                storageReference, "{\"source\":\"simulator\",\"device\":\""
                        + String.valueOf(request.deviceReference()).replace("\"", "") + "\",\"job\":\""
                        + job + "\"}",
                Instant.now(), job);
    }

    @Override
    public DeviceStatus deviceStatus(String deviceReference) {
        return new DeviceStatus(deviceReference, "READY", "Simulator capture station is available", Instant.now());
    }
}

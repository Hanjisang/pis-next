package com.hanjisang.pis.integration.device;

import org.springframework.stereotype.Component;

/** Adapter shell only. The vendor transport belongs to a hospital implementation project. */
@Component
public class GK888Adapter implements LabelPrinter {

    @Override
    public String adapterCode() {
        return "GK888";
    }

    @Override
    public String deviceTypeCode() {
        return "LABEL_PRINTER";
    }

    @Override
    public PrintResult print(PrintCommand command) {
        return PrintResult.failure("VENDOR_TRANSPORT_NOT_CONFIGURED", "GK888 真实传输适配器尚未配置");
    }
}

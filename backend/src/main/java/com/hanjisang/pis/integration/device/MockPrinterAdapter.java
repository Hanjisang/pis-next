package com.hanjisang.pis.integration.device;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class MockPrinterAdapter implements LabelPrinter {

    @Override
    public String adapterCode() {
        return "MOCK";
    }

    @Override
    public String deviceTypeCode() {
        return "LABEL_PRINTER";
    }

    @Override
    public PrintResult print(PrintCommand command) {
        if (command.logicalPrinterCode().startsWith("FAIL")) {
            return PrintResult.failure("MOCK_PRINTER_FAILURE", "合成打印机故障");
        }
        return PrintResult.success("MOCK-JOB-" + UUID.randomUUID());
    }
}

package com.hanjisang.pis.v2.operations.infrastructure;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.v2.operations.api.ReportOutputPort;

/** Product-internal simulator. Real delivery channels and printers remain external adapters. */
@Component
public class SimulatorReportOutputAdapter implements ReportOutputPort {

    private static final String MOCK_PRINTER_PREFIX = "MOCK://";

    @Override
    public DistributionResult distribute(DistributionCommand command) {
        if ("SIMULATOR_PATIENT_PORTAL".equals(command.targetCode())) {
            return DistributionResult.sent("SIM-DISTRIBUTION-" + UUID.randomUUID());
        }
        return DistributionResult.failed("DELIVERY_ADAPTER_NOT_CONFIGURED",
                "未配置报告发放通道：" + command.targetCode());
    }

    @Override
    public PrintResult print(PrintCommand command) {
        if (command.printerReference().startsWith(MOCK_PRINTER_PREFIX)) {
            return PrintResult.success("SIM-PRINT-" + UUID.randomUUID());
        }
        return PrintResult.failed("DOCUMENT_PRINTER_NOT_CONFIGURED",
                "未配置报告打印机：" + command.printerReference());
    }

    @Override
    public PrinterStatus printerStatus(String printerReference) {
        if (printerReference != null && printerReference.startsWith(MOCK_PRINTER_PREFIX)) {
            return new PrinterStatus(printerReference, "READY", "产品内报告打印 Simulator 可用");
        }
        return new PrinterStatus(printerReference, "UNCONFIGURED", "真实报告打印机适配器尚未配置");
    }
}

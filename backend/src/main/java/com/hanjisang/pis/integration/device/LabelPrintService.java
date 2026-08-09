package com.hanjisang.pis.integration.device;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class LabelPrintService {

    private static final String PROFILE_SEPARATOR = "://";

    private final Map<String, LabelPrinter> printers;

    public LabelPrintService(List<LabelPrinter> printers) {
        this.printers = printers.stream().collect(Collectors.toUnmodifiableMap(LabelPrinter::adapterCode,
                Function.identity()));
    }

    public PrintResult print(PrintRequest request) {
        PrinterProfile profile = PrinterProfile.parse(request.printerProfileCode());
        LabelPrinter printer = printers.get(profile.adapterCode());
        if (printer == null) {
            return new PrintResult("FAILED", "PRINTER_ADAPTER_NOT_FOUND",
                    "未配置标签打印适配器：" + profile.adapterCode());
        }
        LabelPrinter.PrintResult result = printer.print(new LabelPrinter.PrintCommand(profile.logicalPrinterCode(),
                profile.endpointReference(), request.entityKindCode(), request.businessCode(),
                request.renderedLabel(), request.operatorRef()));
        return result.succeeded() ? new PrintResult("SUCCESS", null, null)
                : new PrintResult("FAILED", result.errorCode(), result.errorMessage());
    }

    public record PrintRequest(String entityKindCode, UUID entityId, String businessCode, String printerProfileCode,
            String renderedLabel, String operatorRef) { }

    public record PrintResult(String resultCode, String errorCode, String failureReason) {
        public boolean succeeded() { return "SUCCESS".equals(resultCode); }
    }

    private record PrinterProfile(String adapterCode, String logicalPrinterCode, String endpointReference) {
        static PrinterProfile parse(String value) {
            if (value == null || value.isBlank() || !value.contains(PROFILE_SEPARATOR)) {
                throw new IllegalArgumentException("打印配置必须使用 adapter://logical-printer 格式");
            }
            int separator = value.indexOf(PROFILE_SEPARATOR);
            String adapter = value.substring(0, separator).trim();
            String logicalPrinter = value.substring(separator + PROFILE_SEPARATOR.length()).trim();
            if (adapter.isBlank() || logicalPrinter.isBlank()) {
                throw new IllegalArgumentException("打印适配器和逻辑打印机均不能为空");
            }
            return new PrinterProfile(adapter, logicalPrinter, value);
        }
    }
}

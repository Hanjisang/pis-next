package com.hanjisang.pis.v2.operations.api;

import java.util.UUID;

public interface ReportOutputPort {

    DistributionResult distribute(DistributionCommand command);

    PrintResult print(PrintCommand command);

    PrinterStatus printerStatus(String printerReference);

    record DistributionCommand(UUID reportId, String reportNo, String targetCode, String pdfContentHash) { }
    record DistributionResult(String statusCode, String deliveryReference, String errorCode, String errorMessage) {
        public static DistributionResult sent(String reference) {
            return new DistributionResult("SENT", reference, null, null);
        }

        public static DistributionResult failed(String code, String message) {
            return new DistributionResult("FAILED", null, code, message);
        }
    }
    record PrintCommand(UUID reportId, String reportNo, String identityReference, String terminalReference,
            String printerReference, int copyCount, byte[] pdfContent, String pdfContentHash) { }
    record PrintResult(String resultCode, String deviceJobReference, String errorCode, String errorMessage) {
        public static PrintResult success(String reference) {
            return new PrintResult("SUCCESS", reference, null, null);
        }

        public static PrintResult failed(String code, String message) {
            return new PrintResult("FAILED", null, code, message);
        }
    }
    record PrinterStatus(String printerReference, String statusCode, String detail) { }
}

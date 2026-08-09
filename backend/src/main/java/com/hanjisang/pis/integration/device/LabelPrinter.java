package com.hanjisang.pis.integration.device;

public interface LabelPrinter extends DeviceAdapter {

    PrintResult print(PrintCommand command);

    record PrintCommand(String logicalPrinterCode, String endpointReference, String entityKindCode,
            String businessCode, String renderedLabel, String operatorRef) { }

    record PrintResult(boolean succeeded, String deviceJobReference, String errorCode, String errorMessage) {
        public static PrintResult success(String deviceJobReference) {
            return new PrintResult(true, deviceJobReference, null, null);
        }

        public static PrintResult failure(String errorCode, String errorMessage) {
            return new PrintResult(false, null, errorCode, errorMessage);
        }
    }
}

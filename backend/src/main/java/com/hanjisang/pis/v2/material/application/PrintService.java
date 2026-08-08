package com.hanjisang.pis.v2.material.application;

import java.util.UUID;

public interface PrintService {

    PrintResult print(PrintRequest request);

    record PrintRequest(String entityKindCode, UUID entityId, String businessCode, String printerProfileCode,
            String operatorRef) { }

    record PrintResult(String resultCode, String failureReason) {
        public boolean succeeded() { return "SUCCESS".equals(resultCode); }
    }
}

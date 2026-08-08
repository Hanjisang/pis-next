package com.hanjisang.pis.v2.material.application;

import org.springframework.stereotype.Component;

@Component
public class FakePrintService implements PrintService {

    @Override
    public PrintResult print(PrintRequest request) {
        if (request.printerProfileCode().startsWith("FAIL")) {
            return new PrintResult("FAILED", "合成打印机故障");
        }
        return new PrintResult("SUCCESS", null);
    }
}

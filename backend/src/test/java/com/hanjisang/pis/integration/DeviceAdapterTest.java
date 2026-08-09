package com.hanjisang.pis.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.integration.device.GK888Adapter;
import com.hanjisang.pis.integration.device.LabelPrintService;
import com.hanjisang.pis.integration.device.MockPrinterAdapter;
import com.hanjisang.pis.integration.device.MockScannerAdapter;
import com.hanjisang.pis.integration.device.ScannerAdapter.ScanRequest;
import com.hanjisang.pis.integration.device.ZebraAdapter;

class DeviceAdapterTest {

    @Test
    void labelPrintServiceRoutesOnlyThroughConfiguredAdapter() {
        LabelPrintService service = new LabelPrintService(
                List.of(new MockPrinterAdapter(), new GK888Adapter(), new ZebraAdapter()));
        UUID slideId = UUID.randomUUID();

        var success = service.print(new LabelPrintService.PrintRequest("SLIDE", slideId, "A1-HE",
                "MOCK://MATERIAL-PRINTER", "A1-HE", "TECH-001"));
        var failure = service.print(new LabelPrintService.PrintRequest("SLIDE", slideId, "A1-HE",
                "MOCK://FAIL-MATERIAL-PRINTER", "A1-HE", "TECH-001"));
        var vendorNotConfigured = service.print(new LabelPrintService.PrintRequest("SLIDE", slideId, "A1-HE",
                "GK888://GROSSING-ROOM-1", "A1-HE", "TECH-001"));

        assertThat(success.resultCode()).isEqualTo("SUCCESS");
        assertThat(failure.resultCode()).isEqualTo("FAILED");
        assertThat(failure.errorCode()).isEqualTo("MOCK_PRINTER_FAILURE");
        assertThat(vendorNotConfigured.errorCode()).isEqualTo("VENDOR_TRANSPORT_NOT_CONFIGURED");
    }

    @Test
    void mockScannerProducesMetadataEventWithoutImplementingWsiTiles() {
        MockScannerAdapter scanner = new MockScannerAdapter();
        UUID caseId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID slideId = UUID.randomUUID();
        ScanRequest request = new ScanRequest("SYNTH-SCANNER", caseId, blockId, slideId, "CORR-001");

        var submission = scanner.submit(request);
        var completed = scanner.complete(request, submission.scannerJobReference());

        assertThat(submission.statusCode()).isEqualTo("ACCEPTED");
        assertThat(completed.caseId()).isEqualTo(caseId);
        assertThat(completed.blockId()).isEqualTo(blockId);
        assertThat(completed.slideId()).isEqualTo(slideId);
        assertThat(completed.viewerReference()).startsWith("mock://viewer/");
        assertThat(completed.contentDigest()).isNotBlank();
    }
}

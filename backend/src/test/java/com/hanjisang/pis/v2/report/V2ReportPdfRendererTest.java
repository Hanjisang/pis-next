package com.hanjisang.pis.v2.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import com.hanjisang.pis.v2.report.application.V2ReportPdfRenderer;

class V2ReportPdfRendererTest {

    @Test
    void preservesLongUnicodeContentAcrossProtectedPages() throws Exception {
        String content = "合成病理正文-".repeat(1_200) + "-END-OF-SYNTHETIC-REPORT";
        byte[] pdf = new V2ReportPdfRenderer().render("SYNTH-R001", content, "synthetic-content-hash");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(document.isEncrypted()).isTrue();
            assertThat(document.getDocumentInformation().getCustomMetadataValue("PIS-V2-Content-Characters"))
                    .isEqualTo(Integer.toString(content.codePointCount(0, content.length())));
            assertThat(document.getDocumentInformation().getCustomMetadataValue("PIS-V2-Content-Hash"))
                    .isEqualTo("synthetic-content-hash");
        }
    }
}

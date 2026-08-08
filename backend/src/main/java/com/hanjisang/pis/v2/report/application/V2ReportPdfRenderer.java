package com.hanjisang.pis.v2.report.application;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * Produces a deterministic, self-contained PDF artifact for the V2 formal-output boundary.
 * A production deployment may replace this adapter with a managed renderer without changing
 * the report aggregate or its immutable output contract.
 */
@Component
public class V2ReportPdfRenderer {

    public byte[] render(String reportNo, String renderedContent, String contentHash) {
        String safeContent = renderedContent.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replace("\r", " ").replace("\n", " ");
        String body = "BT /F1 10 Tf 40 760 Td (PIS V2 Report " + reportNo + ") Tj 0 -16 Td (Content "
                + contentHash + ") Tj 0 -16 Td (" + safeContent.substring(0, Math.min(1500, safeContent.length()))
                + ") Tj ET";
        String objects = "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n"
                + "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n"
                + "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>endobj\n"
                + "4 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n"
                + "5 0 obj<< /Length " + body.getBytes(StandardCharsets.US_ASCII).length + " >>stream\n" + body
                + "\nendstream\nendobj\n";
        String prefix = "%PDF-1.4\n%V2\n";
        int offset = prefix.getBytes(StandardCharsets.US_ASCII).length;
        StringBuilder output = new StringBuilder(prefix).append(objects);
        int xref = output.toString().getBytes(StandardCharsets.US_ASCII).length;
        output.append("xref\n0 6\n0000000000 65535 f \n");
        int cursor = offset;
        String[] objectTexts = objects.split("(?<=endobj\\n)");
        for (String object : objectTexts) {
            output.append(String.format("%010d 00000 n \n", cursor));
            cursor += object.getBytes(StandardCharsets.US_ASCII).length;
        }
        output.append("trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return output.toString().getBytes(StandardCharsets.US_ASCII);
    }
}

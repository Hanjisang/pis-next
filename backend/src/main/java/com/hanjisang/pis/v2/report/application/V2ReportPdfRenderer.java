package com.hanjisang.pis.v2.report.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/** Produces immutable, paginated PDF output without truncating report content. */
@Component
public class V2ReportPdfRenderer {

    private static final int IMAGE_WIDTH = 1240;
    private static final int IMAGE_HEIGHT = 1754;
    private static final int LEFT = 86;
    private static final int TOP = 104;
    private static final int LINE_HEIGHT = 30;
    private static final int LINES_PER_PAGE = 48;
    private static final int CODE_POINTS_PER_LINE = 72;
    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 20);

    public byte[] render(String reportNo, String renderedContent, String contentHash) {
        List<String> lines = wrap(displayable(renderedContent, BODY_FONT));
        int pageCount = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("PIS V2 Report " + reportNo);
            document.getDocumentInformation().setCustomMetadataValue("PIS-V2-Content-Hash", contentHash);
            document.getDocumentInformation().setCustomMetadataValue("PIS-V2-Content-Characters",
                    Integer.toString(renderedContent.codePointCount(0, renderedContent.length())));
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                addPage(document, reportNo, contentHash, lines, pageIndex, pageCount);
            }
            protect(document, "");
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("报告PDF生成失败", exception);
        }
    }

    public byte[] encryptForDelivery(byte[] source, String accessPassword) {
        try (PDDocument document = Loader.loadPDF(source); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            protect(document, accessPassword);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("报告PDF加密失败", exception);
        }
    }

    private void addPage(PDDocument document, String reportNo, String contentHash, List<String> lines,
            int pageIndex, int pageCount) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            graphics.setColor(new Color(28, 39, 49));
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(HEADER_FONT);
            graphics.drawString(displayable("PIS V2 病理报告 " + reportNo, HEADER_FONT), LEFT, TOP);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
            graphics.setColor(new Color(83, 96, 107));
            graphics.drawString("内容摘要 " + contentHash, LEFT, TOP + 38);
            graphics.drawString("第 " + (pageIndex + 1) + " / " + pageCount + " 页", IMAGE_WIDTH - 210, TOP);
            graphics.setColor(new Color(28, 39, 49));
            graphics.setFont(BODY_FONT);
            int start = pageIndex * LINES_PER_PAGE;
            int end = Math.min(lines.size(), start + LINES_PER_PAGE);
            int y = TOP + 86;
            for (int index = start; index < end; index++) {
                graphics.drawString(lines.get(index), LEFT, y);
                y += LINE_HEIGHT;
            }
        } finally {
            graphics.dispose();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDImageXObject pageImage = LosslessFactory.createFromImage(document, image);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.drawImage(pageImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        }
    }

    private static List<String> wrap(String content) {
        List<String> lines = new ArrayList<>();
        content.lines().forEach(paragraph -> {
            int[] points = paragraph.codePoints().toArray();
            if (points.length == 0) {
                lines.add("");
                return;
            }
            for (int offset = 0; offset < points.length; offset += CODE_POINTS_PER_LINE) {
                lines.add(new String(points, offset, Math.min(CODE_POINTS_PER_LINE, points.length - offset)));
            }
        });
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private static String displayable(String value, Font font) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (font.canDisplay(codePoint)) result.appendCodePoint(codePoint);
            else if (codePoint <= 0xffff) result.append(String.format("\\u%04X", codePoint));
            else result.append(String.format("\\U%08X", codePoint));
        });
        return result.toString();
    }

    private static void protect(PDDocument document, String userPassword) throws IOException {
        AccessPermission permission = new AccessPermission();
        permission.setCanModify(false);
        permission.setCanModifyAnnotations(false);
        permission.setCanFillInForm(false);
        permission.setCanAssembleDocument(false);
        permission.setCanExtractContent(false);
        permission.setCanExtractForAccessibility(true);
        permission.setCanPrint(true);
        permission.setCanPrintFaithful(true);
        StandardProtectionPolicy policy = new StandardProtectionPolicy(UUID.randomUUID().toString(), userPassword,
                permission);
        policy.setEncryptionKeyLength(256);
        policy.setPreferAES(true);
        document.protect(policy);
    }
}

package zw.ac.uz.emhare.documentsreporting.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.documentsreporting.projection.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.projection.PublishedResultProjection;

/** @author Tinashe K */
@Component
public class OfficialResultSlipPdfRenderer {

    private static final float PAGE_MARGIN = 42f;
    private static final float ROW_HEIGHT = 22f;
    private static final int MAXIMUM_ROWS_PER_PAGE = 22;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm 'CAT'")
            .withZone(ZoneId.of("Africa/Harare"));
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public RenderedPdf render(
            GeneratedDocument generatedDocument,
            ProgressionDecisionProjection decision,
            List<PublishedResultProjection> results) {
        if (results.isEmpty()) {
            throw new IllegalStateException("An official result slip requires at least one published Module result.");
        }
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle("Official Result Slip - " + generatedDocument.getStudentNumber());
            information.setAuthor("Tinashe K");
            information.setSubject("eMhare official academic result record");
            information.setKeywords(generatedDocument.getDocumentNumber());
            pdf.setDocumentInformation(information);

            int pageCount = Math.max(1, (results.size() + MAXIMUM_ROWS_PER_PAGE - 1) / MAXIMUM_ROWS_PER_PAGE);
            for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
                int fromIndex = pageNumber * MAXIMUM_ROWS_PER_PAGE;
                int toIndex = Math.min(results.size(), fromIndex + MAXIMUM_ROWS_PER_PAGE);
                renderPage(
                        pdf,
                        generatedDocument,
                        decision,
                        results.subList(fromIndex, toIndex),
                        pageNumber + 1,
                        pageCount);
            }
            pdf.save(output);
            return new RenderedPdf(output.toByteArray(), pageCount);
        } catch (IOException exception) {
            throw new IllegalStateException("Official result slip PDF could not be rendered.", exception);
        }
    }

    private void renderPage(
            PDDocument pdf,
            GeneratedDocument document,
            ProgressionDecisionProjection decision,
            List<PublishedResultProjection> results,
            int pageNumber,
            int pageCount) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
            float pageWidth = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - PAGE_MARGIN;

            content.setNonStrokingColor(0f, 91f / 255f, 65f / 255f);
            content.addRect(0, y - 66, pageWidth, 108);
            content.fill();
            text(content, BOLD, 19, PAGE_MARGIN, y + 5, "UNIVERSITY OF ZIMBABWE");
            text(content, REGULAR, 9, PAGE_MARGIN, y - 12, "eMhare Academic Records");
            text(content, BOLD, 14, PAGE_MARGIN, y - 36, "OFFICIAL RESULT SLIP");
            text(content, REGULAR, 8, pageWidth - 190, y - 36,
                    "Document " + limited(document.getDocumentNumber(), 30));

            y -= 92;
            content.setNonStrokingColor(28f / 255f, 37f / 255f, 42f / 255f);
            text(content, BOLD, 9, PAGE_MARGIN, y, "STUDENT");
            text(content, REGULAR, 10, PAGE_MARGIN, y - 18, decision.getStudentNumber());
            text(content, BOLD, 9, 220, y, "ACADEMIC PERIOD");
            text(content, REGULAR, 10, 220, y - 18, decision.getAcademicPeriodCode());
            text(content, BOLD, 9, 405, y, "PROGRAMME PERIOD");
            text(content, REGULAR, 10, 405, y - 18, Integer.toString(decision.getProgrammePeriodNumber()));

            y -= 52;
            text(content, BOLD, 8, PAGE_MARGIN, y, "MODULE");
            text(content, BOLD, 8, 304, y, "CREDITS");
            text(content, BOLD, 8, 365, y, "MARK");
            text(content, BOLD, 8, 419, y, "GRADE");
            text(content, BOLD, 8, 477, y, "OUTCOME");
            y -= 8;
            content.setStrokingColor(0f, 91f / 255f, 65f / 255f);
            content.moveTo(PAGE_MARGIN, y);
            content.lineTo(pageWidth - PAGE_MARGIN, y);
            content.stroke();

            for (PublishedResultProjection result : results) {
                y -= ROW_HEIGHT;
                content.setNonStrokingColor(28f / 255f, 37f / 255f, 42f / 255f);
                text(content, BOLD, 8, PAGE_MARGIN, y + 5, limited(result.getModuleCode(), 14));
                text(content, REGULAR, 8, 108, y + 5, limited(result.getModuleName(), 31));
                text(content, REGULAR, 8, 310, y + 5, result.getCreditValue().toPlainString());
                text(content, REGULAR, 8, 370, y + 5, result.getFinalMark().toPlainString() + "%");
                text(content, BOLD, 8, 428, y + 5, limited(result.getGrade(), 8));
                if (result.isPassing()) {
                    content.setNonStrokingColor(0f, 91f / 255f, 65f / 255f);
                } else {
                    content.setNonStrokingColor(158f / 255f, 52f / 255f, 56f / 255f);
                }
                text(content, BOLD, 8, 481, y + 5, result.isPassing() ? "PASS" : "FAIL");
                content.setStrokingColor(219f / 255f, 224f / 255f, 226f / 255f);
                content.moveTo(PAGE_MARGIN, y - 3);
                content.lineTo(pageWidth - PAGE_MARGIN, y - 3);
                content.stroke();
            }

            if (pageNumber == pageCount) {
                y -= 46;
                content.setNonStrokingColor(245f / 255f, 247f / 255f, 246f / 255f);
                content.addRect(PAGE_MARGIN, y - 48, pageWidth - (2 * PAGE_MARGIN), 66);
                content.fill();
                content.setNonStrokingColor(28f / 255f, 37f / 255f, 42f / 255f);
                text(content, BOLD, 9, PAGE_MARGIN + 12, y + 2, "OVERALL DECISION");
                text(content, BOLD, 13, PAGE_MARGIN + 12, y - 18,
                        limited(decision.getDecisionLabel(), 45));
                text(content, REGULAR, 9, 350, y + 2,
                        "Weighted average  " + decision.getWeightedAverage().toPlainString() + "%");
                text(content, REGULAR, 9, 350, y - 16,
                        "Credits passed  " + decision.getPassedCredits().toPlainString()
                                + " / " + decision.getAttemptedCredits().toPlainString());
                text(content, REGULAR, 8, PAGE_MARGIN, y - 76,
                        "Published " + DATE_FORMAT.format(decision.getPublishedAt())
                                + "  |  Decision " + limited(decision.getDecisionNumber(), 38));
            }

            content.setNonStrokingColor(87f / 255f, 96f / 255f, 101f / 255f);
            text(content, REGULAR, 7, PAGE_MARGIN, 28,
                    "System-generated official record. Verify against document number "
                            + limited(document.getDocumentNumber(), 42) + ".");
            text(content, REGULAR, 7, pageWidth - 84, 28, pageNumber + " / " + pageCount);
        }
    }

    private void text(
            PDPageContentStream content,
            PDType1Font font,
            float fontSize,
            float x,
            float y,
            String value) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(asWinAnsi(value));
        content.endText();
    }

    private String limited(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        String safeValue = value.trim();
        return safeValue.length() <= maximumLength
                ? safeValue
                : safeValue.substring(0, maximumLength - 1) + "…";
    }

    private String asWinAnsi(String value) {
        return value.replace('…', '.').replaceAll("[^\\x20-\\x7E]", "?");
    }

    public record RenderedPdf(byte[] content, int pageCount) {
    }
}

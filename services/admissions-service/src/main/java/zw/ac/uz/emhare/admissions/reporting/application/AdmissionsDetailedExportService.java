package zw.ac.uz.emhare.admissions.reporting.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsDetailedExportRow;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsPipelineReportRepository;

/** Generates application-level operational reports in portable formats. @author Tinashe K */
@Service
public class AdmissionsDetailedExportService {

    private static final String[] HEADERS = {
        "Application number", "Applicant number", "Applicant name", "Email", "Phone",
        "Intake code", "Intake name", "Application route code", "Application route",
        "Status", "Payment status", "Submitted at", "Total points", "Applicant category",
        "Gender", "Programme choices"
    };
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final PDFont PDF_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont PDF_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final AdmissionsPipelineReportRepository reportRepository;
    private final Clock clock;

    public AdmissionsDetailedExportService(AdmissionsPipelineReportRepository reportRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdmissionsDetailedExport export(
            AdmissionsPipelineReportQuery query,
            AdmissionsDetailedExportFormat format) {
        Instant generatedAt = Instant.now(clock);
        List<AdmissionsDetailedExportRow> rows = reportRepository.findDetailedExportRows(query);
        String stem = "admissions-applications-" + FILE_TIMESTAMP.withZone(clock.getZone()).format(generatedAt);
        return switch (format) {
            case CSV -> new AdmissionsDetailedExport(csv(rows), "text/csv;charset=UTF-8", stem + ".csv");
            case XLSX -> new AdmissionsDetailedExport(
                    xlsx(rows), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", stem + ".xlsx");
            case PDF -> new AdmissionsDetailedExport(pdf(rows, generatedAt), "application/pdf", stem + ".pdf");
        };
    }

    private static byte[] csv(List<AdmissionsDetailedExportRow> rows) {
        StringBuilder output = new StringBuilder("\uFEFF");
        appendCsvRow(output, List.of(HEADERS));
        rows.stream().map(AdmissionsDetailedExportService::values).forEach(values -> appendCsvRow(output, values));
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvRow(StringBuilder output, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) output.append(',');
            String safeValue = spreadsheetSafe(values.get(index));
            output.append('"').append(safeValue.replace("\"", "\"\"")).append('"');
        }
        output.append("\r\n");
    }

    private static byte[] xlsx(List<AdmissionsDetailedExportRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Applications");
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) {
                header.createCell(index).setCellValue(HEADERS[index]);
                header.getCell(index).setCellStyle(headerStyle);
            }
            int rowNumber = 1;
            for (AdmissionsDetailedExportRow exportRow : rows) {
                Row row = sheet.createRow(rowNumber++);
                List<String> values = values(exportRow);
                for (int index = 0; index < values.size(); index++) {
                    row.createCell(index).setCellValue(spreadsheetSafe(values.get(index)));
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowNumber - 1), 0, HEADERS.length - 1));
            for (int index = 0; index < HEADERS.length; index++) {
                sheet.setColumnWidth(index, Math.min(columnWidth(index), 255 * 256));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The Excel admissions report could not be generated.", exception);
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static int columnWidth(int index) {
        if (index == 2 || index == 6 || index == 8) return 28 * 256;
        if (index == 3) return 32 * 256;
        if (index == 15) return 72 * 256;
        return 20 * 256;
    }

    private static byte[] pdf(List<AdmissionsDetailedExportRow> rows, Instant generatedAt) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle("Detailed admissions application register");
            information.setAuthor("Tinashe K");
            information.setSubject("Operational admissions report");
            document.setDocumentInformation(information);
            PdfRegisterWriter writer = new PdfRegisterWriter(document);
            writer.title("Detailed admissions application register");
            writer.line("Generated " + DISPLAY_TIMESTAMP.withZone(ZoneId.of("Africa/Harare")).format(generatedAt)
                    + " | Applications: " + rows.size(), PDF_REGULAR, 8);
            writer.gap(8);
            if (rows.isEmpty()) {
                writer.line("No applications match the selected filters.", PDF_REGULAR, 10);
            } else {
                for (AdmissionsDetailedExportRow row : rows) writer.application(row);
            }
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The PDF admissions report could not be generated.", exception);
        }
    }

    private static List<String> values(AdmissionsDetailedExportRow row) {
        return List.of(
                text(row.applicationNumber()), text(row.applicantNumber()), text(row.applicantName()),
                text(row.primaryEmail()), text(row.primaryPhone()), text(row.intakeCode()), text(row.intakeName()),
                text(row.applicationTypeCode()), text(row.applicationTypeName()), text(row.applicationStatus()),
                text(row.paymentStatus()), instant(row.submittedAt()), decimal(row.calculatedTotalPoints()),
                text(row.applicantCategoryCode()), text(row.genderCode()), text(row.programmeChoices()));
    }

    private static String spreadsheetSafe(String value) {
        if (value.isEmpty()) return value;
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static final class PdfRegisterWriter {
        private static final float MARGIN = 42;
        private static final float LINE_HEIGHT = 11;
        private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - (2 * MARGIN);
        private final PDDocument document;
        private PDPageContentStream content;
        private float y;

        private PdfRegisterWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void title(String title) throws IOException {
            line(title, PDF_BOLD, 16);
        }

        private void application(AdmissionsDetailedExportRow row) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add(text(row.applicationNumber()) + " | " + text(row.applicationStatus()));
            lines.add(text(row.applicantNumber()) + " | " + text(row.applicantName()));
            lines.add("Contact: " + display(row.primaryEmail()) + " | " + display(row.primaryPhone()));
            lines.add("Intake: " + text(row.intakeCode()) + " - " + text(row.intakeName())
                    + " | Route: " + text(row.applicationTypeCode()) + " - " + text(row.applicationTypeName()));
            lines.add("Category: " + display(row.applicantCategoryCode()) + " | Gender: " + display(row.genderCode())
                    + " | Points: " + display(decimal(row.calculatedTotalPoints())));
            lines.add("Payment: " + text(row.paymentStatus()) + " | Submitted: " + display(instant(row.submittedAt())));
            lines.addAll(wrap("Programme choices: " + display(row.programmeChoices()), PDF_REGULAR, 8, CONTENT_WIDTH - 16));
            ensureSpace((lines.size() * LINE_HEIGHT) + 18);
            line(lines.getFirst(), PDF_BOLD, 10);
            for (int index = 1; index < lines.size(); index++) line("  " + lines.get(index), PDF_REGULAR, 8);
            gap(7);
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < MARGIN) newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void line(String value, PDFont font, float size) throws IOException {
            ensureSpace(LINE_HEIGHT);
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(MARGIN, y);
            content.showText(pdfSafe(value));
            content.endText();
            y -= LINE_HEIGHT;
        }

        private void gap(float height) {
            y -= height;
        }

        private void close() throws IOException {
            content.close();
        }

        private static List<String> wrap(String value, PDFont font, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : pdfSafe(value).split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size <= width) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            lines.add(current.toString());
            return List.copyOf(lines);
        }

        private static String display(String value) {
            return value == null || value.isBlank() ? "Not recorded" : value;
        }

        private static String pdfSafe(String value) {
            String normalized = text(value)
                    .replace('·', '|').replace('–', '-').replace('—', '-')
                    .replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"');
            StringBuilder safe = new StringBuilder(normalized.length());
            normalized.codePoints().forEach(codePoint -> {
                if (Character.isISOControl(codePoint)) {
                    safe.append(' ');
                } else if ((codePoint >= 32 && codePoint <= 126) || (codePoint >= 160 && codePoint <= 255)) {
                    safe.appendCodePoint(codePoint);
                } else {
                    safe.append('?');
                }
            });
            return safe.toString();
        }
    }
}

package zw.ac.uz.emhare.admissions.reporting.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Modern UZ-branded renderer for Admissions operational reports. @author Tinashe K */
final class AdmissionsOperationalReportPdfRenderer {

    private static final PDRectangle PAGE_SIZE = new PDRectangle(
            PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter GENERATED_TIME = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm z", Locale.ENGLISH)
            .withZone(ZoneId.of("Africa/Harare"));

    private static final Color UZ_GREEN = new Color(0x00, 0x66, 0x33);
    private static final Color UZ_GOLD = new Color(0xE0, 0xB5, 0x22);
    private static final Color DATA_BLUE = new Color(0x33, 0x5C, 0x81);
    private static final Color INK = new Color(0x1D, 0x29, 0x39);
    private static final Color MUTED = new Color(0x66, 0x70, 0x85);
    private static final Color BORDER = new Color(0xD0, 0xD5, 0xDD);
    private static final Color SURFACE = new Color(0xF8, 0xFA, 0xFC);
    private static final Color ACCENT_TINT = new Color(0xEC, 0xF2, 0xF7);
    private static final Color TABLE_HEADER = new Color(0xF2, 0xF4, 0xF7);
    private static final Color WHITE = Color.WHITE;

    private static final float MARGIN = 30;
    private static final float HEADER_HEIGHT = 58;
    private static final float FOOTER_HEIGHT = 30;
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;
    private static final Set<String> SUMMABLE_COLUMNS = Set.of(
            "applications", "applicants", "choices", "first", "second", "third",
            "female", "male", "otherGender", "offered", "accepted", "pending", "converted", "registered");

    private AdmissionsOperationalReportPdfRenderer() {
    }

    static byte[] render(AdmissionsOperationalReport report) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle(report.definition().title());
            information.setAuthor("Tinashe K");
            information.setSubject(report.definition().family() + " admissions report");
            information.setCreator("eMhare Admissions");
            document.setDocumentInformation(information);

            ReportLayout layout = new ReportLayout(document, report);
            layout.render();
            layout.addFooters();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static final class ReportLayout {
        private final PDDocument document;
        private final AdmissionsOperationalReport report;
        private final float[] columnWidths;
        private final float tableFontSize;
        private PDPage page;
        private PDPageContentStream content;
        private float cursorY;
        private int tableRowNumber;

        private ReportLayout(PDDocument document, AdmissionsOperationalReport report) {
            this.document = document;
            this.report = report;
            this.columnWidths = columnWidths(report.columns());
            this.tableFontSize = report.columns().size() <= 7 ? 7.2f
                    : report.columns().size() <= 11 ? 6.2f : 5.4f;
        }

        private void render() throws IOException {
            newPage(false);
            drawTitleBlock();
            drawMetrics();
            drawChart();
            drawNotes();
            drawTableSectionHeading();
            if (report.rows().isEmpty()) {
                drawEmptyState();
            } else {
                drawTableHeader();
                for (List<String> row : report.rows()) drawTableRow(row);
                drawTotalsRow();
            }
            content.close();
        }

        private void newPage(boolean continuation) throws IOException {
            if (content != null) content.close();
            page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            drawInstitutionHeader();
            cursorY = PAGE_SIZE.getHeight() - HEADER_HEIGHT - 18;
            if (continuation) {
                text("REPORT DETAIL", MARGIN, cursorY, BOLD, 7, UZ_GREEN);
                text(report.definition().title(), MARGIN, cursorY - 15, BOLD, 12, INK);
                text("Continued", PAGE_SIZE.getWidth() - MARGIN - 52, cursorY - 14, REGULAR, 7, MUTED);
                cursorY -= 34;
                drawTableHeader();
            }
        }

        private void drawInstitutionHeader() throws IOException {
            fillRect(0, PAGE_SIZE.getHeight() - HEADER_HEIGHT, PAGE_SIZE.getWidth(), HEADER_HEIGHT, WHITE);
            line(0, PAGE_SIZE.getHeight() - HEADER_HEIGHT, PAGE_SIZE.getWidth(),
                    PAGE_SIZE.getHeight() - HEADER_HEIGHT, BORDER, 0.7f);
            fillRect(MARGIN, PAGE_SIZE.getHeight() - HEADER_HEIGHT, 54, 2, UZ_GREEN);
            fillRect(MARGIN + 54, PAGE_SIZE.getHeight() - HEADER_HEIGHT, 22, 2, UZ_GOLD);
            float badgeX = MARGIN;
            float badgeY = PAGE_SIZE.getHeight() - 44;
            fillRect(badgeX, badgeY, 30, 30, UZ_GOLD);
            centeredText("UZ", badgeX, badgeY + 10, 30, BOLD, 11, UZ_GREEN);

            text("UNIVERSITY OF ZIMBABWE", badgeX + 40, PAGE_SIZE.getHeight() - 25, BOLD, 13, INK);
            text("ADMISSIONS & ENROLMENT", badgeX + 40, PAGE_SIZE.getHeight() - 40, BOLD, 7.4f, UZ_GREEN);
            float right = PAGE_SIZE.getWidth() - MARGIN;
            rightText("eMHARE", right, PAGE_SIZE.getHeight() - 25, BOLD, 10, INK);
            rightText("UNIVERSITY OPERATIONS", right, PAGE_SIZE.getHeight() - 40, REGULAR, 6.5f,
                    MUTED);
        }

        private void drawTitleBlock() throws IOException {
            text("OPERATIONAL REPORT", MARGIN, cursorY, BOLD, 7, UZ_GREEN);
            cursorY -= 21;
            for (String line : wrap(report.definition().title(), BOLD, 18, CONTENT_WIDTH - 180, 2)) {
                text(line, MARGIN, cursorY, BOLD, 18, INK);
                cursorY -= 20;
            }
            List<String> descriptionLines = wrap(report.definition().description(), REGULAR, 8.2f,
                    CONTENT_WIDTH - 180, 2);
            for (String line : descriptionLines) {
                text(line, MARGIN, cursorY, REGULAR, 8.2f, MUTED);
                cursorY -= 11;
            }

            float metaX = PAGE_SIZE.getWidth() - MARGIN - 168;
            float metaY = PAGE_SIZE.getHeight() - HEADER_HEIGHT - 34;
            fillRect(metaX, metaY - 39, 168, 48, SURFACE);
            text("GENERATED", metaX + 10, metaY - 2, BOLD, 6.2f, MUTED);
            text(GENERATED_TIME.format(report.generatedAt()), metaX + 10, metaY - 15, BOLD, 8.5f, INK);
            text(report.rows().size() + (report.rows().size() == 1 ? " report row" : " report rows"),
                    metaX + 10, metaY - 29, REGULAR, 7, MUTED);
            cursorY -= 4;
        }

        private void drawMetrics() throws IOException {
            if (report.metrics().isEmpty()) return;
            cursorY -= 9;
            float gap = 10;
            int count = Math.min(4, report.metrics().size());
            float width = (CONTENT_WIDTH - gap * (count - 1)) / count;
            float height = 48;
            for (int index = 0; index < count; index++) {
                AdmissionsOperationalReport.Metric metric = report.metrics().get(index);
                float x = MARGIN + index * (width + gap);
                fillRect(x, cursorY - height, width, height, SURFACE);
                fillRect(x, cursorY - height, 3, height, metricAccent(index));
                text(metric.label().toUpperCase(Locale.ROOT), x + 12, cursorY - 15, BOLD, 6.4f, MUTED);
                text(metric.value(), x + 12, cursorY - 36, BOLD, 17, INK);
            }
            cursorY -= height + 14;
        }

        private void drawChart() throws IOException {
            if (report.chart().isEmpty()) return;
            List<AdmissionsOperationalReport.ChartPoint> points = report.chart().stream().limit(6).toList();
            long maximum = Math.max(1, points.stream().mapToLong(AdmissionsOperationalReport.ChartPoint::value).max().orElse(1));
            float chartHeight = 27 + points.size() * 14;
            ensureSpace(chartHeight + 8, false);
            text("Visual summary", MARGIN, cursorY, BOLD, 9, INK);
            text(points.get(0).series(), MARGIN + 82, cursorY, REGULAR, 7, MUTED);
            cursorY -= 15;
            float labelWidth = 105;
            float valueWidth = 28;
            float barWidth = CONTENT_WIDTH - labelWidth - valueWidth - 10;
            for (AdmissionsOperationalReport.ChartPoint point : points) {
                text(ellipsize(point.label(), REGULAR, 7, labelWidth), MARGIN, cursorY, REGULAR, 7, MUTED);
                fillRect(MARGIN + labelWidth, cursorY - 1, barWidth, 6, ACCENT_TINT);
                fillRect(MARGIN + labelWidth, cursorY - 1,
                        Math.max(3, barWidth * point.value() / maximum), 6, DATA_BLUE);
                rightText(Long.toString(point.value()), PAGE_SIZE.getWidth() - MARGIN, cursorY, BOLD, 7, INK);
                cursorY -= 14;
            }
            cursorY -= 6;
        }

        private void drawNotes() throws IOException {
            if (report.notes().isEmpty()) return;
            String notes = String.join(" ", report.notes());
            List<String> lines = wrap(notes, REGULAR, 7.2f, CONTENT_WIDTH - 26, 3);
            float height = 23 + lines.size() * 9;
            ensureSpace(height + 8, false);
            fillRect(MARGIN, cursorY - height, CONTENT_WIDTH, height, SURFACE);
            fillRect(MARGIN, cursorY - height, 3, height, UZ_GOLD);
            text("COUNTING BASIS", MARGIN + 12, cursorY - 14, BOLD, 6.4f, INK);
            float y = cursorY - 27;
            for (String line : lines) {
                text(line, MARGIN + 12, y, REGULAR, 7.2f, INK);
                y -= 9;
            }
            cursorY -= height + 13;
        }

        private void drawTableSectionHeading() throws IOException {
            ensureSpace(32, false);
            text("Report detail", MARGIN, cursorY, BOLD, 10, INK);
            rightText(report.rows().size() + (report.rows().size() == 1 ? " row" : " rows"),
                    PAGE_SIZE.getWidth() - MARGIN, cursorY, REGULAR, 7, MUTED);
            cursorY -= 15;
        }

        private void drawTableHeader() throws IOException {
            if (report.columns().isEmpty()) return;
            float height = 27;
            fillRect(MARGIN, cursorY - height, CONTENT_WIDTH, height, TABLE_HEADER);
            line(MARGIN, cursorY, PAGE_SIZE.getWidth() - MARGIN, cursorY, BORDER, 0.7f);
            line(MARGIN, cursorY, MARGIN + 54, cursorY, UZ_GREEN, 1.2f);
            line(MARGIN, cursorY - height, PAGE_SIZE.getWidth() - MARGIN, cursorY - height, BORDER, 0.6f);
            float x = MARGIN;
            for (int index = 0; index < report.columns().size(); index++) {
                float width = columnWidths[index];
                String label = report.columns().get(index).label().toUpperCase(Locale.ROOT);
                float headerFontSize = fittedFontSize(label, BOLD, Math.min(6.2f, tableFontSize), width - 7, 4.2f);
                List<String> lines = wrap(report.columns().get(index).label().toUpperCase(Locale.ROOT),
                        BOLD, headerFontSize, width - 7, 3);
                float y = cursorY - 10;
                for (String line : lines) {
                    text(line, x + 3.5f, y, BOLD, headerFontSize, INK);
                    y -= headerFontSize + 1.6f;
                }
                x += width;
            }
            cursorY -= height;
        }

        private void drawTableRow(List<String> values) throws IOException {
            List<List<String>> wrappedCells = wrappedCells(values, tableFontSize);
            int maximumLines = wrappedCells.stream().mapToInt(List::size).max().orElse(1);
            float rowHeight = Math.max(21, 9 + maximumLines * (tableFontSize + 2));
            if (cursorY - rowHeight < FOOTER_HEIGHT + 8) newPage(true);
            Color background = tableRowNumber++ % 2 == 0 ? WHITE : SURFACE;
            fillRect(MARGIN, cursorY - rowHeight, CONTENT_WIDTH, rowHeight, background);
            line(MARGIN, cursorY - rowHeight, PAGE_SIZE.getWidth() - MARGIN, cursorY - rowHeight, BORDER, 0.45f);
            float x = MARGIN;
            for (int column = 0; column < report.columns().size(); column++) {
                float width = columnWidths[column];
                List<String> lines = wrappedCells.get(column);
                float y = cursorY - 11;
                for (String value : lines) {
                    if (isNumeric(value) && lines.size() == 1) {
                        rightText(value, x + width - 4, y, column == 0 ? BOLD : REGULAR, tableFontSize, INK);
                    } else {
                        text(value, x + 4, y, column == 0 ? BOLD : REGULAR, tableFontSize, INK);
                    }
                    y -= tableFontSize + 2;
                }
                if (column > 0) line(x, cursorY, x, cursorY - rowHeight, BORDER, 0.25f);
                x += width;
            }
            cursorY -= rowHeight;
        }

        private void drawTotalsRow() throws IOException {
            if (report.rows().size() < 2 || report.columns().isEmpty()) return;
            Map<Integer, Long> totals = new HashMap<>();
            for (int column = 0; column < report.columns().size(); column++) {
                if (!SUMMABLE_COLUMNS.contains(report.columns().get(column).key())) continue;
                long total = 0;
                boolean numeric = true;
                for (List<String> row : report.rows()) {
                    try {
                        total += Long.parseLong(row.get(column));
                    } catch (NumberFormatException exception) {
                        numeric = false;
                        break;
                    }
                }
                if (numeric) totals.put(column, total);
            }
            if (totals.isEmpty()) return;
            float height = 23;
            if (cursorY - height < FOOTER_HEIGHT + 8) newPage(true);
            fillRect(MARGIN, cursorY - height, CONTENT_WIDTH, height, SURFACE);
            line(MARGIN, cursorY, PAGE_SIZE.getWidth() - MARGIN, cursorY, BORDER, 0.8f);
            float x = MARGIN;
            for (int column = 0; column < report.columns().size(); column++) {
                float width = columnWidths[column];
                if (column == 0) text("TOTAL", x + 4, cursorY - 15, BOLD, tableFontSize, INK);
                if (totals.containsKey(column)) rightText(Long.toString(totals.get(column)), x + width - 4,
                        cursorY - 15, BOLD, tableFontSize, INK);
                x += width;
            }
            cursorY -= height;
        }

        private void drawEmptyState() throws IOException {
            float height = 88;
            fillRect(MARGIN, cursorY - height, CONTENT_WIDTH, height, SURFACE);
            fillRect(MARGIN, cursorY - height, 4, height, UZ_GREEN);
            fillRect(MARGIN + 18, cursorY - 53, 34, 34, ACCENT_TINT);
            centeredText("0", MARGIN + 18, cursorY - 42, 34, BOLD, 11, DATA_BLUE);
            text("No records found", MARGIN + 68, cursorY - 31, BOLD, 12, INK);
            text("No records match the selected filters. Adjust the report filters and generate it again.",
                    MARGIN + 68, cursorY - 49, REGULAR, 8, MUTED);
            cursorY -= height;
        }

        private void addFooters() throws IOException {
            int pageCount = document.getNumberOfPages();
            for (int index = 0; index < pageCount; index++) {
                PDPage footerPage = document.getPage(index);
                try (PDPageContentStream footer = new PDPageContentStream(document, footerPage,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    AdmissionsOperationalReportPdfRenderer.line(
                            footer, MARGIN, FOOTER_HEIGHT, PAGE_SIZE.getWidth() - MARGIN, FOOTER_HEIGHT,
                            BORDER, 0.6f);
                    AdmissionsOperationalReportPdfRenderer.text(
                            footer, "eMhare | Admissions & Enrolment", MARGIN, 16, BOLD, 6.5f, INK);
                    AdmissionsOperationalReportPdfRenderer.centeredText(
                            footer, "Internal operational report", MARGIN, 16, CONTENT_WIDTH,
                            REGULAR, 6.5f, MUTED);
                    AdmissionsOperationalReportPdfRenderer.rightText(
                            footer, "Page " + (index + 1) + " of " + pageCount,
                            PAGE_SIZE.getWidth() - MARGIN, 16, BOLD, 6.5f, MUTED);
                }
            }
        }

        private void ensureSpace(float requiredHeight, boolean repeatTableHeader) throws IOException {
            if (cursorY - requiredHeight >= FOOTER_HEIGHT + 8) return;
            newPage(repeatTableHeader);
        }

        private List<List<String>> wrappedCells(List<String> values, float fontSize) throws IOException {
            List<List<String>> cells = new ArrayList<>();
            for (int column = 0; column < report.columns().size(); column++) {
                String value = column < values.size() ? values.get(column) : "";
                cells.add(wrap(value, column == 0 ? BOLD : REGULAR, fontSize,
                        columnWidths[column] - 8, 5));
            }
            return cells;
        }

        private void text(String value, float x, float y, PDType1Font font, float size, Color color)
                throws IOException {
            AdmissionsOperationalReportPdfRenderer.text(content, value, x, y, font, size, color);
        }

        private void rightText(String value, float rightX, float y, PDType1Font font, float size, Color color)
                throws IOException {
            AdmissionsOperationalReportPdfRenderer.rightText(content, value, rightX, y, font, size, color);
        }

        private void centeredText(String value, float x, float y, float width,
                PDType1Font font, float size, Color color) throws IOException {
            AdmissionsOperationalReportPdfRenderer.centeredText(content, value, x, y, width, font, size, color);
        }

        private void fillRect(float x, float y, float width, float height, Color color) throws IOException {
            content.setNonStrokingColor(color);
            content.addRect(x, y, width, height);
            content.fill();
        }

        private Color metricAccent(int index) {
            return switch (index % 3) {
                case 0 -> UZ_GREEN;
                case 1 -> UZ_GOLD;
                default -> DATA_BLUE;
            };
        }

        private void line(float x1, float y1, float x2, float y2, Color color, float width) throws IOException {
            AdmissionsOperationalReportPdfRenderer.line(content, x1, y1, x2, y2, color, width);
        }
    }

    private static float[] columnWidths(List<AdmissionsOperationalReport.Column> columns) {
        if (columns.isEmpty()) return new float[0];
        float[] weights = new float[columns.size()];
        float totalWeight = 0;
        for (int index = 0; index < columns.size(); index++) {
            String key = columns.get(index).key();
            float weight = switch (key) {
                case "programme" -> 3.0f;
                case "academicUnit" -> 2.25f;
                case "applicant", "value" -> 2.0f;
                case "qualifications", "schools", "reason" -> 2.8f;
                case "applicationNumber", "applicantNumber", "fromIntake", "toIntake" -> 1.45f;
                case "scope", "dimension", "schedule", "movement", "register", "category" -> 1.55f;
                default -> 0.78f;
            };
            weights[index] = weight;
            totalWeight += weight;
        }
        float[] widths = new float[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            widths[index] = CONTENT_WIDTH * weights[index] / totalWeight;
        }
        return widths;
    }

    private static List<String> wrap(String value, PDType1Font font, float size, float maximumWidth, int maximumLines)
            throws IOException {
        String safe = pdfSafe(value).trim();
        if (safe.isEmpty()) return List.of("");
        List<String> tokens = new ArrayList<>();
        for (String word : safe.split("\\s+")) {
            if (textWidth(word, font, size) <= maximumWidth) {
                tokens.add(word);
            } else {
                StringBuilder fragment = new StringBuilder();
                for (char character : word.toCharArray()) {
                    String candidate = fragment.toString() + character;
                    if (!fragment.isEmpty() && textWidth(candidate, font, size) > maximumWidth) {
                        tokens.add(fragment.toString());
                        fragment.setLength(0);
                    }
                    fragment.append(character);
                }
                if (!fragment.isEmpty()) tokens.add(fragment.toString());
            }
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : tokens) {
            String candidate = current.isEmpty() ? token : current + " " + token;
            if (textWidth(candidate, font, size) <= maximumWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current.setLength(0);
                current.append(token);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        if (lines.size() <= maximumLines) return lines;
        List<String> limited = new ArrayList<>(lines.subList(0, maximumLines));
        String last = limited.get(maximumLines - 1);
        limited.set(maximumLines - 1, ellipsize(last + "...", font, size, maximumWidth));
        return limited;
    }

    private static String ellipsize(String value, PDType1Font font, float size, float maximumWidth) throws IOException {
        String safe = pdfSafe(value);
        if (textWidth(safe, font, size) <= maximumWidth) return safe;
        String suffix = "...";
        StringBuilder shortened = new StringBuilder(safe);
        while (!shortened.isEmpty() && textWidth(shortened + suffix, font, size) > maximumWidth) {
            shortened.deleteCharAt(shortened.length() - 1);
        }
        return shortened + suffix;
    }

    private static float fittedFontSize(
            String value, PDType1Font font, float preferredSize, float maximumWidth, float minimumSize)
            throws IOException {
        float size = preferredSize;
        for (String word : pdfSafe(value).split("\\s+")) {
            while (size > minimumSize && textWidth(word, font, size) > maximumWidth) size -= 0.2f;
        }
        return Math.max(size, minimumSize);
    }

    private static float textWidth(String value, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(pdfSafe(value)) / 1000 * size;
    }

    private static boolean isNumeric(String value) {
        return value.matches("-?\\d+(?:\\.\\d+)?");
    }

    private static void text(PDPageContentStream stream, String value, float x, float y,
            PDType1Font font, float size, Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(pdfSafe(value));
        stream.endText();
    }

    private static void rightText(PDPageContentStream stream, String value, float rightX, float y,
            PDType1Font font, float size, Color color) throws IOException {
        text(stream, value, rightX - textWidth(value, font, size), y, font, size, color);
    }

    private static void centeredText(PDPageContentStream stream, String value, float x, float y, float width,
            PDType1Font font, float size, Color color) throws IOException {
        text(stream, value, x + (width - textWidth(value, font, size)) / 2, y, font, size, color);
    }

    private static void line(PDPageContentStream stream, float x1, float y1, float x2, float y2,
            Color color, float width) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private static String pdfSafe(String value) {
        String normalized = value == null ? "" : value
                .replace('·', '|')
                .replace('–', '-')
                .replace('—', '-')
                .replace('’', '\'')
                .replace('“', '"')
                .replace('”', '"');
        StringBuilder safe = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> safe.appendCodePoint(
                codePoint >= 32 && codePoint <= 255 && !Character.isISOControl(codePoint) ? codePoint : '?'));
        return safe.toString();
    }
}

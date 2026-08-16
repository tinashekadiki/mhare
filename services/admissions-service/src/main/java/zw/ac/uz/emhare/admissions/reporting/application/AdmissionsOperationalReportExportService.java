package zw.ac.uz.emhare.admissions.reporting.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** Reusable governed XLSX/PDF renderer for operational report datasets. @author Tinashe K */
@Service
public class AdmissionsOperationalReportExportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final AdmissionsOperationalReportService reportService;
    private final Clock clock;

    public AdmissionsOperationalReportExportService(AdmissionsOperationalReportService reportService, Clock clock) {
        this.reportService = reportService;
        this.clock = clock;
    }

    public AdmissionsDetailedExport export(
            AdmissionsReportCode reportCode,
            AdmissionsPipelineReportQuery query,
            AdmissionsDetailedExportFormat format) {
        AdmissionsOperationalReport report = reportService.generate(reportCode, query);
        if (reportCode == AdmissionsReportCode.OFFER_LETTERS) {
            throw new IllegalArgumentException(
                    "Offer-letter PDFs and emails must be generated from the governed offer-letter workspace.");
        }
        String formatCode = format == AdmissionsDetailedExportFormat.XLSX ? "XLSX"
                : format == AdmissionsDetailedExportFormat.PDF ? "PDF" : "CSV";
        if (!report.definition().formats().contains(formatCode)) {
            throw new IllegalArgumentException(formatCode + " is not enabled for this report family.");
        }
        String stem = reportCode.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                + FILE_TIME.withZone(clock.getZone()).format(report.generatedAt());
        return switch (format) {
            case XLSX -> new AdmissionsDetailedExport(xlsx(report),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", stem + ".xlsx");
            case PDF -> new AdmissionsDetailedExport(pdf(report), "application/pdf", stem + ".pdf");
            case CSV -> throw new IllegalArgumentException("CSV is not enabled for this report family.");
        };
    }

    private static byte[] xlsx(AdmissionsOperationalReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row header = sheet.createRow(0);
            for (int index = 0; index < report.columns().size(); index++) {
                header.createCell(index).setCellValue(report.columns().get(index).label());
                header.getCell(index).setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (List<String> values : report.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(spreadsheetSafe(values.get(column)));
                }
            }
            sheet.createFreezePane(0, 1);
            if (!report.columns().isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, Math.max(0, rowIndex - 1), 0, report.columns().size() - 1));
            }
            for (int index = 0; index < report.columns().size(); index++) sheet.setColumnWidth(index, 24 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The Excel admissions report could not be generated.", exception);
        }
    }

    private static byte[] pdf(AdmissionsOperationalReport report) {
        try {
            return AdmissionsOperationalReportPdfRenderer.render(report);
        } catch (IOException exception) {
            throw new IllegalStateException("The PDF admissions report could not be generated.", exception);
        }
    }

    private static String spreadsheetSafe(String value) {
        if (value.isEmpty()) return "";
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }

}

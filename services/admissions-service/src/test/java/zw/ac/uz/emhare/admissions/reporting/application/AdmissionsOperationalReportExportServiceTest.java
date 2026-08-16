package zw.ac.uz.emhare.admissions.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class AdmissionsOperationalReportExportServiceTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-14T09:00:00Z");
    private final AdmissionsOperationalReportService reportService = mock(AdmissionsOperationalReportService.class);
    private AdmissionsOperationalReportExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new AdmissionsOperationalReportExportService(
                reportService, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));
    }

    @Test
    void generatesFormulaSafeExcelWorkbookWithGovernedFilename() throws Exception {
        when(reportService.generate(AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(report(AdmissionsReportCode.EXECUTIVE_STATISTICS, "=malicious", "2"));

        AdmissionsDetailedExport export = exportService.export(
                AdmissionsReportCode.EXECUTIVE_STATISTICS,
                AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.XLSX);

        assertThat(export.fileName()).isEqualTo("executive-statistics-20260814-090000.xlsx");
        assertThat(export.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.content()))) {
            assertThat(workbook.getSheet("Report").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("'=malicious");
            assertThat(workbook.getSheet("Report").getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    void generatesReadablePdfAndHandlesAnEmptyDataset() throws Exception {
        when(reportService.generate(AdmissionsReportCode.ADMISSIONS_ANALYSIS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(report(AdmissionsReportCode.ADMISSIONS_ANALYSIS));

        AdmissionsDetailedExport export = exportService.export(
                AdmissionsReportCode.ADMISSIONS_ANALYSIS,
                AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.PDF);

        assertThat(export.content()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var document = Loader.loadPDF(export.content())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getDocumentInformation().getAuthor()).isEqualTo("Tinashe K");
            assertThat(document.getPage(0).getMediaBox().getWidth())
                    .isGreaterThan(document.getPage(0).getMediaBox().getHeight());
            assertThat(new PDFTextStripper().getText(document))
                    .contains("UNIVERSITY OF ZIMBABWE")
                    .contains("ADMISSIONS & ENROLMENT")
                    .contains("OPERATIONAL REPORT")
                    .contains("GENERATED")
                    .contains("14 Aug 2026, 11:00 CAT")
                    .contains("APPLICATIONS")
                    .contains("APPLICANTS")
                    .contains("REPORT ROWS")
                    .contains("No records match the selected filters.")
                    .contains("Page 1 of 1");
        }
    }

    @Test
    void presentsDemandAsAVisualSummaryAndStructuredTable() throws Exception {
        AdmissionsReportDefinition definition = new AdmissionsReportCatalogueService()
                .require(AdmissionsReportCode.APPLICATION_DEMAND);
        AdmissionsOperationalReport demand = new AdmissionsOperationalReport(
                definition,
                GENERATED_AT,
                List.of(
                        new AdmissionsOperationalReport.Metric("Applications", "12"),
                        new AdmissionsOperationalReport.Metric("Applicants", "10"),
                        new AdmissionsOperationalReport.Metric("Report rows", "2")),
                List.of(
                        new AdmissionsOperationalReport.Column("programme", "Programme"),
                        new AdmissionsOperationalReport.Column("academicUnit", "Academic unit"),
                        new AdmissionsOperationalReport.Column("applications", "Applications"),
                        new AdmissionsOperationalReport.Column("applicants", "Applicants"),
                        new AdmissionsOperationalReport.Column("choices", "Choices"),
                        new AdmissionsOperationalReport.Column("first", "1st choice"),
                        new AdmissionsOperationalReport.Column("second", "2nd choice"),
                        new AdmissionsOperationalReport.Column("third", "3rd choice"),
                        new AdmissionsOperationalReport.Column("offered", "Offered"),
                        new AdmissionsOperationalReport.Column("accepted", "Accepted")),
                List.of(
                        List.of("HACCN | Bachelor of Accounting Honours", "Accounting Department",
                                "6", "5", "6", "4", "2", "0", "3", "2"),
                        List.of("HCS | Bachelor of Science Honours Degree in Computer Science",
                                "Computer Science Department", "6", "5", "6", "5", "1", "0", "4", "3")),
                List.of(
                        new AdmissionsOperationalReport.ChartPoint("HCS", 6, "Programme choices"),
                        new AdmissionsOperationalReport.ChartPoint("HACCN", 6, "Programme choices")),
                List.of("Applications and applicants are distinct. Choice counts include every ranked Programme choice."));
        when(reportService.generate(AdmissionsReportCode.APPLICATION_DEMAND, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(demand);

        AdmissionsDetailedExport export = exportService.export(
                AdmissionsReportCode.APPLICATION_DEMAND,
                AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.PDF);

        try (var document = Loader.loadPDF(export.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("Programme and academic-unit demand")
                    .contains("Visual summary")
                    .contains("Report detail")
                    .contains("COUNTING BASIS")
                    .contains("HACCN | Bachelor of Accounting Honours")
                    .contains("Computer Science Department");

            BufferedImage renderedPage = new PDFRenderer(document).renderImageWithDPI(0, 72);
            Color mastheadBackground = new Color(renderedPage.getRGB(renderedPage.getWidth() / 2, 20));
            assertThat(mastheadBackground.getRed()).isGreaterThan(240);
            assertThat(mastheadBackground.getGreen()).isGreaterThan(240);
            assertThat(mastheadBackground.getBlue()).isGreaterThan(240);
            assertThat(darkGreenPixelRatio(renderedPage)).isLessThan(0.01);
        }
    }

    @Test
    void generatesExcelAndPdfForEveryAnalyticalReportFamily() {
        for (AdmissionsReportCode reportCode : List.of(
                AdmissionsReportCode.APPLICATION_DEMAND,
                AdmissionsReportCode.EXECUTIVE_STATISTICS,
                AdmissionsReportCode.APPLICANT_REGISTERS,
                AdmissionsReportCode.SPECIAL_CATEGORY_REGISTERS,
                AdmissionsReportCode.SELECTION_SCHEDULES,
                AdmissionsReportCode.INTAKE_MOVEMENTS,
                AdmissionsReportCode.ADMISSIONS_ANALYSIS)) {
            when(reportService.generate(reportCode, AdmissionsPipelineReportQuery.empty()))
                    .thenReturn(report(reportCode, "Report row", "1"));

            AdmissionsDetailedExport spreadsheet = exportService.export(
                    reportCode, AdmissionsPipelineReportQuery.empty(), AdmissionsDetailedExportFormat.XLSX);
            AdmissionsDetailedExport document = exportService.export(
                    reportCode, AdmissionsPipelineReportQuery.empty(), AdmissionsDetailedExportFormat.PDF);

            assertThat(spreadsheet.fileName()).endsWith(".xlsx");
            assertThat(spreadsheet.content()).isNotEmpty();
            assertThat(document.fileName()).endsWith(".pdf");
            assertThat(document.content()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        }
    }

    private static double darkGreenPixelRatio(BufferedImage image) {
        long darkGreenPixels = 0;
        long totalPixels = (long) image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                if (pixel.getRed() < 60
                        && pixel.getGreen() >= 70
                        && pixel.getGreen() > pixel.getRed() * 1.8
                        && pixel.getGreen() > pixel.getBlue() * 1.4) {
                    darkGreenPixels++;
                }
            }
        }
        return (double) darkGreenPixels / totalPixels;
    }

    @Test
    void keepsOfferLetterPdfAndEmailInsideTheGovernedOfferWorkspace() {
        when(reportService.generate(AdmissionsReportCode.OFFER_LETTERS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(report(AdmissionsReportCode.OFFER_LETTERS, "OFF-001", "Published"));

        assertThatThrownBy(() -> exportService.export(
                AdmissionsReportCode.OFFER_LETTERS,
                AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("governed offer-letter workspace");
    }

    @Test
    void neutralizesEverySpreadsheetFormulaPrefixAndSupportsAnEmptyColumnSet() throws Exception {
        AdmissionsReportDefinition definition = new AdmissionsReportDefinition(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, "Executive", "Executive", "Test",
                List.of("XLSX"), List.of());
        AdmissionsOperationalReport prefixed = new AdmissionsOperationalReport(
                definition, GENERATED_AT, List.of(),
                List.of(
                        new AdmissionsOperationalReport.Column("equals", "Equals"),
                        new AdmissionsOperationalReport.Column("plus", "Plus"),
                        new AdmissionsOperationalReport.Column("minus", "Minus"),
                        new AdmissionsOperationalReport.Column("at", "At"),
                        new AdmissionsOperationalReport.Column("tab", "Tab"),
                        new AdmissionsOperationalReport.Column("return", "Return"),
                        new AdmissionsOperationalReport.Column("empty", "Empty"),
                        new AdmissionsOperationalReport.Column("plain", "Plain")),
                List.of(List.of("=1", "+1", "-1", "@name", "\tvalue", "\rvalue", "", "safe")),
                List.of(), List.of());
        when(reportService.generate(AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(prefixed);

        AdmissionsDetailedExport prefixedExport = exportService.export(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.XLSX);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(prefixedExport.content()))) {
            var row = workbook.getSheet("Report").getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("'=1");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("'+1");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("'-1");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("'@name");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("'\tvalue");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("'\rvalue");
            assertThat(row.getCell(6).getStringCellValue()).isEmpty();
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("safe");
        }

        AdmissionsOperationalReport noColumns = new AdmissionsOperationalReport(
                definition, GENERATED_AT, List.of(), List.of(), List.of(), List.of(), List.of());
        when(reportService.generate(AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(noColumns);
        assertThat(exportService.export(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.XLSX).content()).isNotEmpty();
    }

    @Test
    void paginatesAndWrapsLongPdfRowsWhileNormalizingUnsupportedGlyphs() throws Exception {
        AdmissionsReportDefinition definition = new AdmissionsReportDefinition(
                AdmissionsReportCode.ADMISSIONS_ANALYSIS, "Analysis", "Admissions — analysis", "Test",
                List.of("PDF"), List.of());
        List<List<String>> rows = new ArrayList<>();
        String longValue = "A-very-long-unbroken-value-that-exceeds-the-printable-width-".repeat(4)
                + " · en–dash em—dash control\u0001 emoji-📈";
        for (int index = 0; index < 90; index++) rows.add(List.of("Row " + index, longValue));
        AdmissionsOperationalReport report = new AdmissionsOperationalReport(
                definition, GENERATED_AT, List.of(),
                List.of(new AdmissionsOperationalReport.Column("row", "Row"),
                        new AdmissionsOperationalReport.Column("detail", "Detail")),
                rows, List.of(), List.of());
        when(reportService.generate(AdmissionsReportCode.ADMISSIONS_ANALYSIS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(report);

        AdmissionsDetailedExport export = exportService.export(
                AdmissionsReportCode.ADMISSIONS_ANALYSIS, AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.PDF);

        try (var document = Loader.loadPDF(export.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Admissions — analysis");
            assertThat(document.getPages()).allSatisfy(page ->
                    assertThat(page.getMediaBox().getWidth()).isGreaterThan(page.getMediaBox().getHeight()));
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("UNIVERSITY OF ZIMBABWE")
                    .contains("REPORT DETAIL")
                    .contains("Continued")
                    .contains("Page 1 of ")
                    .contains("Page " + document.getNumberOfPages() + " of " + document.getNumberOfPages());
        }
    }

    @Test
    void rejectsCsvEvenWhenAReportDefinitionAccidentallyAdvertisesIt() {
        AdmissionsReportDefinition definition = new AdmissionsReportDefinition(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, "Executive", "Executive", "Test",
                List.of("CSV"), List.of());
        when(reportService.generate(AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty()))
                .thenReturn(new AdmissionsOperationalReport(
                        definition, GENERATED_AT, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> exportService.export(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty(),
                AdmissionsDetailedExportFormat.CSV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV is not enabled for this report family.");
    }

    private static AdmissionsOperationalReport report(AdmissionsReportCode code, String... values) {
        AdmissionsReportDefinition definition = new AdmissionsReportCatalogueService().require(code);
        return new AdmissionsOperationalReport(
                definition,
                GENERATED_AT,
                List.of(
                        new AdmissionsOperationalReport.Metric("Applications", values.length == 0 ? "0" : "1"),
                        new AdmissionsOperationalReport.Metric("Applicants", values.length == 0 ? "0" : "1"),
                        new AdmissionsOperationalReport.Metric("Report rows", values.length == 0 ? "0" : "1")),
                List.of(
                        new AdmissionsOperationalReport.Column("name", "Name"),
                        new AdmissionsOperationalReport.Column("count", "Count")),
                values.length == 0 ? List.of() : List.of(List.of(values)),
                List.of(),
                List.of());
    }
}

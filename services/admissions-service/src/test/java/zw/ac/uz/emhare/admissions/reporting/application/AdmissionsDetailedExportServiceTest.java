package zw.ac.uz.emhare.admissions.reporting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsDetailedExportRow;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsPipelineReportRepository;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsDetailedExportServiceTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-14T08:30:00Z");

    @Mock
    private AdmissionsPipelineReportRepository reportRepository;

    @Test
    void export_shouldCreateAnExcelCompatibleCsvWithoutFormulaInjection() {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        when(reportRepository.findDetailedExportRows(query)).thenReturn(List.of(row("=A123456")));
        AdmissionsDetailedExportService service = service();

        AdmissionsDetailedExport export = service.export(query, AdmissionsDetailedExportFormat.CSV);

        String csv = new String(export.content(), StandardCharsets.UTF_8);
        assertEquals("text/csv;charset=UTF-8", export.contentType());
        assertEquals("admissions-applications-20260814-103000.csv", export.fileName());
        assertTrue(csv.startsWith("\uFEFF\"Application number\",\"Applicant number\",\"Applicant name\""));
        assertTrue(csv.contains("'=A123456"));
        assertTrue(csv.contains("\"Doe, Jane\""));
        assertTrue(csv.contains("1. HCS - Computer Science | 2. HACC - Accountancy"));
    }

    @Test
    void export_shouldCreateARealXlsxWorkbookWithTheDetailedRegister() throws Exception {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        when(reportRepository.findDetailedExportRows(query)).thenReturn(List.of(row("A123456")));
        AdmissionsDetailedExportService service = service();

        AdmissionsDetailedExport export = service.export(query, AdmissionsDetailedExportFormat.XLSX);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", export.contentType());
        assertEquals("admissions-applications-20260814-103000.xlsx", export.fileName());
        assertEquals("PK", new String(export.content(), 0, 2, StandardCharsets.US_ASCII));
        String sheetXml = zipEntry(export.content(), "xl/worksheets/sheet1.xml");
        String sharedStrings = zipEntry(export.content(), "xl/sharedStrings.xml");
        assertTrue(sheetXml.contains("<autoFilter"));
        assertTrue(sharedStrings.contains("Application number"));
        assertTrue(sharedStrings.contains("Doe, Jane"));
        assertTrue(sharedStrings.contains("1. HCS - Computer Science | 2. HACC - Accountancy"));
    }

    @Test
    void export_shouldCreateAPaginatedPdfRegister() {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        when(reportRepository.findDetailedExportRows(query)).thenReturn(List.of(row("A123456")));
        AdmissionsDetailedExportService service = service();

        AdmissionsDetailedExport export = service.export(query, AdmissionsDetailedExportFormat.PDF);

        assertEquals("application/pdf", export.contentType());
        assertEquals("admissions-applications-20260814-103000.pdf", export.fileName());
        assertEquals("%PDF", new String(export.content(), 0, 4, StandardCharsets.US_ASCII));
        assertTrue(export.content().length > 500);
    }

    @Test
    void format_shouldAcceptCaseInsensitiveValuesAndRejectUnsupportedFormats() {
        assertEquals(AdmissionsDetailedExportFormat.XLSX, AdmissionsDetailedExportFormat.from(" xLsX "));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> AdmissionsDetailedExportFormat.from("xml"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> AdmissionsDetailedExportFormat.from(null));
    }

    @Test
    void export_shouldGenerateEveryFormatForAnEmptyResult() {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        when(reportRepository.findDetailedExportRows(query)).thenReturn(List.of());
        AdmissionsDetailedExportService service = service();

        AdmissionsDetailedExport csv = service.export(query, AdmissionsDetailedExportFormat.CSV);
        AdmissionsDetailedExport xlsx = service.export(query, AdmissionsDetailedExportFormat.XLSX);
        AdmissionsDetailedExport pdf = service.export(query, AdmissionsDetailedExportFormat.PDF);

        assertEquals(1, new String(csv.content(), StandardCharsets.UTF_8).lines().count());
        assertEquals("PK", new String(xlsx.content(), 0, 2, StandardCharsets.US_ASCII));
        assertEquals("%PDF", new String(pdf.content(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void export_shouldRepresentMissingValuesAndProtectEverySpreadsheetFormulaPrefix() {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        AdmissionsDetailedExportRow sparseRow = new AdmissionsDetailedExportRow(
                UUID.randomUUID(), "+APP", null, "DRAFT", "PENDING", null, null, null, null,
                "-A123", "@Applicant 📄", "\tmail@example.test", "\r077", null, null, null, null);
        when(reportRepository.findDetailedExportRows(query)).thenReturn(List.of(sparseRow));
        AdmissionsDetailedExportService service = service();

        String csv = new String(service.export(query, AdmissionsDetailedExportFormat.CSV).content(),
                StandardCharsets.UTF_8);
        AdmissionsDetailedExport pdf = service.export(query, AdmissionsDetailedExportFormat.PDF);

        assertTrue(csv.contains("'+APP"));
        assertTrue(csv.contains("'-A123"));
        assertTrue(csv.contains("'@Applicant"));
        assertTrue(csv.contains("'\tmail@example.test"));
        assertTrue(csv.contains("'\r077"));
        assertEquals("%PDF", new String(pdf.content(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void pdfExport_shouldAddPagesForLargeApplicationRegisters() throws Exception {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        List<AdmissionsDetailedExportRow> rows = java.util.stream.IntStream.range(0, 60)
                .mapToObj(index -> row("A" + String.format("%06d", index)))
                .toList();
        when(reportRepository.findDetailedExportRows(query)).thenReturn(rows);

        byte[] content = service().export(query, AdmissionsDetailedExportFormat.PDF).content();

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(content)) {
            assertTrue(document.getNumberOfPages() > 1);
        }
    }

    private AdmissionsDetailedExportService service() {
        return new AdmissionsDetailedExportService(
                reportRepository, Clock.fixed(GENERATED_AT, ZoneOffset.ofHours(2)));
    }

    private static AdmissionsDetailedExportRow row(String applicantNumber) {
        return new AdmissionsDetailedExportRow(
                UUID.randomUUID(), "APP-2026-0001", Instant.parse("2026-08-13T10:15:00Z"), "UNDER_REVIEW",
                "PAID", "AUG-2026", "August 2026", "UNDERGRAD", "Undergraduate",
                applicantNumber, "Doe, Jane", "jane@example.test", "+263771234567", "LOCAL", "FEMALE",
                new java.math.BigDecimal("14.00"),
                "1. HCS - Computer Science | 2. HACC - Accountancy");
    }

    private static String zipEntry(byte[] content, String requestedName) throws Exception {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (requestedName.equals(entry.getName())) {
                    return new String(zipInput.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("Missing XLSX entry " + requestedName);
    }
}

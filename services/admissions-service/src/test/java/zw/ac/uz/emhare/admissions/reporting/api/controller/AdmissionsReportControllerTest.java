package zw.ac.uz.emhare.admissions.reporting.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import zw.ac.uz.emhare.admissions.reporting.api.model.AdmissionsPipelineReportResponse;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsDetailedExport;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsDetailedExportFormat;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsDetailedExportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineFilterOptions;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReport;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportQuery;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsReportCatalogueService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReportExportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReport;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsReportCode;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsReportDefinition;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsReportControllerTest {

    @Mock
    private AdmissionsPipelineReportService pipelineReportService;

    @Mock
    private AdmissionsDetailedExportService detailedExportService;

    @Mock
    private AdmissionsReportCatalogueService reportCatalogueService;

    @Mock
    private AdmissionsOperationalReportService operationalReportService;

    @Mock
    private AdmissionsOperationalReportExportService operationalReportExportService;

    @Test
    void pipelineSummary_shouldMapFiltersAndEveryPublicReportingDimension() {
        UUID intakeId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID applicationTypeId = UUID.randomUUID();
        AdmissionsPipelineReport.DimensionCount dimension =
                new AdmissionsPipelineReport.DimensionCount("LOCAL", 2);
        AdmissionsPipelineReport.RankedChoiceCount rank =
                new AdmissionsPipelineReport.RankedChoiceCount(1, 2, 2);
        AdmissionsPipelineReport report = new AdmissionsPipelineReport(
                Instant.parse("2026-08-13T16:00:00Z"),
                2,
                2,
                List.of(dimension),
                List.of(dimension),
                List.of(dimension),
                List.of(dimension),
                List.of(rank),
                List.of(new AdmissionsPipelineReport.IntakeStatistic(
                        intakeId, "AUG-2026", "August 2026", 2, 2,
                        List.of(dimension), List.of(dimension), List.of(dimension), List.of(rank))),
                List.of(new AdmissionsPipelineReport.ProgrammeStatistic(
                        programmeId, "HCS", "Computer Science", "Science Faculty", 2, 2, 2,
                        List.of(dimension), List.of(dimension), List.of(dimension), List.of(rank))),
                new AdmissionsPipelineFilterOptions(
                        List.of(new AdmissionsPipelineReport.FilterOption(intakeId.toString(), "AUG-2026", "August 2026")),
                        List.of(new AdmissionsPipelineReport.FilterOption(applicationTypeId.toString(), "UNDERGRAD", "Undergraduate")),
                        List.of(new AdmissionsPipelineReport.FilterOption(programmeId.toString(), "HCS", "Computer Science")),
                        List.of(new AdmissionsPipelineReport.FilterOption("LOCAL", "LOCAL", "Local")),
                        List.of(new AdmissionsPipelineReport.FilterOption("FEMALE", "FEMALE", "Female"))));
        when(pipelineReportService.generate(org.mockito.ArgumentMatchers.any())).thenReturn(report);
        AdmissionsReportController controller = new AdmissionsReportController(
                pipelineReportService, detailedExportService, reportCatalogueService,
                operationalReportService, operationalReportExportService);

        AdmissionsPipelineReportResponse response = controller.pipelineSummary(
                intakeId, programmeId, applicationTypeId, " local ", " female ");

        assertEquals(2, response.totalApplications());
        assertEquals("AUG-2026", response.intakeStatistics().getFirst().intakeCode());
        assertEquals("HCS", response.programmeStatistics().getFirst().programmeCode());
        assertEquals("LOCAL", response.filterOptions().categories().getFirst().code());
        assertEquals(2, response.rankedChoiceCounts().getFirst().choices());
        ArgumentCaptor<AdmissionsPipelineReportQuery> queryCaptor =
                ArgumentCaptor.forClass(AdmissionsPipelineReportQuery.class);
        verify(pipelineReportService).generate(queryCaptor.capture());
        assertEquals(intakeId, queryCaptor.getValue().intakeId());
        assertEquals(programmeId, queryCaptor.getValue().programmeId());
        assertEquals(applicationTypeId, queryCaptor.getValue().applicationTypeId());
        assertEquals("LOCAL", queryCaptor.getValue().categoryCode());
        assertEquals("FEMALE", queryCaptor.getValue().genderCode());
    }

    @Test
    void detailedExport_shouldReturnAnAttachmentAndApplyTheSameFiltersAsTheSummary() {
        UUID intakeId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        byte[] content = "export".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(detailedExportService.export(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AdmissionsDetailedExportFormat.XLSX)))
                .thenReturn(new AdmissionsDetailedExport(
                        content,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "admissions-applications.xlsx"));
        AdmissionsReportController controller = new AdmissionsReportController(
                pipelineReportService, detailedExportService, reportCatalogueService,
                operationalReportService, operationalReportExportService);

        ResponseEntity<byte[]> response = controller.detailedExport(
                intakeId, programmeId, null, " local ", " female ", "xlsx");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(content, response.getBody());
        assertEquals("attachment; filename=\"admissions-applications.xlsx\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        ArgumentCaptor<AdmissionsPipelineReportQuery> queryCaptor =
                ArgumentCaptor.forClass(AdmissionsPipelineReportQuery.class);
        verify(detailedExportService).export(
                queryCaptor.capture(), org.mockito.ArgumentMatchers.eq(AdmissionsDetailedExportFormat.XLSX));
        assertEquals(intakeId, queryCaptor.getValue().intakeId());
        assertEquals(programmeId, queryCaptor.getValue().programmeId());
        assertEquals("LOCAL", queryCaptor.getValue().categoryCode());
        assertEquals("FEMALE", queryCaptor.getValue().genderCode());
    }

    @Test
    void catalogueAndOperationalReportExposeTheGovernedFamilyContract() {
        AdmissionsReportDefinition definition = new AdmissionsReportDefinition(
                AdmissionsReportCode.APPLICATION_DEMAND, "Demand", "Demand", "Description",
                List.of("SCREEN", "PDF"), List.of("Programme demand"));
        AdmissionsOperationalReport operationalReport = new AdmissionsOperationalReport(
                definition, Instant.parse("2026-08-14T09:00:00Z"), List.of(), List.of(), List.of(), List.of(), List.of());
        when(reportCatalogueService.catalogue()).thenReturn(List.of(definition));
        when(operationalReportService.generate(
                org.mockito.ArgumentMatchers.eq(AdmissionsReportCode.APPLICATION_DEMAND),
                org.mockito.ArgumentMatchers.any())).thenReturn(operationalReport);
        AdmissionsReportController controller = new AdmissionsReportController(
                pipelineReportService, detailedExportService, reportCatalogueService,
                operationalReportService, operationalReportExportService);

        assertEquals(List.of(definition), controller.catalogue());
        assertEquals(operationalReport, controller.operationalReport(
                AdmissionsReportCode.APPLICATION_DEMAND, null, null, null, " local ", " female "));
    }

    @Test
    void operationalExportReturnsNoStoreAttachmentWithNormalizedFilters() {
        byte[] content = "%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(operationalReportExportService.export(
                org.mockito.ArgumentMatchers.eq(AdmissionsReportCode.ADMISSIONS_ANALYSIS),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AdmissionsDetailedExportFormat.PDF)))
                .thenReturn(new AdmissionsDetailedExport(content, "application/pdf", "analysis.pdf"));
        AdmissionsReportController controller = new AdmissionsReportController(
                pipelineReportService, detailedExportService, reportCatalogueService,
                operationalReportService, operationalReportExportService);

        ResponseEntity<byte[]> response = controller.operationalExport(
                AdmissionsReportCode.ADMISSIONS_ANALYSIS, null, null, null, " local ", " female ", "pdf");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(content, response.getBody());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"analysis.pdf\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }
}

package zw.ac.uz.emhare.admissions.reporting.api.controller;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.reporting.api.model.AdmissionsPipelineReportResponse;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsDetailedExport;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsDetailedExportFormat;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsDetailedExportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportQuery;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsReportCatalogueService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsReportDefinition;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsReportCode;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReport;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReportService;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReportExportService;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

/** Admissions operational reporting endpoints. @author Tinashe K */
@Validated
@RestController
@RequestMapping("/api/admissions/reports")
public class AdmissionsReportController {

    private final AdmissionsPipelineReportService pipelineReportService;
    private final AdmissionsDetailedExportService detailedExportService;
    private final AdmissionsReportCatalogueService reportCatalogueService;
    private final AdmissionsOperationalReportService operationalReportService;
    private final AdmissionsOperationalReportExportService operationalReportExportService;

    public AdmissionsReportController(
            AdmissionsPipelineReportService pipelineReportService,
            AdmissionsDetailedExportService detailedExportService,
            AdmissionsReportCatalogueService reportCatalogueService,
            AdmissionsOperationalReportService operationalReportService,
            AdmissionsOperationalReportExportService operationalReportExportService) {
        this.pipelineReportService = pipelineReportService;
        this.detailedExportService = detailedExportService;
        this.reportCatalogueService = reportCatalogueService;
        this.operationalReportService = operationalReportService;
        this.operationalReportExportService = operationalReportExportService;
    }

    @GetMapping("/catalogue")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public List<AdmissionsReportDefinition> catalogue() {
        return reportCatalogueService.catalogue();
    }

    @GetMapping("/{reportCode}")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public AdmissionsOperationalReport operationalReport(
            @PathVariable AdmissionsReportCode reportCode,
            @RequestParam(name = "intakeId", required = false) UUID intakeId,
            @RequestParam(name = "programmeId", required = false) UUID programmeId,
            @RequestParam(name = "applicationTypeId", required = false) UUID applicationTypeId,
            @RequestParam(name = "categoryCode", required = false) @Size(max = 30) String categoryCode,
            @RequestParam(name = "genderCode", required = false) @Size(max = 30) String genderCode) {
        return operationalReportService.generate(reportCode, AdmissionsPipelineReportQuery.of(
                intakeId, programmeId, applicationTypeId, categoryCode, genderCode));
    }

    @GetMapping("/{reportCode}/export")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public ResponseEntity<byte[]> operationalExport(
            @PathVariable AdmissionsReportCode reportCode,
            @RequestParam(name = "intakeId", required = false) UUID intakeId,
            @RequestParam(name = "programmeId", required = false) UUID programmeId,
            @RequestParam(name = "applicationTypeId", required = false) UUID applicationTypeId,
            @RequestParam(name = "categoryCode", required = false) @Size(max = 30) String categoryCode,
            @RequestParam(name = "genderCode", required = false) @Size(max = 30) String genderCode,
            @RequestParam(name = "format") @Size(max = 10) String format) {
        AdmissionsDetailedExport export = operationalReportExportService.export(reportCode,
                AdmissionsPipelineReportQuery.of(intakeId, programmeId, applicationTypeId, categoryCode, genderCode),
                AdmissionsDetailedExportFormat.from(format));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.fileName() + "\"")
                .cacheControl(CacheControl.noStore()).body(export.content());
    }

    @GetMapping("/pipeline-summary")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public AdmissionsPipelineReportResponse pipelineSummary(
            @RequestParam(name = "intakeId", required = false) UUID intakeId,
            @RequestParam(name = "programmeId", required = false) UUID programmeId,
            @RequestParam(name = "applicationTypeId", required = false) UUID applicationTypeId,
            @RequestParam(name = "categoryCode", required = false) @Size(max = 30) String categoryCode,
            @RequestParam(name = "genderCode", required = false) @Size(max = 30) String genderCode) {
        return AdmissionsPipelineReportResponse.from(pipelineReportService.generate(
                AdmissionsPipelineReportQuery.of(
                        intakeId, programmeId, applicationTypeId, categoryCode, genderCode)));
    }

    @GetMapping("/detailed-export")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public ResponseEntity<byte[]> detailedExport(
            @RequestParam(name = "intakeId", required = false) UUID intakeId,
            @RequestParam(name = "programmeId", required = false) UUID programmeId,
            @RequestParam(name = "applicationTypeId", required = false) UUID applicationTypeId,
            @RequestParam(name = "categoryCode", required = false) @Size(max = 30) String categoryCode,
            @RequestParam(name = "genderCode", required = false) @Size(max = 30) String genderCode,
            @RequestParam(name = "format") @Size(max = 10) String format) {
        AdmissionsDetailedExport export = detailedExportService.export(
                AdmissionsPipelineReportQuery.of(
                        intakeId, programmeId, applicationTypeId, categoryCode, genderCode),
                AdmissionsDetailedExportFormat.from(format));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.fileName() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(export.content());
    }
}

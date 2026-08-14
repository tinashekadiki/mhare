package zw.ac.uz.emhare.admissions.integration.http;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsCatalogue;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.ProgrammeHierarchyResolution;

/** Consumer-owned Admissions view of Academic Setup. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface AcademicSetupHttpService {

    @GetExchange("/api/academic/admissions-catalogue")
    AcademicAdmissionsCatalogue getAdmissionsCatalogue(
            @RequestParam("academicYearId") UUID academicYearId,
            @RequestParam("intakeId") UUID intakeId);

    @GetExchange("/api/academic/admissions-intakes")
    List<AcademicAdmissionsIntake> getOpenAdmissionsIntakes();

    @GetExchange("/api/academic/admissions-intakes/{intakeId}")
    AcademicAdmissionsIntake getAdmissionsIntake(@PathVariable("intakeId") UUID intakeId);

    @GetExchange("/api/academic/programmes/{programmeId}/hierarchy")
    ProgrammeHierarchyResolution getProgrammeHierarchy(@PathVariable("programmeId") UUID programmeId);

    @GetExchange("/api/academic/programmes/{programmeId}/hierarchy")
    ProgrammeHierarchyResolution getProgrammeHierarchy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("programmeId") UUID programmeId);
}

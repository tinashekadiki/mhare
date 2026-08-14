package zw.ac.uz.emhare.studentrecords.reporting.api.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.studentrecords.reporting.AdmissionsRegistrationOutcome;
import zw.ac.uz.emhare.studentrecords.reporting.AdmissionsRegistrationReportingService;

/** Authoritative registration outcomes consumed by Admissions reporting. @author Tinashe K */
@RestController
@RequestMapping("/api/student-records/reporting/admissions-registration-outcomes")
public class AdmissionsRegistrationReportingController {

    private final AdmissionsRegistrationReportingService service;

    public AdmissionsRegistrationReportingController(AdmissionsRegistrationReportingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_system-admin', 'ROLE_registry-officer', 'ROLE_admissions-officer', 'ROLE_admissions-manager', 'ROLE_reporting-officer')")
    public List<AdmissionsRegistrationOutcome> outcomes() {
        return service.outcomes();
    }
}

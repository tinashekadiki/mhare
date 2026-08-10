package zw.ac.uz.emhare.studentrecords.web;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.studentrecords.registration.CurriculumRegistrationUsageService;
import zw.ac.uz.emhare.studentrecords.registration.CurriculumRegistrationUsageService.CurriculumRegistrationUsageSummary;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/student-records/curriculum-module-usage")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin', 'ROLE_academic-admin')")
public class CurriculumRegistrationUsageController {

    private final CurriculumRegistrationUsageService curriculumRegistrationUsageService;

    public CurriculumRegistrationUsageController(CurriculumRegistrationUsageService curriculumRegistrationUsageService) {
        this.curriculumRegistrationUsageService = curriculumRegistrationUsageService;
    }

    @GetMapping("/{curriculumModuleId}")
    public CurriculumRegistrationUsageSummary usage(@PathVariable("curriculumModuleId") UUID curriculumModuleId) {
        return curriculumRegistrationUsageService.usage(curriculumModuleId);
    }
}

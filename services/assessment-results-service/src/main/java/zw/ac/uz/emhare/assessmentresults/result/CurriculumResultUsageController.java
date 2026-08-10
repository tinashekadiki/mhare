package zw.ac.uz.emhare.assessmentresults.result;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.assessmentresults.result.CurriculumResultUsageService.CurriculumResultUsageSummary;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/results/curriculum-module-usage")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin', 'ROLE_academic-admin')")
public class CurriculumResultUsageController {

    private final CurriculumResultUsageService curriculumResultUsageService;

    public CurriculumResultUsageController(CurriculumResultUsageService curriculumResultUsageService) {
        this.curriculumResultUsageService = curriculumResultUsageService;
    }

    @GetMapping("/{curriculumModuleId}")
    public CurriculumResultUsageSummary usage(@PathVariable("curriculumModuleId") UUID curriculumModuleId) {
        return curriculumResultUsageService.usage(curriculumModuleId);
    }
}

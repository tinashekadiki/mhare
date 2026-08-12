package zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Consumer-owned Assessment and Results usage contract. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface AssessmentResultsUsageHttpService {

    @GetExchange("/api/results/curriculum-module-usage/{curriculumModuleId}")
    ResultUsage getUsage(@PathVariable("curriculumModuleId") UUID curriculumModuleId);

    record ResultUsage(UUID curriculumModuleId, long resultCount) {
    }
}

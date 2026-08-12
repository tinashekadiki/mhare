package zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Consumer-owned Student Records usage contract. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface StudentRecordsUsageHttpService {

    @GetExchange("/api/student-records/curriculum-module-usage/{curriculumModuleId}")
    RegistrationUsage getUsage(@PathVariable("curriculumModuleId") UUID curriculumModuleId);

    record RegistrationUsage(UUID curriculumModuleId, long registrationCount) {
    }
}

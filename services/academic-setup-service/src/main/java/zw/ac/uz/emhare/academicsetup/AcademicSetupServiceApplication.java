package zw.ac.uz.emhare.academicsetup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;
import zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client.AssessmentResultsUsageHttpService;
import zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client.StudentRecordsUsageHttpService;

/** @author Tinashe K */
@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
@ImportHttpServices(group = "student-records", types = StudentRecordsUsageHttpService.class)
@ImportHttpServices(group = "assessment-results", types = AssessmentResultsUsageHttpService.class)
public class AcademicSetupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcademicSetupServiceApplication.class, args);
    }
}

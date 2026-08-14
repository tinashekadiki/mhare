package zw.ac.uz.emhare.admissions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;
import zw.ac.uz.emhare.admissions.integration.http.AcademicSetupHttpService;
import zw.ac.uz.emhare.admissions.integration.http.CoreIdentityHttpService;
import zw.ac.uz.emhare.admissions.integration.http.DocumentsReportingHttpService;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;
import zw.ac.uz.emhare.admissions.integration.http.StudentRecordsReportingHttpService;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
@ImportHttpServices(group = "academic-setup", types = AcademicSetupHttpService.class)
@ImportHttpServices(group = "core-identity", types = CoreIdentityHttpService.class)
@ImportHttpServices(group = "documents-reporting", types = DocumentsReportingHttpService.class)
@ImportHttpServices(group = "finance", types = FinanceHttpService.class)
@ImportHttpServices(group = "student-records", types = StudentRecordsReportingHttpService.class)
public class AdmissionsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdmissionsServiceApplication.class, args);
    }
}

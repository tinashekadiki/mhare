package zw.ac.uz.emhare.studentrecords;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;
import zw.ac.uz.emhare.studentrecords.integration.http.AcademicSetupRegistrationHttpService;
import zw.ac.uz.emhare.studentrecords.integration.http.CoreIdentityHttpService;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
@ImportHttpServices(group = "academic-setup", types = AcademicSetupRegistrationHttpService.class)
@ImportHttpServices(group = "core-identity", types = CoreIdentityHttpService.class)
public class StudentRecordsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentRecordsServiceApplication.class, args);
    }
}

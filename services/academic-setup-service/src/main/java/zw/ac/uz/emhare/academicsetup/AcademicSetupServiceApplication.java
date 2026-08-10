package zw.ac.uz.emhare.academicsetup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** @author Tinashe K */
@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class AcademicSetupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcademicSetupServiceApplication.class, args);
    }
}

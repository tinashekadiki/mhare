package zw.ac.uz.emhare.admissions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class AdmissionsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdmissionsServiceApplication.class, args);
    }
}

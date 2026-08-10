package zw.ac.uz.emhare.coreidentity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class CoreIdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreIdentityServiceApplication.class, args);
    }
}

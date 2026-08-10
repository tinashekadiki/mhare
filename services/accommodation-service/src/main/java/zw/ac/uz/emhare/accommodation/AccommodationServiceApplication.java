package zw.ac.uz.emhare.accommodation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class AccommodationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccommodationServiceApplication.class, args);
    }
}

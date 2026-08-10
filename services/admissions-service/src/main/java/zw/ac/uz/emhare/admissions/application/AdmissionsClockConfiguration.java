package zw.ac.uz.emhare.admissions.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdmissionsClockConfiguration {

    @Bean
    Clock admissionsClock() {
        return Clock.systemUTC();
    }
}

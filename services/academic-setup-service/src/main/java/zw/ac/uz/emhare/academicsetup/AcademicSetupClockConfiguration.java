package zw.ac.uz.emhare.academicsetup;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class AcademicSetupClockConfiguration {

    @Bean
    Clock academicSetupClock() {
        return Clock.systemUTC();
    }
}

package zw.ac.uz.emhare.accommodation;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class AccommodationClockConfiguration {
    @Bean
    Clock accommodationClock() {
        return Clock.systemUTC();
    }
}

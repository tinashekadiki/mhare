package zw.ac.uz.emhare.assessmentresults;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class AssessmentResultsClockConfiguration {
    @Bean
    Clock assessmentResultsClock() {
        return Clock.systemUTC();
    }
}

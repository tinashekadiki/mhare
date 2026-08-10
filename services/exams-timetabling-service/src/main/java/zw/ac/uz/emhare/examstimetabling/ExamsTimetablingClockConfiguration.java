package zw.ac.uz.emhare.examstimetabling;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class ExamsTimetablingClockConfiguration {
    @Bean
    Clock examsTimetablingClock() {
        return Clock.systemUTC();
    }
}

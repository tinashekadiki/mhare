package zw.ac.uz.emhare.studentrecords;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class StudentRecordsClockConfiguration {

    @Bean
    Clock studentRecordsClock() {
        return Clock.systemUTC();
    }
}

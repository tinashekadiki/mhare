package zw.ac.uz.emhare.documentsreporting;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class DocumentsReportingClockConfiguration {

    @Bean
    Clock documentsReportingClock() {
        return Clock.systemUTC();
    }
}

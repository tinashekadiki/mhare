package zw.ac.uz.emhare.coreidentity;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class CoreIdentityClockConfiguration {

    @Bean
    Clock coreIdentityClock() {
        return Clock.systemUTC();
    }
}

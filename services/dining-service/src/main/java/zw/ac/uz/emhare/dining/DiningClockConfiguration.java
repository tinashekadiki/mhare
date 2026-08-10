package zw.ac.uz.emhare.dining;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
public class DiningClockConfiguration {
    @Bean Clock diningClock() { return Clock.systemUTC(); }
}

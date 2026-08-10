package zw.ac.uz.emhare.finance.payment;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinanceClockConfiguration {

    @Bean
    Clock financeClock() {
        return Clock.systemUTC();
    }
}

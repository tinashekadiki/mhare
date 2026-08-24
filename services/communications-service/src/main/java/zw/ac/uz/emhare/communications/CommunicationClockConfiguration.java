package zw.ac.uz.emhare.communications;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies an injectable UTC clock for publication and event decisions. @author Tinashe K */
@Configuration
public class CommunicationClockConfiguration {

  @Bean
  Clock communicationClock() {
    return Clock.systemUTC();
  }
}

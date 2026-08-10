package zw.ac.uz.emhare.notifications;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author Tinashe K */
@Configuration
class NotificationClockConfiguration {
    @Bean Clock notificationClock(){return Clock.systemUTC();}
}

package zw.ac.uz.emhare.notifications;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Isolates the HMAC-authenticated callback endpoint from bearer-token endpoints. @author Tinashe K */
@Configuration
public class NotificationWebhookSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain notificationWebhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/notifications/provider-callbacks/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}

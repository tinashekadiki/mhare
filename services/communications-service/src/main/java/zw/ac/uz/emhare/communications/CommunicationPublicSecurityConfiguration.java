package zw.ac.uz.emhare.communications;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Allows anonymous reads of the canonical public Communications API. @author Tinashe K */
@Configuration
public class CommunicationPublicSecurityConfiguration {

  @Bean
  @Order(1)
  SecurityFilterChain communicationPublicSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/communications/public/**")
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }
}

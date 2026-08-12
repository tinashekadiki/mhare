package zw.ac.uz.emhare.admissions.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Allows token-protected referees to respond without an eMhare account. @author Tinashe K */
@Configuration
public class ApplicantRefereeReferenceSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain applicantRefereeReferenceSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/admissions/referee-references/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}

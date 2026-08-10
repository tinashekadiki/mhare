package zw.ac.uz.emhare.finance.payment.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Isolates the nonce-validated hosted-checkout return from bearer-token APIs. @author Tinashe K */
@Configuration
public class CbzIveriLiteReturnSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain cbzIveriLiteReturnSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/finance/application-payment-returns/**")
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}

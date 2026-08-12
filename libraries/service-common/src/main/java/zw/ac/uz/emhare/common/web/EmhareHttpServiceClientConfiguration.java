package zw.ac.uz.emhare.common.web;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/** Transitional HTTP Service Client context propagation until services adopt service-foundation. @author Tinashe K */
@Configuration
public class EmhareHttpServiceClientConfiguration {

    @Bean
    RestClientHttpServiceGroupConfigurer emhareHttpServiceContextConfigurer() {
        return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor((request, body, execution) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                request.getHeaders().setBearerAuth(jwtAuthenticationToken.getToken().getTokenValue());
            }
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            }
            request.getHeaders().set(HttpHeaders.ACCEPT, "application/json");
            return execution.execute(request, body);
        }));
    }
}

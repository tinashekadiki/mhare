package zw.ac.uz.emhare.common.web;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;

/** Applies Keycloak client credentials only to explicitly named background-client groups. @author Tinashe K */
@Configuration
@EnableConfigurationProperties(EmhareHttpServiceClientProperties.class)
public class EmhareClientCredentialsHttpServiceConfiguration {

    private static final Authentication SERVICE_PRINCIPAL = UsernamePasswordAuthenticationToken.authenticated(
            "emhare-service", "N/A", List.of(new SimpleGrantedAuthority("ROLE_SYSTEM")));

    @Bean
    @ConditionalOnProperty(
            prefix = "emhare.http-service-client.client-credentials",
            name = "enabled",
            havingValue = "true")
    RestClientHttpServiceGroupConfigurer emhareClientCredentialsHttpServiceConfigurer(
            EmhareHttpServiceClientProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager) {
        EmhareHttpServiceClientProperties.ClientCredentials clientCredentials = properties.getClientCredentials();
        if (clientCredentials.getGroupNames().isEmpty()) {
            throw new IllegalStateException(
                    "At least one HTTP Service Client group is required when client credentials are enabled.");
        }
        return new ClientCredentialsGroupConfigurer(clientCredentials, authorizedClientManager);
    }

    private static final class ClientCredentialsGroupConfigurer implements RestClientHttpServiceGroupConfigurer {

        private final EmhareHttpServiceClientProperties.ClientCredentials properties;
        private final OAuth2AuthorizedClientManager authorizedClientManager;

        private ClientCredentialsGroupConfigurer(
                EmhareHttpServiceClientProperties.ClientCredentials properties,
                OAuth2AuthorizedClientManager authorizedClientManager) {
            this.properties = properties;
            this.authorizedClientManager = authorizedClientManager;
        }

        @Override
        public void configureGroups(HttpServiceGroupConfigurer.Groups<RestClient.Builder> groups) {
            groups.filterByName(properties.getGroupNames().toArray(String[]::new))
                    .forEachClient((group, builder) -> builder.requestInterceptor((request, body, execution) -> {
                        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(
                                OAuth2AuthorizeRequest.withClientRegistrationId(properties.getRegistrationId())
                                        .principal(SERVICE_PRINCIPAL)
                                        .build());
                        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                            throw new ServiceDependencyUnavailableException(
                                    "A service access token could not be obtained for " + group.name() + ".", null);
                        }
                        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
                        return execution.execute(request, body);
                    }));
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}

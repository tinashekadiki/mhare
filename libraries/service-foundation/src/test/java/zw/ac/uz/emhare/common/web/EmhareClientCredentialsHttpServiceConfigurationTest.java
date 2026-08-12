package zw.ac.uz.emhare.common.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;

/** @author Tinashe K */
class EmhareClientCredentialsHttpServiceConfigurationTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void configuredBackgroundGroupUsesServiceTokenInsteadOfCurrentUserToken() {
        Jwt userJwt = Jwt.withTokenValue("user-token").header("alg", "none").subject("user-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(userJwt));

        ClientRegistration registration = ClientRegistration.withRegistrationId("emhare-service")
                .clientId("emhare-service")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("https://identity.test/token")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "service-token",
                Instant.now(),
                Instant.now().plusSeconds(300));
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                registration, "emhare-service", accessToken);
        OAuth2AuthorizedClientManager authorizedClientManager = mock(OAuth2AuthorizedClientManager.class);
        when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient);

        EmhareHttpServiceClientProperties properties = new EmhareHttpServiceClientProperties();
        properties.getClientCredentials().setEnabled(true);
        properties.getClientCredentials().setRegistrationId("emhare-service");
        properties.getClientCredentials().setGroupNames(java.util.List.of("background-provider"));

        RestClient.Builder builder = RestClient.builder();
        var groups = new NamedClientGroups("background-provider", builder);
        new EmhareHttpServiceClientConfiguration()
                .emhareHttpServiceContextConfigurer()
                .configureGroups(groups);
        new EmhareClientCredentialsHttpServiceConfiguration()
                .emhareClientCredentialsHttpServiceConfigurer(properties, authorizedClientManager)
                .configureGroups(groups);

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://provider.test/background-operation"))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        builder.build().get().uri("http://provider.test/background-operation").retrieve().toBodilessEntity();

        server.verify();
    }

    private static final class NamedClientGroups
            implements HttpServiceGroupConfigurer.Groups<RestClient.Builder> {

        private final String groupName;
        private final RestClient.Builder builder;
        private boolean selected = true;

        private NamedClientGroups(String groupName, RestClient.Builder builder) {
            this.groupName = groupName;
            this.builder = builder;
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<RestClient.Builder> filterByName(String... names) {
            selected = Arrays.asList(names).contains(groupName);
            return this;
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<RestClient.Builder> filter(Predicate<HttpServiceGroup> predicate) {
            selected = predicate.test(new TestHttpServiceGroup(groupName));
            return this;
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.ClientCallback<RestClient.Builder> callback) {
            if (selected) {
                callback.withClient(new TestHttpServiceGroup(groupName), builder);
            }
            selected = true;
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.InitializingClientCallback<RestClient.Builder> callback) {
            throw new UnsupportedOperationException("Not used by the foundation configurers");
        }

        @Override
        public void forEachProxyFactory(HttpServiceGroupConfigurer.ProxyFactoryCallback callback) {
            throw new UnsupportedOperationException("Not used by the foundation configurers");
        }

        @Override
        public void forEachGroup(HttpServiceGroupConfigurer.GroupCallback<RestClient.Builder> callback) {
            throw new UnsupportedOperationException("Not used by the foundation configurers");
        }
    }

    private record TestHttpServiceGroup(String name) implements HttpServiceGroup {
        @Override
        public Set<Class<?>> httpServiceTypes() {
            return Set.of();
        }

        @Override
        public ClientType clientType() {
            return ClientType.REST_CLIENT;
        }
    }
}

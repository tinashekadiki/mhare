package zw.ac.uz.emhare.common.web;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;

/** @author Tinashe K */
class EmhareHttpServiceClientConfigurationTest {

    @AfterEach
    void clearRequestContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void relaysJwtCorrelationAndJsonHeaders() {
        Jwt jwt = Jwt.withTokenValue("signed-user-token")
                .header("alg", "none")
                .subject("user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MDC.put("correlationId", "correlation-123");

        RestClient.Builder builder = RestClient.builder();
        new EmhareHttpServiceClientConfiguration()
                .emhareHttpServiceContextConfigurer()
                .configureGroups(new SingleClientGroups(builder));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://provider.test/catalogue"))
                .andExpect(header("Authorization", "Bearer signed-user-token"))
                .andExpect(header(CorrelationIdFilter.CORRELATION_ID_HEADER, "correlation-123"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        builder.build().get().uri("http://provider.test/catalogue").retrieve().toBodilessEntity();

        server.verify();
    }

    private static final class SingleClientGroups
            implements HttpServiceGroupConfigurer.Groups<RestClient.Builder> {

        private final RestClient.Builder builder;

        private SingleClientGroups(RestClient.Builder builder) {
            this.builder = builder;
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<RestClient.Builder> filterByName(String... names) {
            return this;
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<RestClient.Builder> filter(Predicate<HttpServiceGroup> predicate) {
            return this;
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.ClientCallback<RestClient.Builder> callback) {
            callback.withClient(new TestHttpServiceGroup(), builder);
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.InitializingClientCallback<RestClient.Builder> callback) {
            throw new UnsupportedOperationException("Not used by the foundation configurer");
        }

        @Override
        public void forEachProxyFactory(HttpServiceGroupConfigurer.ProxyFactoryCallback callback) {
            throw new UnsupportedOperationException("Not used by the foundation configurer");
        }

        @Override
        public void forEachGroup(HttpServiceGroupConfigurer.GroupCallback<RestClient.Builder> callback) {
            throw new UnsupportedOperationException("Not used by the foundation configurer");
        }
    }

    private record TestHttpServiceGroup() implements HttpServiceGroup {
        @Override
        public String name() {
            return "test-provider";
        }

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

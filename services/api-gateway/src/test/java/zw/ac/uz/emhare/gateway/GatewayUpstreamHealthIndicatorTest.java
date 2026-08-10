package zw.ac.uz.emhare.gateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/** @author Tinashe K */
class GatewayUpstreamHealthIndicatorTest {

    @Test
    void health_shouldBeUpAndRetainPerServiceEvidence_whenEveryConfiguredServiceIsAvailable() {
        GatewayUpstreamHealthProperties properties = properties(Map.of(
                "core-identity", URI.create("http://localhost:8081"),
                "finance", URI.create("http://localhost:8084")));
        GatewayUpstreamHealthIndicator indicator = new GatewayUpstreamHealthIndicator(
                properties, (serviceBaseUri, timeout) -> GatewayUpstreamProbeResult.available(200, 12));

        Health health = indicator.health();

        assertAll(
                () -> assertEquals(Status.UP, health.getStatus()),
                () -> assertEquals(2, health.getDetails().get("configuredServiceCount")),
                () -> assertEquals("UP", serviceDetail(health, "core-identity").get("status")),
                () -> assertEquals(200, serviceDetail(health, "finance").get("httpStatus")));
    }

    @Test
    void health_shouldBeDownAndIdentifyOnlyTheUnavailableService_whenOneConfiguredRouteCannotServeTraffic() {
        Map<String, URI> services = new LinkedHashMap<>();
        services.put("core-identity", URI.create("http://localhost:8081"));
        services.put("finance", URI.create("http://localhost:18084"));
        GatewayUpstreamHealthProperties properties = properties(services);
        GatewayUpstreamHealthIndicator indicator = new GatewayUpstreamHealthIndicator(properties, (serviceBaseUri, timeout) -> {
            if (serviceBaseUri.getPort() == 18084) {
                return GatewayUpstreamProbeResult.unavailable(null, 4, "Connection refused");
            }
            return GatewayUpstreamProbeResult.available(200, 3);
        });

        Health health = indicator.health();

        assertAll(
                () -> assertEquals(Status.DOWN, health.getStatus()),
                () -> assertEquals("UP", serviceDetail(health, "core-identity").get("status")),
                () -> assertEquals("DOWN", serviceDetail(health, "finance").get("status")),
                () -> assertEquals("http://localhost:18084", serviceDetail(health, "finance").get("baseUrl")),
                () -> assertTrue(String.valueOf(serviceDetail(health, "finance").get("failure")).contains("Connection refused")));
    }

    private static GatewayUpstreamHealthProperties properties(Map<String, URI> services) {
        GatewayUpstreamHealthProperties properties = new GatewayUpstreamHealthProperties();
        properties.setTimeout(Duration.ofMillis(250));
        properties.setServices(services);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serviceDetail(Health health, String serviceName) {
        Map<String, Object> services = (Map<String, Object>) health.getDetails().get("services");
        return (Map<String, Object>) services.get(serviceName);
    }
}

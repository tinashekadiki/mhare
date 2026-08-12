package zw.ac.uz.emhare.gateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

/** @author Tinashe K */
class GatewayServiceTopologyHealthIndicatorTest {

    @Test
    void health_shouldReportRegisteredInstances_withoutNetworkProbes() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("core-identity-service")).thenReturn(List.of(
                new DefaultServiceInstance("core-1", "core-identity-service", "localhost", 8081, false)));
        GatewayServiceTopologyHealthIndicator indicator = new GatewayServiceTopologyHealthIndicator(
                properties("core-identity-service"), discoveryClient);

        Health health = indicator.health();

        assertAll(
                () -> assertEquals(Status.UP, health.getStatus()),
                () -> assertEquals(1, health.getDetails().get("expectedServiceCount")),
                () -> assertEquals("REGISTERED", serviceDetail(health, "core-identity-service").get("status")),
                () -> assertEquals(1, serviceDetail(health, "core-identity-service").get("instanceCount")));
    }

    @Test
    void health_shouldIdentifyMissingService_withoutChangingApplicationLiveness() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("finance-service")).thenReturn(List.of());
        GatewayServiceTopologyHealthIndicator indicator = new GatewayServiceTopologyHealthIndicator(
                properties("finance-service"), discoveryClient);

        Health health = indicator.health();

        assertAll(
                () -> assertEquals(Status.DOWN, health.getStatus()),
                () -> assertEquals("MISSING", serviceDetail(health, "finance-service").get("status")),
                () -> assertEquals(0, serviceDetail(health, "finance-service").get("instanceCount")));
    }

    private static GatewayServiceTopologyProperties properties(String... serviceIds) {
        GatewayServiceTopologyProperties properties = new GatewayServiceTopologyProperties();
        properties.setExpectedServiceIds(List.of(serviceIds));
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serviceDetail(Health health, String serviceId) {
        return ((Map<String, Map<String, Object>>) health.getDetails().get("services")).get(serviceId);
    }
}

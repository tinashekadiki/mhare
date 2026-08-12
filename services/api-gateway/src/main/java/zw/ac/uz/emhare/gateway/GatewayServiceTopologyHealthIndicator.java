package zw.ac.uz.emhare.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

/** Reports registry topology without coupling gateway readiness to every domain service. @author Tinashe K */
@Component("serviceTopology")
@ConditionalOnProperty(prefix = "emhare.gateway.service-topology", name = "enabled", havingValue = "true", matchIfMissing = true)
class GatewayServiceTopologyHealthIndicator implements HealthIndicator {

    private final GatewayServiceTopologyProperties properties;
    private final DiscoveryClient discoveryClient;

    GatewayServiceTopologyHealthIndicator(
            GatewayServiceTopologyProperties properties,
            DiscoveryClient discoveryClient) {
        this.properties = properties;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean complete = true;
        for (String serviceId : properties.getExpectedServiceIds()) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            boolean registered = instances != null && !instances.isEmpty();
            complete &= registered;
            details.put(serviceId, Map.of(
                    "status", registered ? "REGISTERED" : "MISSING",
                    "instanceCount", instances == null ? 0 : instances.size()));
        }
        Health.Builder health = complete ? Health.up() : Health.down();
        return health
                .withDetail("expectedServiceCount", properties.getExpectedServiceIds().size())
                .withDetail("services", details)
                .build();
    }
}

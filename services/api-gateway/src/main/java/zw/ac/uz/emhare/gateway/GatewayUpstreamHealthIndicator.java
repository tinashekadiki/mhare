package zw.ac.uz.emhare.gateway;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Makes gateway readiness fail when a configured domain route cannot serve traffic. @author Tinashe K */
@Component("upstreamServices")
@ConditionalOnProperty(prefix = "emhare.gateway.upstream-health", name = "enabled", havingValue = "true", matchIfMissing = true)
class GatewayUpstreamHealthIndicator implements HealthIndicator {

    private final GatewayUpstreamHealthProperties properties;
    private final GatewayUpstreamProbe upstreamProbe;

    GatewayUpstreamHealthIndicator(
            GatewayUpstreamHealthProperties properties,
            GatewayUpstreamProbe upstreamProbe) {
        this.properties = properties;
        this.upstreamProbe = upstreamProbe;
    }

    @Override
    public Health health() {
        Map<String, Object> serviceDetails = new LinkedHashMap<>();
        boolean allServicesAvailable = true;

        for (Map.Entry<String, URI> configuredService : properties.getServices().entrySet()) {
            GatewayUpstreamProbeResult result = upstreamProbe.probe(configuredService.getValue(), properties.getTimeout());
            serviceDetails.put(configuredService.getKey(), detail(configuredService.getValue(), result));
            allServicesAvailable &= result.available();
        }

        Health.Builder health = allServicesAvailable ? Health.up() : Health.down();
        return health
                .withDetail("configuredServiceCount", properties.getServices().size())
                .withDetail("services", serviceDetails)
                .build();
    }

    private static Map<String, Object> detail(URI serviceBaseUri, GatewayUpstreamProbeResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", result.available() ? "UP" : "DOWN");
        detail.put("baseUrl", serviceBaseUri.toString());
        detail.put("responseTimeMilliseconds", result.responseTimeMilliseconds());
        if (result.httpStatus() != null) {
            detail.put("httpStatus", result.httpStatus());
        }
        if (result.failure() != null) {
            detail.put("failure", result.failure());
        }
        return detail;
    }
}

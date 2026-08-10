package zw.ac.uz.emhare.gateway;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authoritative service endpoints monitored by the API Gateway readiness check.
 *
 * @author Tinashe K
 */
@ConfigurationProperties(prefix = "emhare.gateway.upstream-health")
public class GatewayUpstreamHealthProperties {

    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(2);
    private Map<String, URI> services = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Map<String, URI> getServices() {
        return services;
    }

    public void setServices(Map<String, URI> services) {
        this.services = new LinkedHashMap<>(services);
    }
}

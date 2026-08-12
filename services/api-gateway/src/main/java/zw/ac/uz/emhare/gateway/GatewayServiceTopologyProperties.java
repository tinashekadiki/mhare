package zw.ac.uz.emhare.gateway;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Service IDs expected to be registered for the complete eMhare topology. @author Tinashe K */
@ConfigurationProperties(prefix = "emhare.gateway.service-topology")
public class GatewayServiceTopologyProperties {

    private boolean enabled = true;
    private List<String> expectedServiceIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getExpectedServiceIds() {
        return List.copyOf(expectedServiceIds);
    }

    public void setExpectedServiceIds(List<String> expectedServiceIds) {
        this.expectedServiceIds = expectedServiceIds == null ? new ArrayList<>() : new ArrayList<>(expectedServiceIds);
    }
}

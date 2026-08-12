package zw.ac.uz.emhare.common.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed internal HTTP client authentication configuration. @author Tinashe K */
@ConfigurationProperties(prefix = "emhare.http-service-client")
public class EmhareHttpServiceClientProperties {

    private final ClientCredentials clientCredentials = new ClientCredentials();

    public ClientCredentials getClientCredentials() {
        return clientCredentials;
    }

    public static class ClientCredentials {
        private boolean enabled;
        private String registrationId = "emhare-service";
        private List<String> groupNames = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRegistrationId() {
            return registrationId;
        }

        public void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }

        public List<String> getGroupNames() {
            return groupNames;
        }

        public void setGroupNames(List<String> groupNames) {
            this.groupNames = groupNames == null ? new ArrayList<>() : new ArrayList<>(groupNames);
        }
    }
}

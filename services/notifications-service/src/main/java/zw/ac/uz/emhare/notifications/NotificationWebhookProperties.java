package zw.ac.uz.emhare.notifications;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** External provider webhook verification settings. @author Tinashe K */
@Component
@ConfigurationProperties(prefix = "emhare.notifications.webhooks")
public class NotificationWebhookProperties {
    private Duration maximumClockSkew = Duration.ofMinutes(5);
    private Map<String, String> secrets = new HashMap<>();

    public Duration getMaximumClockSkew() {
        return maximumClockSkew;
    }

    public void setMaximumClockSkew(Duration maximumClockSkew) {
        if (maximumClockSkew == null || maximumClockSkew.isNegative() || maximumClockSkew.isZero()) {
            throw new IllegalArgumentException("Webhook maximum clock skew must be positive.");
        }
        this.maximumClockSkew = maximumClockSkew;
    }

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public void setSecrets(Map<String, String> secrets) {
        this.secrets = secrets == null ? new HashMap<>() : new HashMap<>(secrets);
    }

    Optional<String> secretFor(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) return Optional.empty();
        String requestedProvider = providerCode.trim();
        return secrets.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(requestedProvider))
                .map(Map.Entry::getValue)
                .filter(secret -> secret != null && !secret.isBlank())
                .findFirst();
    }
}

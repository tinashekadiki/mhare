package zw.ac.uz.emhare.notifications;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Verifies replay-bounded HMAC-SHA256 provider callbacks. @author Tinashe K */
@Component
public class NotificationWebhookSignatureVerifier {
    private static final String ALGORITHM = "HmacSHA256";
    private final NotificationWebhookProperties properties;
    private final Clock clock;

    public NotificationWebhookSignatureVerifier(NotificationWebhookProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isValid(String providerCode, String timestamp, String signature, String rawPayload) {
        if (timestamp == null || signature == null || rawPayload == null) return false;
        String secret = properties.secretFor(providerCode).orElse(null);
        if (secret == null) return false;
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(timestampSeconds);
        } catch (RuntimeException exception) {
            return false;
        }
        Duration difference = Duration.between(signedAt, clock.instant()).abs();
        if (difference.compareTo(properties.getMaximumClockSkew()) > 0) return false;
        String normalizedProvider = providerCode == null ? "" : providerCode.trim().toUpperCase();
        String signedContent = timestamp.trim() + "." + normalizedProvider + "." + rawPayload;
        String expected = "sha256=" + hmac(secret, signedContent);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private String hmac(String secret, String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Webhook signature verification is unavailable.", exception);
        }
    }
}

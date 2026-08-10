package zw.ac.uz.emhare.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class NotificationWebhookSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-08T17:00:00Z");

    @Test void verifiesProviderBoundSignatureAndRejectsReplay(){
        NotificationWebhookProperties properties=new NotificationWebhookProperties();
        properties.setMaximumClockSkew(Duration.ofMinutes(5));
        properties.setSecrets(Map.of("LOCAL_LOG","test-webhook-secret"));
        NotificationWebhookSignatureVerifier verifier=new NotificationWebhookSignatureVerifier(properties,Clock.fixed(NOW,ZoneOffset.UTC));
        String timestamp=Long.toString(NOW.getEpochSecond());String payload="{\"providerEventId\":\"evt-1\"}";
        String signature="sha256="+sign("test-webhook-secret",timestamp+".LOCAL_LOG."+payload);
        assertTrue(verifier.isValid("local_log",timestamp,signature,payload));
        assertFalse(verifier.isValid("another-provider",timestamp,signature,payload));
        assertFalse(verifier.isValid("LOCAL_LOG",Long.toString(NOW.minusSeconds(301).getEpochSecond()),signature,payload));
        assertFalse(verifier.isValid("LOCAL_LOG",timestamp,signature,payload+" "));
    }

    private String sign(String secret,String content){
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));}catch(java.security.GeneralSecurityException exception){throw new AssertionError(exception);}
    }
}

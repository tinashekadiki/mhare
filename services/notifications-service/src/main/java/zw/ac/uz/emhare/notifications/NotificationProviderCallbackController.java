package zw.ac.uz.emhare.notifications;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.notifications.NotificationContracts.CallbackSummary;
import zw.ac.uz.emhare.notifications.NotificationContracts.ProviderCallbackPayload;

/** Signature-verified delivery evidence endpoint for external providers. @author Tinashe K */
@RestController
@RequestMapping("/api/notifications/provider-callbacks")
public class NotificationProviderCallbackController {
    private final NotificationService notificationService;
    private final NotificationWebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public NotificationProviderCallbackController(
            NotificationService notificationService,
            NotificationWebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            Validator validator) {
        this.notificationService = notificationService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/{providerCode}")
    public ResponseEntity<CallbackSummary> receive(
            @PathVariable String providerCode,
            @RequestHeader("X-Emhare-Webhook-Timestamp") String timestamp,
            @RequestHeader("X-Emhare-Webhook-Signature") String signature,
            @RequestBody String rawPayload) {
        if (!signatureVerifier.isValid(providerCode, timestamp, signature, rawPayload)) {
            return ResponseEntity.status(401).build();
        }
        ProviderCallbackPayload callback = parse(rawPayload, ProviderCallbackPayload.class);
        var violations = validator.validate(callback);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        Map<String, Object> callbackEvidence = parse(rawPayload, new TypeReference<>() {});
        return ResponseEntity.ok(notificationService.recordProviderCallback(providerCode, callback, callbackEvidence));
    }

    private <T> T parse(String rawPayload, Class<T> valueType) {
        try {
            return objectMapper.readValue(rawPayload, valueType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Provider callback payload is not valid JSON.", exception);
        }
    }

    private <T> T parse(String rawPayload, TypeReference<T> valueType) {
        try {
            return objectMapper.readValue(rawPayload, valueType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Provider callback payload is not a JSON object.", exception);
        }
    }
}

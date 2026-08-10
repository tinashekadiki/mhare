package zw.ac.uz.emhare.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable, signature-verified provider delivery evidence. @author Tinashe K */
@Audited
@Immutable
@Entity
@Table(name = "notification_provider_callbacks")
@SQLRestriction("deleted_at IS NULL")
public class NotificationProviderCallback extends AuditableEntity {

    public enum DeliveryStatus { DELIVERED, BOUNCED, FAILED }

    @Column(name = "provider_code", nullable = false, length = 80)
    private String providerCode;
    @Column(name = "provider_event_id", nullable = false, length = 240)
    private String providerEventId;
    @Column(name = "provider_message_id", nullable = false, length = 240)
    private String providerMessageId;
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "notification_request_id")
    private UUID notificationRequestId;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "callback_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> callbackPayload;

    protected NotificationProviderCallback() {
    }

    public NotificationProviderCallback(
            String providerCode,
            String providerEventId,
            String providerMessageId,
            DeliveryStatus deliveryStatus,
            Instant occurredAt,
            Instant receivedAt,
            UUID notificationRequestId,
            String errorCode,
            String errorMessage,
            Map<String, Object> callbackPayload) {
        this.providerCode = NotificationValues.code(providerCode, "Provider code");
        this.providerEventId = NotificationValues.required(providerEventId, "Provider event ID");
        this.providerMessageId = NotificationValues.required(providerMessageId, "Provider message ID");
        this.deliveryStatus = java.util.Objects.requireNonNull(deliveryStatus, "Delivery status is required.");
        this.occurredAt = java.util.Objects.requireNonNull(occurredAt, "Provider event time is required.");
        this.receivedAt = java.util.Objects.requireNonNull(receivedAt, "Callback received time is required.");
        this.notificationRequestId = notificationRequestId;
        this.errorCode = NotificationValues.optional(errorCode);
        this.errorMessage = NotificationValues.optional(errorMessage);
        if (callbackPayload == null || callbackPayload.isEmpty()) {
            throw new IllegalArgumentException("Callback payload is required.");
        }
        this.callbackPayload = new LinkedHashMap<>(callbackPayload);
    }

    public String getProviderCode() { return providerCode; }
    public String getProviderEventId() { return providerEventId; }
    public String getProviderMessageId() { return providerMessageId; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public UUID getNotificationRequestId() { return notificationRequestId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getCallbackPayload() { return callbackPayload; }
}

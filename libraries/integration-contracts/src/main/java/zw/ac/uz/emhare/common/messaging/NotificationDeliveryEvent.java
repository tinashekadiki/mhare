package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** Append-only provider delivery-attempt status evidence. @author Tinashe K */
public record NotificationDeliveryEvent(UUID eventId, int schemaVersion, Instant occurredAt,
        UUID notificationEventId, int attemptNumber, String status, String providerMessageId,
        String failureReason) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

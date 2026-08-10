package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, recipient-resolved notification intent emitted transactionally by a business service.
 *
 * @author Tinashe K
 */
public record NotificationRequestedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        String sourceService,
        UUID sourceEventId,
        String idempotencyKey,
        String eventType,
        String templateCode,
        String channel,
        String locale,
        UUID recipientUserId,
        String recipientKey,
        String recipientAddress,
        String priority,
        Instant scheduledAt,
        int maximumAttempts,
        Map<String, String> variables) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}

package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.List;
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
        Map<String, String> variables,
        List<NotificationAttachmentReference> attachments) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public NotificationRequestedEvent(UUID eventId, int schemaVersion, Instant occurredAt,
            String sourceService, UUID sourceEventId, String idempotencyKey, String eventType,
            String templateCode, String channel, String locale, UUID recipientUserId,
            String recipientKey, String recipientAddress, String priority, Instant scheduledAt,
            int maximumAttempts, Map<String, String> variables) {
        this(eventId, schemaVersion, occurredAt, sourceService, sourceEventId, idempotencyKey,
                eventType, templateCode, channel, locale, recipientUserId, recipientKey,
                recipientAddress, priority, scheduledAt, maximumAttempts, variables, List.of());
    }
}

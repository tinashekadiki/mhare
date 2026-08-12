package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record StudentPortalAccessProvisionedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID conversionRequestId,
        UUID studentId,
        UUID userId,
        boolean successful,
        String failureReason) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record StudentPortalAccessProvisioningRequestedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID conversionRequestId,
        UUID studentId,
        String studentNumber,
        UUID userId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

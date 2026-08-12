package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record StudentConversionCompletedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID conversionRequestId,
        UUID studentId,
        String studentNumber,
        UUID userId,
        UUID applicationId,
        UUID offerId,
        UUID programmeEnrolmentId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

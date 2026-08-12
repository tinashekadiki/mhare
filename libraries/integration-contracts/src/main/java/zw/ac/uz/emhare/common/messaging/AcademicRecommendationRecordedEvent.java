package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AcademicRecommendationRecordedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID assignmentId,
        UUID recommendationId,
        String recommendation,
        UUID recommendedByUserId,
        Instant recommendedAt) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

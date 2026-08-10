package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AcademicReviewReleasedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID assignmentId,
        UUID applicationId,
        String applicationNumber,
        UUID programmeChoiceId,
        String programmeCode,
        String programmeName,
        UUID recommendationAcademicUnitId,
        String recommendationAcademicUnitCode,
        String recommendationAcademicUnitName,
        UUID releasedByUserId,
        Instant dueAt) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

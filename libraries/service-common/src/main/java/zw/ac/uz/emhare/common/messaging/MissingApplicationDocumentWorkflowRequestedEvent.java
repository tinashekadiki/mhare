package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Requests a durable corrective workflow when application evidence is rejected.
 *
 * @author Tinashe K
 */
public record MissingApplicationDocumentWorkflowRequestedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID applicationId,
        String applicationNumber,
        UUID applicantUserId,
        UUID documentId,
        long documentVersion,
        String requirementCode,
        String rejectionReason,
        UUID initiatedByUserId,
        Instant dueAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}

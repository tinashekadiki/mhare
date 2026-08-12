package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable document-verification evidence published by Documents/Reporting.
 *
 * @author Tinashe K
 */
public record DocumentVerificationChangedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID documentId,
        String ownerType,
        UUID ownerId,
        String documentTypeCode,
        String verificationStatus,
        UUID verifiedByUserId,
        Instant verifiedAt,
        String verificationComment,
        String rejectionReason,
        long documentVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}

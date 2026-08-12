package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** Authoritative current/superseded portal publication evidence. @author Tinashe K */
public record OfferPublicationEvent(UUID eventId, int schemaVersion, Instant occurredAt,
        UUID publicationId, UUID offerId, String offerStatus, UUID generatedDocumentId,
        int documentVersion, String offerNumber, UUID applicationId, String applicationNumber,
        UUID applicantUserId, String applicantName, UUID intakeId, UUID programmeId,
        String programmeCode, String programmeName, Instant publishedAt,
        boolean currentPublication, Instant supersededAt) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

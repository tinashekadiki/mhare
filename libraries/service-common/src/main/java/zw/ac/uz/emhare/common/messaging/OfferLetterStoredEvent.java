package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record OfferLetterStoredEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID offerId,
        long offerVersion,
        UUID generatedDocumentId,
        String documentNumber,
        String storageBucket,
        String storageKey,
        String checksumSha256,
        Instant storedAt) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

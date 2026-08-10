package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** @author Tinashe K */
public record OfferLetterRequestedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID offerId,
        long offerVersion,
        String offerNumber,
        UUID applicationId,
        String applicationNumber,
        String applicantNumber,
        String applicantName,
        String applicantEmail,
        UUID programmeId,
        String programmeCode,
        String programmeName,
        String offerType,
        String conditionsText,
        Instant acceptanceDeadline,
        LocalDate registrationDate,
        LocalDate orientationDate,
        LocalDate commencementDate,
        UUID requestedByUserId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

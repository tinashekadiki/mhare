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
        int documentVersion,
        String offerNumber,
        UUID applicationId,
        String applicationNumber,
        String applicantNumber,
        String applicantName,
        String applicantEmail,
        UUID applicantUserId,
        UUID programmeId,
        String programmeCode,
        String programmeName,
        UUID intakeId,
        String offerType,
        String conditionsText,
        Instant acceptanceDeadline,
        LocalDate registrationDate,
        LocalDate orientationDate,
        LocalDate commencementDate,
        OfferLetterContentSnapshot contentSnapshot,
        UUID requestedByUserId) {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public OfferLetterRequestedEvent(UUID eventId, int schemaVersion, Instant occurredAt,
            UUID offerId, long offerVersion, int documentVersion, String offerNumber, UUID applicationId,
            String applicationNumber, String applicantNumber, String applicantName, String applicantEmail,
            UUID applicantUserId, UUID programmeId, String programmeCode, String programmeName, UUID intakeId,
            String offerType, String conditionsText, Instant acceptanceDeadline, LocalDate registrationDate,
            LocalDate orientationDate, LocalDate commencementDate, UUID requestedByUserId) {
        this(eventId, schemaVersion, occurredAt, offerId, offerVersion, documentVersion, offerNumber, applicationId,
                applicationNumber, applicantNumber, applicantName, applicantEmail, applicantUserId, programmeId,
                programmeCode, programmeName, intakeId, offerType, conditionsText, acceptanceDeadline,
                registrationDate, orientationDate, commencementDate, null, requestedByUserId);
    }

    public OfferLetterRequestedEvent(UUID eventId, int schemaVersion, Instant occurredAt,
            UUID offerId, long offerVersion, String offerNumber, UUID applicationId,
            String applicationNumber, String applicantNumber, String applicantName, String applicantEmail,
            UUID programmeId, String programmeCode, String programmeName, String offerType,
            String conditionsText, Instant acceptanceDeadline, LocalDate registrationDate,
            LocalDate orientationDate, LocalDate commencementDate, UUID requestedByUserId) {
        this(eventId, schemaVersion, occurredAt, offerId, offerVersion, 1, offerNumber, applicationId,
                applicationNumber, applicantNumber, applicantName, applicantEmail, null, programmeId,
                programmeCode, programmeName, null, offerType, conditionsText, acceptanceDeadline,
                registrationDate, orientationDate, commencementDate, null, requestedByUserId);
    }
}

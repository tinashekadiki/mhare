package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable Admissions-to-Student-Records contract emitted only when an accepted
 * offer has no unresolved required conditions.
 *
 * @author Tinashe K
 */
public record AcceptedOfferReadyForConversionEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID applicationId,
        String applicationNumber,
        UUID offerId,
        String offerNumber,
        UUID applicantId,
        UUID applicantUserId,
        String applicantNumber,
        String applicantCategoryCode,
        String firstName,
        String lastName,
        String primaryEmail,
        UUID programmeChoiceId,
        UUID programmeId,
        UUID programmeVersionId,
        String programmeCode,
        String programmeName,
        UUID intakeId,
        LocalDate commencementDate) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}

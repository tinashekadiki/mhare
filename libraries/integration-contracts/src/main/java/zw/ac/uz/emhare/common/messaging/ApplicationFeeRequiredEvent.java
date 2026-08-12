package zw.ac.uz.emhare.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Admissions-to-Finance contract for provisioning an application fee.
 *
 * @author Tinashe K
 */
public record ApplicationFeeRequiredEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID applicationId,
        UUID applicantUserId,
        UUID applicantKeycloakUserId,
        BigDecimal amountDue,
        String currencyCode,
        boolean requiredForSubmission) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}

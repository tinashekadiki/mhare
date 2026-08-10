package zw.ac.uz.emhare.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Finance-to-Admissions payment projection contract.
 *
 * @author Tinashe K
 */
public record ApplicationPaymentReferenceUpdatedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        long stateSequence,
        UUID financePaymentReferenceId,
        UUID applicationId,
        String reference,
        BigDecimal amountDue,
        String currencyCode,
        String baseCurrencyCode,
        UUID exchangeRateId,
        BigDecimal baseAmountDue,
        String ratingStatus,
        String status,
        boolean requiredForSubmission,
        boolean workflowCleared,
        Instant expiresAt,
        Instant paidAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}

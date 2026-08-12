package zw.ac.uz.emhare.common.messaging;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record StudentFinanceAccountProvisionedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID conversionRequestId,
        UUID studentId,
        UUID financeAccountId,
        boolean successful,
        String failureReason) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

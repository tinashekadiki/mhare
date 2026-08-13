package zw.ac.uz.emhare.coreidentity.audit;

import java.time.Instant;
import java.util.Map;

/** @author Tinashe K */
public record CoreOperationalReport(
        Instant generatedAt,
        Map<String, Long> inventory,
        long loginSessionsLast24Hours,
        long auditEventsLast24Hours) {
}

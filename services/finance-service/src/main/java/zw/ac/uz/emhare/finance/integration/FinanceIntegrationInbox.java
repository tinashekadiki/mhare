package zw.ac.uz.emhare.finance.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** @author Tinashe K */
@Component
public class FinanceIntegrationInbox {

    private final JdbcTemplate jdbcTemplate;

    public FinanceIntegrationInbox(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claim(
            UUID eventId,
            String eventType,
            String sourceService,
            String payload,
            Instant receivedAt) {
        return jdbcTemplate.update("""
                INSERT INTO integration_inbox (
                    event_id, event_type, source_service, payload, received_at
                ) VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, sourceService, payload, Timestamp.from(receivedAt)) == 1;
    }

    public void markProcessed(UUID eventId, Instant processedAt) {
        int updatedRows = jdbcTemplate.update("""
                UPDATE integration_inbox
                SET processed_at = ?
                WHERE event_id = ?
                  AND processed_at IS NULL
                """, Timestamp.from(processedAt), eventId);
        if (updatedRows != 1) {
            throw new IllegalStateException("Claimed integration event could not be marked as processed.");
        }
    }
}

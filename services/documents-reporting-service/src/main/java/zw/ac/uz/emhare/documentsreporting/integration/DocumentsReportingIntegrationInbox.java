package zw.ac.uz.emhare.documentsreporting.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** @author Tinashe K */
@Entity
@Table(name = "integration_inbox")
public class DocumentsReportingIntegrationInbox {

    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;
    @Column(name = "source_service", nullable = false, length = 100)
    private String sourceService;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected DocumentsReportingIntegrationInbox() {
    }

    public DocumentsReportingIntegrationInbox(
            UUID eventId,
            String eventType,
            String sourceService,
            String payload,
            Instant receivedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.sourceService = sourceService;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public void markProcessed(Instant now) {
        processedAt = now;
    }

    public boolean hasEnvelope(String expectedEventType, String expectedSourceService) {
        return eventType.equals(expectedEventType)
                && sourceService.equals(expectedSourceService);
    }

    public UUID getEventId() { return eventId; }
    public String getPayload() { return payload; }
    public Instant getProcessedAt() { return processedAt; }
}

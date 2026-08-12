package zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model;

import zw.ac.uz.emhare.coreidentity.integration.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** @author Tinashe K */
@Entity
@Table(name = "integration_outbox")
public class CoreIdentityOutboxEvent {
    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 160)
    private String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CoreIdentityOutboxEvent() {
    }

    public CoreIdentityOutboxEvent(UUID id, String eventType, String payload, Instant now) {
        this.id = id;
        this.eventType = eventType;
        this.routingKey = eventType;
        this.payload = payload;
        this.occurredAt = now;
        this.status = "PENDING";
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markPublished(Instant now) {
        status = "PUBLISHED";
        publishedAt = now;
        lastError = null;
        updatedAt = now;
    }

    public void scheduleRetry(Instant now, RuntimeException exception) {
        attemptCount++;
        lastError = String.valueOf(exception.getMessage());
        if (lastError.length() > 1000) {
            lastError = lastError.substring(0, 1000);
        }
        updatedAt = now;
        if (attemptCount >= 20) {
            status = "DEAD";
            nextAttemptAt = now;
        } else {
            nextAttemptAt = now.plus(
                    Math.min(300L, 1L << Math.min(attemptCount, 8)), ChronoUnit.SECONDS);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }
}

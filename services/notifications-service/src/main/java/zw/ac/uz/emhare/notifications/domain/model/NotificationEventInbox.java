package zw.ac.uz.emhare.notifications.domain.model;

import zw.ac.uz.emhare.notifications.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Durable notification-intent inbox with operator-recoverable processing state. @author Tinashe K */
@Audited
@Entity
@Table(name = "notification_event_inbox")
@SQLRestriction("deleted_at IS NULL")
public class NotificationEventInbox extends AuditableEntity {

    public enum Status { RECEIVED, PROCESSING, PROCESSED, RETRY_SCHEDULED, DEAD }

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;
    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;
    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;
    @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
    private String rawPayload;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "processing_error", length = 1000)
    private String processingError;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    @Column(name = "manual_retry_by_user_id")
    private UUID manualRetryByUserId;
    @Column(name = "manual_retry_at")
    private Instant manualRetryAt;
    @Column(name = "manual_retry_reason", length = 1000)
    private String manualRetryReason;

    protected NotificationEventInbox() {
    }

    public NotificationEventInbox(
            String sourceService,
            UUID sourceEventId,
            String eventType,
            String rawPayload,
            Instant receivedAt,
            int maxAttempts) {
        this.sourceService = NotificationValues.code(sourceService, "Source service");
        this.sourceEventId = java.util.Objects.requireNonNull(sourceEventId, "Source event ID is required.");
        this.eventType = NotificationValues.required(eventType, "Event type");
        this.rawPayload = NotificationValues.required(rawPayload, "Event payload");
        this.receivedAt = java.util.Objects.requireNonNull(receivedAt, "Received time is required.");
        if (maxAttempts < 1) throw new IllegalArgumentException("Maximum inbox attempts must be positive.");
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = receivedAt;
        this.status = Status.RECEIVED;
    }

    public void startAttempt(Instant now) {
        if (status != Status.RECEIVED && status != Status.RETRY_SCHEDULED) {
            throw new IllegalStateException("Notification event is not ready for processing.");
        }
        status = Status.PROCESSING;
        attemptCount++;
        lastAttemptAt = now;
        nextAttemptAt = null;
        processingError = null;
    }

    public void markProcessed(Instant now) {
        if (status != Status.PROCESSING) throw new IllegalStateException("Notification event is not processing.");
        status = Status.PROCESSED;
        processedAt = now;
        nextAttemptAt = null;
        processingError = null;
    }

    public void recordFailure(RuntimeException error, Instant now, boolean permanent) {
        processingError = safeError(error);
        if (permanent || attemptCount >= maxAttempts) {
            status = Status.DEAD;
            nextAttemptAt = null;
            return;
        }
        status = Status.RETRY_SCHEDULED;
        long delaySeconds = Math.min(3600L, 30L << Math.min(attemptCount - 1, 7));
        nextAttemptAt = now.plus(delaySeconds, ChronoUnit.SECONDS);
    }

    public void retryNow(UUID actorUserId, String reason, Instant now, long expectedVersion) {
        NotificationValues.version(getVersion(), expectedVersion, "Notification event");
        if (status != Status.DEAD) throw new IllegalStateException("Only dead notification events can be retried manually.");
        manualRetryByUserId = java.util.Objects.requireNonNull(actorUserId, "Retry operator is required.");
        manualRetryAt = java.util.Objects.requireNonNull(now, "Retry time is required.");
        manualRetryReason = NotificationValues.reason(reason, "Manual retry reason");
        status = Status.RETRY_SCHEDULED;
        processingError = null;
        nextAttemptAt = now;
        maxAttempts = Math.addExact(maxAttempts, 5);
    }

    private String safeError(RuntimeException error) {
        String message = error.getMessage();
        String value = message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
        return value.substring(0, Math.min(value.length(), 1000));
    }

    public String getSourceService() { return sourceService; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getEventType() { return eventType; }
    public String getRawPayload() { return rawPayload; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Status getStatus() { return status; }
    public String getProcessingError() { return processingError; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public UUID getManualRetryByUserId() { return manualRetryByUserId; }
    public Instant getManualRetryAt() { return manualRetryAt; }
    public String getManualRetryReason() { return manualRetryReason; }
}

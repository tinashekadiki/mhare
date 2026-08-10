package zw.ac.uz.emhare.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.notifications.NotificationContracts.QueueNotification;

/** Converts durable integration events into governed, rendered notification requests. @author Tinashe K */
@Component
public class NotificationInboxProcessor {
    private final NotificationEventInboxRepository inboxRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationInboxProcessor(
            NotificationEventInboxRepository inboxRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.inboxRepository = inboxRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${emhare.notifications.inbox-processing-interval-ms:3000}")
    @Transactional
    public void processDue() {
        Instant now = clock.instant();
        for (NotificationEventInbox inbox : inboxRepository.lockDue(now, PageRequest.of(0, 50))) {
            process(inbox, now);
        }
    }

    void process(NotificationEventInbox inbox, Instant startedAt) {
        inbox.startAttempt(startedAt);
        try {
            NotificationRequestedEvent event = deserialize(inbox.getRawPayload());
            validate(inbox, event);
            notificationService.queue(toCommand(event));
            inbox.markProcessed(clock.instant());
        } catch (RuntimeException exception) {
            inbox.recordFailure(exception, clock.instant(), isPermanent(exception));
        }
    }

    private NotificationRequestedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationRequestedEvent.class);
        } catch (JacksonException exception) {
            throw new InvalidNotificationEventException("Notification event payload is not valid JSON.", exception);
        }
    }

    private void validate(NotificationEventInbox inbox, NotificationRequestedEvent event) {
        if (event.eventId() == null
                || !event.eventId().equals(inbox.getSourceEventId())
                || event.schemaVersion() != NotificationRequestedEvent.CURRENT_SCHEMA_VERSION
                || event.occurredAt() == null
                || event.sourceEventId() == null
                || blank(event.sourceService())
                || !event.sourceService().equalsIgnoreCase(inbox.getSourceService())
                || blank(event.idempotencyKey())
                || blank(event.eventType())
                || blank(event.templateCode())
                || blank(event.channel())
                || blank(event.locale())
                || blank(event.recipientKey())
                || blank(event.recipientAddress())
                || blank(event.priority())
                || event.maximumAttempts() < 1
                || event.maximumAttempts() > 20) {
            throw new InvalidNotificationEventException("Notification event contract is invalid or unsupported.");
        }
        channel(event.channel());
        priority(event.priority());
    }

    private QueueNotification toCommand(NotificationRequestedEvent event) {
        return new QueueNotification(
                event.idempotencyKey(),
                event.sourceService(),
                event.sourceEventId(),
                event.eventType(),
                event.templateCode(),
                channel(event.channel()),
                event.locale(),
                event.recipientUserId(),
                event.recipientKey(),
                event.recipientAddress(),
                priority(event.priority()),
                event.scheduledAt(),
                event.maximumAttempts(),
                event.variables() == null ? Map.of() : event.variables());
    }

    private NotificationTemplate.Channel channel(String value) {
        try {
            return NotificationTemplate.Channel.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new InvalidNotificationEventException("Notification channel is unsupported.", exception);
        }
    }

    private NotificationRequest.Priority priority(String value) {
        try {
            return NotificationRequest.Priority.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new InvalidNotificationEventException("Notification priority is unsupported.", exception);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPermanent(RuntimeException exception) {
        return exception instanceof InvalidNotificationEventException || exception instanceof IllegalArgumentException;
    }

    static final class InvalidNotificationEventException extends RuntimeException {
        InvalidNotificationEventException(String message) { super(message); }
        InvalidNotificationEventException(String message, Throwable cause) { super(message, cause); }
    }
}

package zw.ac.uz.emhare.notifications;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** Commits raw notification intents to the application inbox before broker acknowledgement. @author Tinashe K */
@Component
public class NotificationRequestedEventListener {
    private final NotificationService notificationService;

    public NotificationRequestedEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = EmhareMessagingTopology.NOTIFICATION_REQUESTED_QUEUE)
    public void receive(Message message) {
        String sourceService = requiredHeader(message, "source-service");
        String eventType = required(message.getMessageProperties().getType(), "Message type");
        if (!EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT.equals(eventType)) {
            throw new IllegalArgumentException("Notification event routing type is unsupported.");
        }
        UUID eventId = parseEventId(required(message.getMessageProperties().getMessageId(), "Message ID"));
        String rawPayload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            notificationService.captureEvent(sourceService, eventId, eventType, rawPayload);
        } catch (DataIntegrityViolationException duplicate) {
            // A broker redelivery may race the first committed delivery. The unique inbox key is authoritative.
        }
    }

    private String requiredHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return required(value == null ? null : value.toString(), name);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private UUID parseEventId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Message ID must be a UUID.", exception);
        }
    }
}

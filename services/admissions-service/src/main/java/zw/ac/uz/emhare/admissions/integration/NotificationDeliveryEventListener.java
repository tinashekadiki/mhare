package zw.ac.uz.emhare.admissions.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDispatchRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferPublicationRepository;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationDeliveryEvent;

/** Reconciles duplicate/out-of-order notification delivery evidence without republishing offers. @author Tinashe K */
@Component
public class NotificationDeliveryEventListener {
    private final AdmissionsIntegrationInbox inbox;
    private final OfferDispatchRepository dispatches;
    private final OfferPublicationRepository publications;
    private final ObjectMapper mapper;
    private final Clock clock;

    public NotificationDeliveryEventListener(
            AdmissionsIntegrationInbox inbox,
            OfferDispatchRepository dispatches,
            OfferPublicationRepository publications,
            ObjectMapper mapper,
            Clock clock) {
        this.inbox = inbox;
        this.dispatches = dispatches;
        this.publications = publications;
        this.mapper = mapper;
        this.clock = clock;
    }

    @RabbitListener(queues=EmhareMessagingTopology.NOTIFICATION_DELIVERY_ADMISSIONS_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        NotificationDeliveryEvent event = deserialize(payload);
        validate(event);
        if (!inbox.claim(
                event.eventId(),
                EmhareMessagingTopology.NOTIFICATION_DELIVERY_EVENT,
                "notifications-service",
                payload,
                clock.instant())) {
            return;
        }
        OfferDispatch dispatch = dispatches.findByNotificationEventIdAndDeletedAtIsNull(event.notificationEventId())
                .orElse(null);
        if (dispatch == null) {
            inbox.markProcessed(event.eventId(), clock.instant());
            return;
        }
        OfferDispatchStatus dispatchStatus = parseDispatch(event.status());
        dispatch.recordStatus(dispatchStatus, event.providerMessageId(), event.failureReason(), event.occurredAt());
        OfferPublication publication = dispatch.getOfferPublication();
        OfferEmailDeliveryStatus emailStatus = switch (dispatchStatus) {
            case BOUNCED -> OfferEmailDeliveryStatus.BOUNCED;
            case FAILED -> OfferEmailDeliveryStatus.FAILED;
            default -> OfferEmailDeliveryStatus.SENT;
        };
        publication.recordEmailStatus(emailStatus, event.providerMessageId(), event.failureReason(), event.occurredAt());
        dispatches.save(dispatch);
        publications.save(publication);
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private NotificationDeliveryEvent deserialize(String payload) {
        try {
            return mapper.readValue(payload, NotificationDeliveryEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Notification delivery event is invalid.", exception);
        }
    }

    private void validate(NotificationDeliveryEvent event) {
        if (event.schemaVersion() != NotificationDeliveryEvent.CURRENT_SCHEMA_VERSION
                || event.eventId() == null
                || event.notificationEventId() == null
                || event.occurredAt() == null
                || event.status() == null) {
            throw new IllegalArgumentException("Notification delivery event is invalid or unsupported.");
        }
    }

    private OfferDispatchStatus parseDispatch(String value) {
        try {
            return OfferDispatchStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Notification delivery status is unsupported.", exception);
        }
    }
}

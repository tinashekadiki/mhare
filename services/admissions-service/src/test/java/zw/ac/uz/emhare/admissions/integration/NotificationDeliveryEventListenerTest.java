package zw.ac.uz.emhare.admissions.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDispatchRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferPublicationRepository;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationDeliveryEvent;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T10:00:00Z");

    @Mock private AdmissionsIntegrationInbox inbox;
    @Mock private OfferDispatchRepository dispatchRepository;
    @Mock private OfferPublicationRepository publicationRepository;
    @Mock private ObjectMapper objectMapper;
    private NotificationDeliveryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationDeliveryEventListener(
                inbox,
                dispatchRepository,
                publicationRepository,
                objectMapper,
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC));
    }

    @Test
    void nonOfferNotificationDeliveryIsAcknowledgedWithoutRetryingTheAdmissionsQueue() throws Exception {
        UUID deliveryEventId = UUID.randomUUID();
        UUID refereeNotificationEventId = UUID.randomUUID();
        NotificationDeliveryEvent event = new NotificationDeliveryEvent(
                deliveryEventId,
                NotificationDeliveryEvent.CURRENT_SCHEMA_VERSION,
                OCCURRED_AT,
                refereeNotificationEventId,
                1,
                "SENT",
                "local-log-message",
                null);
        when(objectMapper.readValue(anyString(), eq(NotificationDeliveryEvent.class))).thenReturn(event);
        when(inbox.claim(
                eq(deliveryEventId),
                eq(EmhareMessagingTopology.NOTIFICATION_DELIVERY_EVENT),
                eq("notifications-service"),
                anyString(),
                any(Instant.class))).thenReturn(true);
        when(dispatchRepository.findByNotificationEventIdAndDeletedAtIsNull(refereeNotificationEventId))
                .thenReturn(Optional.empty());

        listener.receive(new Message("{}".getBytes(StandardCharsets.UTF_8)));

        verify(inbox).markProcessed(deliveryEventId, OCCURRED_AT);
    }
}

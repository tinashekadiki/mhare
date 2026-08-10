package zw.ac.uz.emhare.studentrecords.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.studentrecords.conversion.StudentConversionService;

/** @author Tinashe K */
@Component
public class AcceptedOfferConversionEventListener {
    private final StudentRecordsIntegrationInbox inbox;
    private final StudentConversionService conversionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AcceptedOfferConversionEventListener(
            StudentRecordsIntegrationInbox inbox, StudentConversionService conversionService,
            ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox;
        this.conversionService = conversionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        AcceptedOfferReadyForConversionEvent event = deserialize(payload);
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_EVENT,
                "admissions-service", payload, clock.instant())) return;
        conversionService.startConversion(event);
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private AcceptedOfferReadyForConversionEvent deserialize(String payload) {
        try { return objectMapper.readValue(payload, AcceptedOfferReadyForConversionEvent.class); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Accepted-offer event payload is invalid.", exception); }
    }
}

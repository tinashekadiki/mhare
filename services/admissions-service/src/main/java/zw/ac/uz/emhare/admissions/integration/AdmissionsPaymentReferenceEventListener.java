package zw.ac.uz.emhare.admissions.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationService;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** @author Tinashe K */
@Component
public class AdmissionsPaymentReferenceEventListener {

    private final AdmissionsIntegrationInbox integrationInbox;
    private final AdmissionsApplicationService admissionsApplicationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdmissionsPaymentReferenceEventListener(
            AdmissionsIntegrationInbox integrationInbox,
            AdmissionsApplicationService admissionsApplicationService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.integrationInbox = integrationInbox;
        this.admissionsApplicationService = admissionsApplicationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_QUEUE)
    @Transactional
    public void receivePaymentReferenceUpdate(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        ApplicationPaymentReferenceUpdatedEvent event = deserialize(payload);
        validate(event);
        if (!integrationInbox.claim(
                event.eventId(),
                EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_EVENT,
                "finance-service",
                payload,
                clock.instant())) {
            return;
        }
        admissionsApplicationService.applyFinancePaymentReferenceUpdate(event);
        integrationInbox.markProcessed(event.eventId(), clock.instant());
    }

    private ApplicationPaymentReferenceUpdatedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ApplicationPaymentReferenceUpdatedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payment reference event payload is invalid.", exception);
        }
    }

    private void validate(ApplicationPaymentReferenceUpdatedEvent event) {
        if (event.eventId() == null
                || event.schemaVersion() != ApplicationPaymentReferenceUpdatedEvent.CURRENT_SCHEMA_VERSION
                || event.applicationId() == null
                || event.financePaymentReferenceId() == null) {
            throw new IllegalArgumentException("Payment reference event contract is invalid or unsupported.");
        }
    }
}

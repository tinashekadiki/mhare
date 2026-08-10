package zw.ac.uz.emhare.finance.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.ApplicationFeeRequiredEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.finance.payment.CreateApplicationPaymentReferenceCommand;
import zw.ac.uz.emhare.finance.payment.FinanceApplicationPaymentService;

/** @author Tinashe K */
@Component
public class ApplicationFeeRequiredEventListener {

    private final FinanceIntegrationInbox integrationInbox;
    private final FinanceApplicationPaymentService financeApplicationPaymentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApplicationFeeRequiredEventListener(
            FinanceIntegrationInbox integrationInbox,
            FinanceApplicationPaymentService financeApplicationPaymentService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.integrationInbox = integrationInbox;
        this.financeApplicationPaymentService = financeApplicationPaymentService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_QUEUE)
    @Transactional
    public void receiveApplicationFeeRequired(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        ApplicationFeeRequiredEvent event = deserialize(payload);
        validate(event);
        if (!integrationInbox.claim(
                event.eventId(),
                EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT,
                "admissions-service",
                payload,
                clock.instant())) {
            return;
        }
        financeApplicationPaymentService.ensurePaymentReference(new CreateApplicationPaymentReferenceCommand(
                event.applicationId(),
                event.applicantUserId(),
                event.applicantKeycloakUserId(),
                event.amountDue(),
                event.currencyCode(),
                event.requiredForSubmission()));
        integrationInbox.markProcessed(event.eventId(), clock.instant());
    }

    private ApplicationFeeRequiredEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ApplicationFeeRequiredEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Application fee event payload is invalid.", exception);
        }
    }

    private void validate(ApplicationFeeRequiredEvent event) {
        if (event.eventId() == null
                || event.schemaVersion() != ApplicationFeeRequiredEvent.CURRENT_SCHEMA_VERSION
                || event.applicationId() == null
                || event.applicantUserId() == null
                || event.applicantKeycloakUserId() == null
                || !event.requiredForSubmission()) {
            throw new IllegalArgumentException("Application fee event contract is invalid or unsupported.");
        }
    }
}

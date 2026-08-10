package zw.ac.uz.emhare.finance.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisionedEvent;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReference;
import zw.ac.uz.emhare.finance.student.StudentFinanceAccount;

/** @author Tinashe K */
@Service
public class FinanceIntegrationOutboxService {

    private final FinanceOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FinanceIntegrationOutboxService(
            FinanceOutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueuePaymentReferenceUpdated(ApplicationPaymentReference paymentReference) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        ApplicationPaymentReferenceUpdatedEvent event = new ApplicationPaymentReferenceUpdatedEvent(
                eventId,
                ApplicationPaymentReferenceUpdatedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                paymentReference.getStateSequence(),
                paymentReference.getId(),
                paymentReference.getSourceApplicationId(),
                paymentReference.getReference(),
                paymentReference.getAmountDue(),
                paymentReference.getCurrencyCode(),
                paymentReference.getBaseCurrencyCode(),
                paymentReference.getExchangeRateId(),
                paymentReference.getBaseAmountDue(),
                paymentReference.getRatingStatusCode(),
                paymentReference.getStatusCode(),
                paymentReference.isRequiredForSubmission(),
                paymentReference.isWorkflowCleared(),
                paymentReference.getExpiresAt(),
                paymentReference.getPaidAt());
        outboxEventRepository.save(new FinanceOutboxEvent(
                eventId,
                EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_EVENT,
                EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_EVENT,
                serialize(event),
                occurredAt));
    }

    public void enqueueStudentFinanceAccountProvisioned(
            UUID conversionRequestId, UUID studentId, StudentFinanceAccount account) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        StudentFinanceAccountProvisionedEvent event = new StudentFinanceAccountProvisionedEvent(
                eventId, StudentFinanceAccountProvisionedEvent.CURRENT_SCHEMA_VERSION, occurredAt,
                conversionRequestId, studentId, account.getId(), true, null);
        outboxEventRepository.save(new FinanceOutboxEvent(
                eventId,
                EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_EVENT,
                EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_EVENT,
                serialize(event), occurredAt));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Finance integration event could not be serialized.", exception);
        }
    }
}

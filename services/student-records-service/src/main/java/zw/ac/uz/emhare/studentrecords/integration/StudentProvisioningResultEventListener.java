package zw.ac.uz.emhare.studentrecords.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisionedEvent;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisionedEvent;
import zw.ac.uz.emhare.studentrecords.conversion.StudentConversionService;

/** @author Tinashe K */
@Component
public class StudentProvisioningResultEventListener {
    private final StudentRecordsIntegrationInbox inbox;
    private final StudentConversionService conversionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StudentProvisioningResultEventListener(
            StudentRecordsIntegrationInbox inbox, StudentConversionService conversionService,
            ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox;
        this.conversionService = conversionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_QUEUE)
    @Transactional
    public void receiveFinanceResult(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        StudentFinanceAccountProvisionedEvent event = deserialize(payload, StudentFinanceAccountProvisionedEvent.class);
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_EVENT,
                "finance-service", payload, clock.instant())) return;
        conversionService.recordFinanceProvisioning(event.conversionRequestId(), event.successful(), event.failureReason());
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    @RabbitListener(queues = EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_QUEUE)
    @Transactional
    public void receivePortalResult(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        StudentPortalAccessProvisionedEvent event = deserialize(payload, StudentPortalAccessProvisionedEvent.class);
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_EVENT,
                "core-identity-service", payload, clock.instant())) return;
        conversionService.recordPortalProvisioning(event.conversionRequestId(), event.successful(), event.failureReason());
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private <T> T deserialize(String payload, Class<T> type) {
        try { return objectMapper.readValue(payload, type); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Provisioning result payload is invalid.", exception); }
    }
}

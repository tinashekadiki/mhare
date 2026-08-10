package zw.ac.uz.emhare.finance.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;
import zw.ac.uz.emhare.finance.student.StudentFinanceAccount;
import zw.ac.uz.emhare.finance.student.StudentFinanceAccountService;

/** @author Tinashe K */
@Component
public class StudentFinanceAccountProvisioningEventListener {
    private final FinanceIntegrationInbox inbox;
    private final StudentFinanceAccountService accountService;
    private final FinanceIntegrationOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StudentFinanceAccountProvisioningEventListener(
            FinanceIntegrationInbox inbox, StudentFinanceAccountService accountService,
            FinanceIntegrationOutboxService outboxService, ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox;
        this.accountService = accountService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        StudentFinanceAccountProvisioningRequestedEvent event = deserialize(payload);
        validate(event);
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT,
                "student-records-service", payload, clock.instant())) return;
        StudentFinanceAccount account = accountService.ensureAccount(event);
        outboxService.enqueueStudentFinanceAccountProvisioned(event.conversionRequestId(), event.studentId(), account);
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private StudentFinanceAccountProvisioningRequestedEvent deserialize(String payload) {
        try { return objectMapper.readValue(payload, StudentFinanceAccountProvisioningRequestedEvent.class); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Student finance account event is invalid.", exception); }
    }

    private void validate(StudentFinanceAccountProvisioningRequestedEvent event) {
        if (event.eventId() == null
                || event.schemaVersion() != StudentFinanceAccountProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION
                || event.conversionRequestId() == null || event.studentId() == null
                || event.userId() == null || event.sourceOfferId() == null
                || event.studentNumber() == null || event.studentNumber().isBlank()) {
            throw new IllegalArgumentException("Student finance account event contract is invalid or unsupported.");
        }
    }
}

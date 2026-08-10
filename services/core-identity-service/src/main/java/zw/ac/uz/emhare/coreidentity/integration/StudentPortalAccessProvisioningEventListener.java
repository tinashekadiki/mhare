package zw.ac.uz.emhare.coreidentity.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisioningRequestedEvent;
import zw.ac.uz.emhare.coreidentity.provisioning.StudentPortalAccessProvisioning;
import zw.ac.uz.emhare.coreidentity.provisioning.StudentPortalAccessProvisioningService;

/** @author Tinashe K */
@Component
public class StudentPortalAccessProvisioningEventListener {
    private final CoreIdentityIntegrationInbox inbox;
    private final StudentPortalAccessProvisioningService provisioningService;
    private final CoreIdentityIntegrationOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StudentPortalAccessProvisioningEventListener(
            CoreIdentityIntegrationInbox inbox,
            StudentPortalAccessProvisioningService provisioningService,
            CoreIdentityIntegrationOutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.inbox = inbox;
        this.provisioningService = provisioningService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(
            queues = EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        StudentPortalAccessProvisioningRequestedEvent event = deserialize(payload);
        validate(event);
        if (!inbox.claim(
                event.eventId(),
                EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT,
                "student-records-service",
                payload,
                clock.instant())) {
            return;
        }
        StudentPortalAccessProvisioning provisioning = provisioningService.ensureAccess(event);
        outboxService.enqueueStudentPortalAccessProvisioned(provisioning);
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private StudentPortalAccessProvisioningRequestedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(
                    payload, StudentPortalAccessProvisioningRequestedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Student portal access event is invalid.", exception);
        }
    }

    private void validate(StudentPortalAccessProvisioningRequestedEvent event) {
        if (event.eventId() == null
                || event.schemaVersion()
                        != StudentPortalAccessProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION
                || event.conversionRequestId() == null
                || event.studentId() == null
                || event.userId() == null
                || event.studentNumber() == null
                || event.studentNumber().isBlank()) {
            throw new IllegalArgumentException(
                    "Student portal access event contract is invalid or unsupported.");
        }
    }
}

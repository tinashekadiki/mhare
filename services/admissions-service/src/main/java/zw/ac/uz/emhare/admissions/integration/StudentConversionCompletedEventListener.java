package zw.ac.uz.emhare.admissions.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsSelectionOfferService;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentConversionCompletedEvent;

/** @author Tinashe K */
@Component
public class StudentConversionCompletedEventListener {
    private final AdmissionsIntegrationInbox inbox;
    private final AdmissionsSelectionOfferService workflowService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StudentConversionCompletedEventListener(
            AdmissionsIntegrationInbox inbox, AdmissionsSelectionOfferService workflowService,
            ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        StudentConversionCompletedEvent event = deserialize(payload);
        validate(event);
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_EVENT,
                "student-records-service", payload, clock.instant())) return;
        workflowService.completeStudentConversion(
                event.conversionRequestId(), event.applicationId(), event.offerId(), event.studentId(),
                event.studentNumber(), event.userId());
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private StudentConversionCompletedEvent deserialize(String payload) {
        try { return objectMapper.readValue(payload, StudentConversionCompletedEvent.class); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Student conversion event is invalid.", exception); }
    }

    private void validate(StudentConversionCompletedEvent event) {
        if (event.eventId() == null || event.schemaVersion() != StudentConversionCompletedEvent.CURRENT_SCHEMA_VERSION
                || event.conversionRequestId() == null || event.applicationId() == null || event.offerId() == null
                || event.studentId() == null || event.userId() == null
                || event.studentNumber() == null || event.studentNumber().isBlank()) {
            throw new IllegalArgumentException("Student conversion event contract is invalid or unsupported.");
        }
    }
}

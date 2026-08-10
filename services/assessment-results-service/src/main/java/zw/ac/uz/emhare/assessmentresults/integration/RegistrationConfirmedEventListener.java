package zw.ac.uz.emhare.assessmentresults.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterImportService;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;

/** @author Tinashe K */
@Component
public class RegistrationConfirmedEventListener {
    private final AssessmentResultsIntegrationInbox inbox;
    private final AssessmentRosterImportService rosterImportService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RegistrationConfirmedEventListener(
            AssessmentResultsIntegrationInbox inbox,
            AssessmentRosterImportService rosterImportService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.inbox = inbox;
        this.rosterImportService = rosterImportService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_ASSESSMENT_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        StudentRegistrationConfirmedEvent event = deserialize(payload);
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,
                "student-records-service", payload, clock.instant())) return;
        rosterImportService.importConfirmedRegistration(event);
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private StudentRegistrationConfirmedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, StudentRegistrationConfirmedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Confirmed-registration event payload is invalid.", exception);
        }
    }
}

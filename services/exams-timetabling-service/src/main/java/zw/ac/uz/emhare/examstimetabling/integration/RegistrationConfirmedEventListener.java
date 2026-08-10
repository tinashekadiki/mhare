package zw.ac.uz.emhare.examstimetabling.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;
import zw.ac.uz.emhare.examstimetabling.roster.ExamRosterImportService;

/** @author Tinashe K */
@Component
public class RegistrationConfirmedEventListener {
    private final ExamsTimetablingIntegrationInbox inbox; private final ExamRosterImportService importService;
    private final ObjectMapper objectMapper; private final Clock clock;
    public RegistrationConfirmedEventListener(ExamsTimetablingIntegrationInbox inbox, ExamRosterImportService importService,
            ObjectMapper objectMapper, Clock clock) {
        this.inbox=inbox; this.importService=importService; this.objectMapper=objectMapper; this.clock=clock;
    }
    @RabbitListener(queues=EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EXAMS_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload=new String(message.getBody(),StandardCharsets.UTF_8);
        StudentRegistrationConfirmedEvent event=deserialize(payload);
        if(!inbox.claim(event.eventId(),EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,
                "student-records-service",payload,clock.instant())) return;
        importService.importConfirmedRegistration(event); inbox.markProcessed(event.eventId(),clock.instant());
    }
    private StudentRegistrationConfirmedEvent deserialize(String payload) {
        try{return objectMapper.readValue(payload,StudentRegistrationConfirmedEvent.class);}
        catch(JacksonException exception){throw new IllegalArgumentException("Confirmed-registration payload is invalid.",exception);}
    }
}

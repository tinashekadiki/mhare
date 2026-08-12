package zw.ac.uz.emhare.studentrecords.integration;

import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.StudentRecordsOutboxEvent;
import zw.ac.uz.emhare.studentrecords.infrastructure.persistence.messaging.StudentRecordsOutboxEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentConversionCompletedEvent;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisioningRequestedEvent;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentConversionRequest;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationModule;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationSession;

/** @author Tinashe K */
@Service
public class StudentRecordsIntegrationOutboxService {
    private final StudentRecordsOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StudentRecordsIntegrationOutboxService(
            StudentRecordsOutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueueProvisioningRequests(StudentConversionRequest conversion) {
        Instant occurredAt = clock.instant();
        var student = conversion.getStudent();
        if (conversion.needsFinanceProvisioning()) {
            UUID financeEventId = UUID.randomUUID();
            save(financeEventId,
                    EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT,
                    new StudentFinanceAccountProvisioningRequestedEvent(
                            financeEventId,
                            StudentFinanceAccountProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                            occurredAt,
                            conversion.getId(),
                            student.getId(),
                            student.getStudentNumber(),
                            student.getUserId(),
                            conversion.getSourceOfferId(),
                            student.getPrimaryEmail()),
                    occurredAt);
        }
        if (conversion.needsPortalProvisioning()) {
            UUID portalEventId = UUID.randomUUID();
            save(portalEventId,
                    EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT,
                    new StudentPortalAccessProvisioningRequestedEvent(
                            portalEventId,
                            StudentPortalAccessProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                            occurredAt,
                            conversion.getId(),
                            student.getId(),
                            student.getStudentNumber(),
                            student.getUserId()),
                    occurredAt);
        }
    }

    public void enqueueConversionCompleted(StudentConversionRequest conversion) {
        Instant occurredAt = clock.instant();
        UUID eventId = UUID.randomUUID();
        save(eventId, EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_EVENT,
                new StudentConversionCompletedEvent(
                        eventId, StudentConversionCompletedEvent.CURRENT_SCHEMA_VERSION, occurredAt,
                        conversion.getId(), conversion.getStudent().getId(), conversion.getStudent().getStudentNumber(),
                        conversion.getStudent().getUserId(),
                        conversion.getSourceApplicationId(), conversion.getSourceOfferId(),
                conversion.getProgrammeEnrolment().getId()), occurredAt);
    }

    public void enqueueRegistrationConfirmed(
            RegistrationSession registration, List<RegistrationModule> modules) {
        Instant occurredAt = clock.instant();
        UUID eventId = UUID.randomUUID();
        var student = registration.getStudent();
        var enrolment = registration.getProgrammeEnrolment();
        save(eventId, EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,
                new StudentRegistrationConfirmedEvent(
                        eventId,
                        StudentRegistrationConfirmedEvent.CURRENT_SCHEMA_VERSION,
                        occurredAt,
                        registration.getId(),
                        student.getId(),
                        student.getStudentNumber(),
                        enrolment.getId(),
                        enrolment.getProgrammeId(),
                        enrolment.getProgrammeVersionId(),
                        registration.getOwningAcademicUnitId(),
                        registration.getOwningAcademicUnitCode(),
                        registration.getOwningAcademicUnitName(),
                        registration.getProgrammeLevelId(),
                        registration.getProgrammeLevelCode(),
                        registration.getProgrammeLevelName(),
                        registration.getAcademicPeriodId(),
                        registration.getAcademicPeriodCode(),
                        registration.getAcademicPeriodName(),
                        registration.getAcademicPeriodStartsOn(),
                        registration.getAcademicPeriodEndsOn(),
                        registration.getProgrammePeriodNumber(),
                        modules.stream().map(module -> new StudentRegistrationConfirmedEvent.RegisteredModule(
                                module.getId(), module.getCurriculumModuleId(), module.getModuleId(),
                                module.getModuleCode(), module.getModuleName(), module.getCurriculumModuleType(),
                                module.getCreditValue(), module.getMinimumMarkRequired()))
                                .toList()),
                occurredAt);
    }

    public void enqueueRegistrationActionNotification(
            RegistrationSession registration,
            String requiredAction) {
        var student = registration.getStudent();
        Map<String, String> variables = Map.of(
                "firstName", student.getFirstName(),
                "studentNumber", student.getStudentNumber(),
                "requiredAction", requiredAction);
        enqueueNotification(
                registration,
                "REGISTRATION_ACTION_EMAIL",
                "EMAIL",
                student.getPrimaryEmail(),
                variables);
        enqueueNotification(
                registration,
                "REGISTRATION_ACTION_IN_APP",
                "IN_APP",
                student.getUserId().toString(),
                variables);
    }

    private void enqueueNotification(
            RegistrationSession registration,
            String templateCode,
            String channel,
            String recipientAddress,
            Map<String, String> variables) {
        var student = registration.getStudent();
        String idempotencyKey = "student-records:registration-action:"
                + registration.getId() + ":" + registration.getStatus() + ":" + channel.toLowerCase();
        UUID eventId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) {
            return;
        }
        Instant occurredAt = clock.instant();
        NotificationRequestedEvent notification = new NotificationRequestedEvent(
                eventId,
                NotificationRequestedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                "student-records-service",
                eventId,
                idempotencyKey,
                "REGISTRATION_ACTION",
                templateCode,
                channel,
                "en-ZW",
                student.getUserId(),
                student.getUserId().toString(),
                recipientAddress,
                "HIGH",
                null,
                8,
                variables);
        save(eventId, EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT, notification, occurredAt);
    }

    private void save(UUID eventId, String eventType, Object event, Instant occurredAt) {
        repository.save(new StudentRecordsOutboxEvent(eventId, eventType, eventType, serialize(event), occurredAt));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Student Records integration event could not be serialized.", exception);
        }
    }
}

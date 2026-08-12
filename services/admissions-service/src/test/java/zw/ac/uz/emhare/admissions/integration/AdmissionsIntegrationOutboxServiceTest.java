package zw.ac.uz.emhare.admissions.integration;

import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.AdmissionsOutboxEvent;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.messaging.AdmissionsOutboxEventRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantReferee;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.common.messaging.MissingApplicationDocumentWorkflowRequestedEvent;

/** @author Tinashe K */
class AdmissionsIntegrationOutboxServiceTest {

    @Test
    void applicationSubmissionCreatesIdempotentEmailAndInAppOutboxEvents() throws Exception {
        AdmissionsOutboxEventRepository repository = org.mockito.Mockito.mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-01-15T10:00:00Z");
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(
                repository,
                objectMapper,
                Clock.fixed(occurredAt, ZoneOffset.UTC));
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        Application application = application(applicationId, applicantUserId);

        service.enqueueApplicationSubmittedNotification(application);

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository, times(2)).save(eventCaptor.capture());
        List<AdmissionsOutboxEvent> outboxEvents = eventCaptor.getAllValues();
        assertEquals(
                List.of(
                        EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,
                        EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT),
                outboxEvents.stream().map(AdmissionsOutboxEvent::getRoutingKey).toList());
        List<NotificationRequestedEvent> notifications = outboxEvents.stream()
                .map(event -> deserialize(objectMapper, event.getPayload()))
                .toList();
        assertEquals(List.of("EMAIL", "IN_APP"), notifications.stream().map(NotificationRequestedEvent::channel).toList());
        assertEquals(
                List.of("APPLICATION_SUBMITTED_EMAIL", "APPLICATION_SUBMITTED_IN_APP"),
                notifications.stream().map(NotificationRequestedEvent::templateCode).toList());
        assertEquals(List.of(applicantUserId, applicantUserId), notifications.stream().map(NotificationRequestedEvent::recipientUserId).toList());
        assertEquals(List.of(occurredAt, occurredAt), notifications.stream().map(NotificationRequestedEvent::occurredAt).toList());
        assertNotEquals(notifications.get(0).eventId(), notifications.get(1).eventId());
        assertEquals("EMH-2027-0001", notifications.get(0).variables().get("applicationNumber"));
    }

    @Test
    void rejectedDocumentCreatesHighPriorityEmailAndInAppCorrectionNotifications() throws Exception {
        AdmissionsOutboxEventRepository repository = org.mockito.Mockito.mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-01-15T10:00:00Z");
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(
                repository,
                objectMapper,
                Clock.fixed(occurredAt, ZoneOffset.UTC));
        UUID documentId = UUID.randomUUID();
        UUID verifierUserId = UUID.randomUUID();
        Application application = application(UUID.randomUUID(), UUID.randomUUID());

        service.enqueueMissingDocumentsNotification(
                application,
                "NATIONAL_ID",
                "The identity number is unreadable.",
                documentId,
                4,
                verifierUserId);

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository, times(3)).save(eventCaptor.capture());
        List<AdmissionsOutboxEvent> outboxEvents = eventCaptor.getAllValues();
        List<NotificationRequestedEvent> notifications = outboxEvents.stream()
                .filter(event -> event.getEventType().equals(EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT))
                .map(event -> deserialize(objectMapper, event.getPayload()))
                .toList();
        assertEquals(List.of("EMAIL", "IN_APP"), notifications.stream().map(NotificationRequestedEvent::channel).toList());
        assertEquals(
                List.of("MISSING_DOCUMENTS_EMAIL", "MISSING_DOCUMENTS_IN_APP"),
                notifications.stream().map(NotificationRequestedEvent::templateCode).toList());
        assertEquals(List.of("HIGH", "HIGH"), notifications.stream().map(NotificationRequestedEvent::priority).toList());
        assertEquals(
                "NATIONAL_ID — The identity number is unreadable.",
                notifications.get(0).variables().get("documentList"));
        assertEquals(
                "admissions:missing-document:" + documentId + ":4:email",
                notifications.get(0).idempotencyKey());
        AdmissionsOutboxEvent workflowOutboxEvent = outboxEvents.stream()
                .filter(event -> event.getEventType().equals(
                        EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT))
                .findFirst()
                .orElseThrow();
        MissingApplicationDocumentWorkflowRequestedEvent workflowEvent = objectMapper.readValue(
                workflowOutboxEvent.getPayload(),
                MissingApplicationDocumentWorkflowRequestedEvent.class);
        assertEquals(application.getId(), workflowEvent.applicationId());
        assertEquals(application.getApplicant().getUserId(), workflowEvent.applicantUserId());
        assertEquals(documentId, workflowEvent.documentId());
        assertEquals(4, workflowEvent.documentVersion());
        assertEquals(verifierUserId, workflowEvent.initiatedByUserId());
        assertEquals(application.getAdmissionCycle().getClosesAt(), workflowEvent.dueAt());
    }

    @Test
    void refereeRequestTargetsExternalEmailWithoutRequiringAnEmhareUser() {
        AdmissionsOutboxEventRepository repository = org.mockito.Mockito.mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-01-15T10:00:00Z");
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(
                repository, objectMapper, Clock.fixed(occurredAt, ZoneOffset.UTC));
        Application application = application(UUID.randomUUID(), UUID.randomUUID());
        ApplicantReferee referee = new ApplicantReferee(
                application.getApplicant(), "Dr Tariro Dube", "Dr", "UZ", "Dean",
                "referee@example.test", null);

        service.enqueueRefereeReferenceRequest(
                application, referee, UUID.randomUUID(), "http://localhost:3001/references/token", occurredAt.plusSeconds(86400));

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository).save(eventCaptor.capture());
        NotificationRequestedEvent notification = deserialize(objectMapper, eventCaptor.getValue().getPayload());
        assertEquals("REFEREE_REFERENCE_REQUEST_EMAIL", notification.templateCode());
        assertEquals("referee@example.test", notification.recipientKey());
        assertEquals("referee@example.test", notification.recipientAddress());
        assertNull(notification.recipientUserId());
        assertEquals("http://localhost:3001/references/token", notification.variables().get("responseUrl"));
    }

    private Application application(UUID applicationId, UUID applicantUserId) {
        AdmissionCycle admissionCycle = new AdmissionCycle(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "2027-AUG",
                "2027 August Intake",
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-02-01T00:00:00Z"));
        ApplicationType applicationType = new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
        Applicant applicant = new Applicant(
                applicantUserId,
                "APP-0001",
                "LOCAL",
                "Nyasha",
                "Moyo",
                "nyasha@example.test");
        Application application = new Application(
                admissionCycle,
                applicant,
                applicationType,
                "EMH-2027-0001",
                false);
        ReflectionTestUtils.setField(application, "id", applicationId);
        return application;
    }

    private NotificationRequestedEvent deserialize(ObjectMapper objectMapper, String payload) {
        try {
            return objectMapper.readValue(payload, NotificationRequestedEvent.class);
        } catch (Exception exception) {
            throw new AssertionError("Notification outbox payload could not be read.", exception);
        }
    }
}

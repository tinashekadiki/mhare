package zw.ac.uz.emhare.studentrecords.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfile;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationSession;

/** @author Tinashe K */
class StudentRecordsIntegrationOutboxServiceTest {

    @Test
    void registrationActionCreatesIdempotentEmailAndInAppOutboxEvents() {
        StudentRecordsOutboxEventRepository repository =
                org.mockito.Mockito.mock(StudentRecordsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(StudentRecordsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-02-10T08:30:00Z");
        StudentRecordsIntegrationOutboxService service = new StudentRecordsIntegrationOutboxService(
                repository,
                objectMapper,
                Clock.fixed(occurredAt, ZoneOffset.UTC));
        UUID registrationId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        RegistrationSession registration = org.mockito.Mockito.mock(RegistrationSession.class);
        StudentProfile student = org.mockito.Mockito.mock(StudentProfile.class);
        when(registration.getId()).thenReturn(registrationId);
        when(registration.getStatus()).thenReturn(
                zw.ac.uz.emhare.studentrecords.registration.RegistrationStatus.SUBMITTED);
        when(registration.getStudent()).thenReturn(student);
        when(student.getUserId()).thenReturn(studentUserId);
        when(student.getStudentNumber()).thenReturn("R271234A");
        when(student.getFirstName()).thenReturn("Nyasha");
        when(student.getPrimaryEmail()).thenReturn("nyasha@example.test");

        service.enqueueRegistrationActionNotification(
                registration,
                "Your registration was submitted and is awaiting academic approval.");

        ArgumentCaptor<StudentRecordsOutboxEvent> eventCaptor =
                ArgumentCaptor.forClass(StudentRecordsOutboxEvent.class);
        verify(repository, times(2)).save(eventCaptor.capture());
        List<StudentRecordsOutboxEvent> outboxEvents = eventCaptor.getAllValues();
        assertEquals(
                List.of(
                        EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,
                        EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT),
                outboxEvents.stream().map(StudentRecordsOutboxEvent::getRoutingKey).toList());
        List<NotificationRequestedEvent> notifications = outboxEvents.stream()
                .map(event -> deserialize(objectMapper, event.getPayload()))
                .toList();
        assertEquals(List.of("EMAIL", "IN_APP"),
                notifications.stream().map(NotificationRequestedEvent::channel).toList());
        assertEquals(List.of("REGISTRATION_ACTION_EMAIL", "REGISTRATION_ACTION_IN_APP"),
                notifications.stream().map(NotificationRequestedEvent::templateCode).toList());
        assertEquals(List.of(studentUserId, studentUserId),
                notifications.stream().map(NotificationRequestedEvent::recipientUserId).toList());
        assertEquals(List.of(occurredAt, occurredAt),
                notifications.stream().map(NotificationRequestedEvent::occurredAt).toList());
        assertNotEquals(notifications.get(0).eventId(), notifications.get(1).eventId());
        assertEquals("R271234A", notifications.get(0).variables().get("studentNumber"));
    }

    private NotificationRequestedEvent deserialize(ObjectMapper objectMapper, String payload) {
        try {
            return objectMapper.readValue(payload, NotificationRequestedEvent.class);
        } catch (Exception exception) {
            throw new AssertionError("Notification outbox payload could not be read.", exception);
        }
    }
}

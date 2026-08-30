package zw.ac.uz.emhare.examstimetabling.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.examstimetabling.ExamTestData.RegistrationEvidence;
import zw.ac.uz.emhare.examstimetabling.roster.ExamRosterImportService;

/**
 * @author Tinashe K
 */
class ExamRegistrationEventDeliveryTest {
  private final ExamsTimetablingIntegrationInbox inbox =
      mock(ExamsTimetablingIntegrationInbox.class);
  private final ExamRosterImportService roster = mock(ExamRosterImportService.class);
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final RegistrationConfirmedEventListener listener =
      new RegistrationConfirmedEventListener(
          inbox, roster, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void registrationEventIsClaimedBeforeImportAndAcknowledgedAfterSuccess() {
    var event = new RegistrationEvidence().event();
    String payload = mapper.writeValueAsString(event);
    when(inbox.claim(
            event.eventId(),
            EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,
            "student-records-service",
            payload,
            NOW))
        .thenReturn(true);
    listener.receive(new Message(payload.getBytes(StandardCharsets.UTF_8)));
    var ordered = inOrder(inbox, roster);
    ordered
        .verify(inbox)
        .claim(
            event.eventId(),
            EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,
            "student-records-service",
            payload,
            NOW);
    ordered.verify(roster).importConfirmedRegistration(event);
    ordered.verify(inbox).markProcessed(event.eventId(), NOW);
  }

  @Test
  void previouslyClaimedEventIsNotImportedOrAcknowledgedAgain() {
    var event = new RegistrationEvidence().event();
    listener.receive(
        new Message(mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8)));
    verifyNoInteractions(roster);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void failedRosterImportLeavesNoProcessedAcknowledgement() {
    var event = new RegistrationEvidence().event();
    when(inbox.claim(any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
    doThrow(new IllegalArgumentException("Invalid registration evidence"))
        .when(roster)
        .importConfirmedRegistration(event);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            listener.receive(
                new Message(mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8))));
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-json", "[]", "{\"eventId\":\"not-a-uuid\"}"})
  void malformedPayloadsAreRejectedBeforeInboxClaim(String payload) {
    assertThrows(
        IllegalArgumentException.class,
        () -> listener.receive(new Message(payload.getBytes(StandardCharsets.UTF_8))));
    verifyNoInteractions(inbox, roster);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void inboxClaimUsesConflictSafeInsertAndReturnsWhetherThisDeliveryWon(int affected) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID event = UUID.randomUUID();
    when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(affected);
    var repository = new ExamsTimetablingIntegrationInbox(jdbc);
    assertEquals(
        affected == 1,
        repository.claim(event, "registration", "student-records-service", "{}", NOW));
    verify(jdbc)
        .update(
            contains("ON CONFLICT(event_id) DO NOTHING"),
            eq(event),
            eq("registration"),
            eq("student-records-service"),
            eq("{}"),
            eq(Timestamp.from(NOW)));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void processedAcknowledgementMustUpdateExactlyOneUnprocessedClaim(int affected) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID event = UUID.randomUUID();
    when(jdbc.update(anyString(), any(Timestamp.class), any(UUID.class))).thenReturn(affected);
    var repository = new ExamsTimetablingIntegrationInbox(jdbc);
    if (affected == 1) assertDoesNotThrow(() -> repository.markProcessed(event, NOW));
    else assertThrows(IllegalStateException.class, () -> repository.markProcessed(event, NOW));
    verify(jdbc).update(contains("processed_at IS NULL"), eq(Timestamp.from(NOW)), eq(event));
  }
}

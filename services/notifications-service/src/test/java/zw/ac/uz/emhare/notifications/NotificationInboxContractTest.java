package zw.ac.uz.emhare.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.notifications.api.model.NotificationApiModels.QueueNotification;
import zw.ac.uz.emhare.notifications.domain.model.NotificationEventInbox;
import zw.ac.uz.emhare.notifications.domain.model.NotificationTemplate;
import zw.ac.uz.emhare.notifications.infrastructure.persistence.NotificationEventInboxRepository;

/**
 * @author Tinashe K
 */
class NotificationInboxContractTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final UUID eventId = UUID.randomUUID(), sourceId = UUID.randomUUID();
  private final ObjectMapper mapper = new ObjectMapper();
  private NotificationEventInboxRepository repository;
  private NotificationService service;
  private NotificationInboxProcessor processor;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationEventInboxRepository.class);
    service = mock(NotificationService.class);
    processor =
        new NotificationInboxProcessor(
            repository, service, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "occurredAt",
        "sourceEventId",
        "sourceService",
        "idempotencyKey",
        "eventType",
        "templateCode",
        "channel",
        "locale",
        "recipientKey",
        "recipientAddress",
        "priority"
      })
  void missingRequiredContractFieldIsDeadLetteredWithoutCreatingNotification(String field) {
    ObjectNode tree = (ObjectNode) mapper.valueToTree(event());
    tree.putNull(field);
    var inbox = inbox(mapper.writeValueAsString(tree));
    processor.process(inbox, NOW);
    assertEquals(NotificationEventInbox.Status.DEAD, inbox.getStatus());
    assertTrue(inbox.getProcessingError().contains("contract is invalid"));
    assertNull(inbox.getNextAttemptAt());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sourceService",
        "idempotencyKey",
        "eventType",
        "templateCode",
        "channel",
        "locale",
        "recipientKey",
        "recipientAddress",
        "priority"
      })
  void blankRequiredContractFieldIsRejected(String field) {
    ObjectNode tree = (ObjectNode) mapper.valueToTree(event());
    tree.put(field, " ");
    var inbox = inbox(mapper.writeValueAsString(tree));
    processor.process(inbox, NOW);
    assertEquals(NotificationEventInbox.Status.DEAD, inbox.getStatus());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "sourceService",
        "schemaVersion",
        "tooFewAttempts",
        "tooManyAttempts",
        "channel",
        "priority"
      })
  void incompatibleOrMisroutedContractIsPermanentlyRejected(String defect) {
    ObjectNode tree = (ObjectNode) mapper.valueToTree(event());
    switch (defect) {
      case "eventId" -> tree.put("eventId", UUID.randomUUID().toString());
      case "sourceService" -> tree.put("sourceService", "finance-service");
      case "schemaVersion" -> tree.put("schemaVersion", 999);
      case "tooFewAttempts" -> tree.put("maximumAttempts", 0);
      case "tooManyAttempts" -> tree.put("maximumAttempts", 21);
      case "channel" -> tree.put("channel", "FAX");
      case "priority" -> tree.put("priority", "IMMEDIATE");
      default -> throw new AssertionError(defect);
    }
    var inbox = inbox(mapper.writeValueAsString(tree));
    processor.process(inbox, NOW);
    assertEquals(NotificationEventInbox.Status.DEAD, inbox.getStatus());
    assertEquals(1, inbox.getAttemptCount());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void validContractNormalizesEnumsAndPreservesSourceIdentityAndOptionalCollections(
      boolean missingCollections) {
    ObjectNode tree = (ObjectNode) mapper.valueToTree(event());
    tree.put("channel", " email ");
    tree.put("priority", " high ");
    if (missingCollections) {
      tree.putNull("variables");
      tree.putNull("attachments");
    }
    var inbox = inbox(mapper.writeValueAsString(tree));
    when(repository.lockDue(eq(NOW), any())).thenReturn(List.of(inbox));
    processor.processDue();
    var captor = org.mockito.ArgumentCaptor.forClass(QueueNotification.class);
    verify(service).queue(captor.capture());
    var command = captor.getValue();
    assertEquals(sourceId, command.sourceEventId());
    assertEquals("admissions:application:1", command.idempotencyKey());
    assertEquals(NotificationTemplate.Channel.EMAIL, command.channel());
    assertEquals(missingCollections ? Map.of() : Map.of("name", "Tariro"), command.variables());
    assertTrue(command.attachments().isEmpty());
    assertEquals(NotificationEventInbox.Status.PROCESSED, inbox.getStatus());
    assertEquals(NOW, inbox.getProcessedAt());
    assertNull(inbox.getProcessingError());
  }

  @Test
  void malformedJsonIsPermanentButUnavailableTemplateRetriesWithBackoff() {
    var invalid = inbox("{broken");
    processor.process(invalid, NOW);
    assertEquals(NotificationEventInbox.Status.DEAD, invalid.getStatus());
    assertTrue(invalid.getProcessingError().contains("not valid JSON"));
    when(service.queue(any())).thenThrow(new IllegalStateException("Template not yet deployed"));
    var pending = inbox(mapper.writeValueAsString(event()));
    processor.process(pending, NOW);
    assertEquals(NotificationEventInbox.Status.RETRY_SCHEDULED, pending.getStatus());
    assertEquals(NOW.plusSeconds(30), pending.getNextAttemptAt());
    assertNull(pending.getProcessedAt());
    new NotificationInboxProcessor(
            repository, service, mapper, Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC))
        .process(pending, NOW.plusSeconds(30));
    assertEquals(2, pending.getAttemptCount());
    assertEquals(NOW.plusSeconds(90), pending.getNextAttemptAt());
  }

  @Test
  void applicationValidationFailureIsPermanentAndCannotBeProcessedAgain() {
    when(service.queue(any())).thenThrow(new IllegalArgumentException("Missing template variable"));
    var inbox = inbox(mapper.writeValueAsString(event()));
    processor.process(inbox, NOW);
    assertEquals(NotificationEventInbox.Status.DEAD, inbox.getStatus());
    assertThrows(IllegalStateException.class, () -> processor.process(inbox, NOW));
    verify(service, times(1)).queue(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "x"})
  void failedEventKeepsSafeDiagnosticAndCanBeManuallyRetriedWithAuditEvidence(String detail) {
    var inbox = new NotificationEventInbox("admissions-service", eventId, "intent", "{}", NOW, 1);
    inbox.startAttempt(NOW);
    inbox.recordFailure(new IllegalStateException(detail), NOW, false);
    assertEquals(NotificationEventInbox.Status.DEAD, inbox.getStatus());
    assertNotNull(inbox.getProcessingError());
    UUID actor = UUID.randomUUID();
    inbox.retryNow(actor, "  Corrected the dependency configuration  ", NOW, 0);
    assertEquals(NotificationEventInbox.Status.RETRY_SCHEDULED, inbox.getStatus());
    assertEquals(6, inbox.getMaxAttempts());
    assertEquals(actor, inbox.getManualRetryByUserId());
    assertEquals("Corrected the dependency configuration", inbox.getManualRetryReason());
    inbox.startAttempt(NOW);
    inbox.markProcessed(NOW);
    assertEquals(2, inbox.getAttemptCount());
    assertEquals(NotificationEventInbox.Status.PROCESSED, inbox.getStatus());
  }

  @Test
  void invalidInboxLifecycleAndRetryEvidenceFailClosed() {
    var inbox = inbox("{}");
    assertThrows(IllegalStateException.class, () -> inbox.markProcessed(NOW));
    assertThrows(
        IllegalStateException.class,
        () -> inbox.retryNow(UUID.randomUUID(), "Valid reason", NOW, 0));
    inbox.startAttempt(NOW);
    assertThrows(IllegalStateException.class, () -> inbox.startAttempt(NOW));
    inbox.recordFailure(new IllegalStateException("x".repeat(1500)), NOW, true);
    assertEquals(1000, inbox.getProcessingError().length());
    assertThrows(
        IllegalStateException.class,
        () -> inbox.retryNow(UUID.randomUUID(), "Valid reason", NOW, 9));
    assertThrows(NullPointerException.class, () -> inbox.retryNow(null, "Valid reason", NOW, 0));
    assertThrows(
        NullPointerException.class,
        () -> inbox.retryNow(UUID.randomUUID(), "Valid reason", null, 0));
    assertThrows(
        IllegalArgumentException.class, () -> inbox.retryNow(UUID.randomUUID(), "short", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> inbox.retryNow(UUID.randomUUID(), "x".repeat(1001), NOW, 0));
  }

  @Test
  void inboxRequiresDurableIdentityReceivedTimeAndPositiveAttemptLimit() {
    assertThrows(
        NullPointerException.class,
        () -> new NotificationEventInbox("source", null, "event", "{}", NOW, 1));
    assertThrows(
        NullPointerException.class,
        () -> new NotificationEventInbox("source", eventId, "event", "{}", null, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new NotificationEventInbox("source", eventId, "event", "{}", NOW, 0));
  }

  private NotificationEventInbox inbox(String payload) {
    return new NotificationEventInbox(
        "admissions-service", eventId, "notification.requested", payload, NOW, 3);
  }

  private NotificationRequestedEvent event() {
    return new NotificationRequestedEvent(
        eventId,
        NotificationRequestedEvent.CURRENT_SCHEMA_VERSION,
        NOW,
        "admissions-service",
        sourceId,
        "admissions:application:1",
        "APPLICATION_SUBMITTED",
        "APPLICATION_SUBMITTED",
        "EMAIL",
        "en-ZW",
        UUID.randomUUID(),
        "applicant",
        "applicant@example.test",
        "HIGH",
        NOW,
        5,
        Map.of("name", "Tariro"));
  }
}

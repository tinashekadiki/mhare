package zw.ac.uz.emhare.coreidentity.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.CoreIdentityOutboxEvent;
import zw.ac.uz.emhare.coreidentity.infrastructure.persistence.messaging.CoreIdentityOutboxEventRepository;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.StudentPortalAccessProvisioning;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

/**
 * Repository and broker boundary tests; not database or broker integration tests. @author Tinashe K
 */
class CoreMessagingReliabilityTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final ObjectMapper mapper = new ObjectMapper();
  private final CoreIdentityOutboxEventRepository repository =
      mock(CoreIdentityOutboxEventRepository.class);
  private final CoreIdentityIntegrationOutboxService outbox =
      new CoreIdentityIntegrationOutboxService(repository, mapper, clock);

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void inbox_shouldReturnWhetherItsAtomicInsertClaimedTheEvent(int inserted) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID event = UUID.randomUUID();
    when(jdbc.update(
            anyString(),
            eq(event),
            eq("event"),
            eq("admissions-service"),
            eq("{}"),
            eq(Timestamp.from(NOW))))
        .thenReturn(inserted);
    assertEquals(
        inserted == 1,
        new CoreIdentityIntegrationInbox(jdbc)
            .claim(event, "event", "admissions-service", "{}", NOW));
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .update(
            sql.capture(),
            eq(event),
            eq("event"),
            eq("admissions-service"),
            eq("{}"),
            eq(Timestamp.from(NOW)));
    assertTrue(sql.getValue().contains("ON CONFLICT (event_id) DO NOTHING"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void inbox_shouldRequireExactlyOneUnprocessedClaimForAcknowledgement(int updated) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID event = UUID.randomUUID();
    when(jdbc.update(anyString(), eq(Timestamp.from(NOW)), eq(event))).thenReturn(updated);
    CoreIdentityIntegrationInbox inbox = new CoreIdentityIntegrationInbox(jdbc);
    if (updated == 1) assertDoesNotThrow(() -> inbox.markProcessed(event, NOW));
    else assertThrows(IllegalStateException.class, () -> inbox.markProcessed(event, NOW));
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), eq(Timestamp.from(NOW)), eq(event));
    assertTrue(sql.getValue().contains("processed_at IS NULL"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void workflowNotifications_shouldEmitRecipientResolvedContractsOncePerChannel(
      boolean hasDueDate) {
    PlatformUser recipient = user("reviewer@example.test");
    WorkflowTask task = task(recipient, hasDueDate ? NOW.plusSeconds(3600) : null);
    outbox.enqueueWorkflowTaskNotifications(
        task, List.of(recipient, recipient, user(null), user(" ")));
    ArgumentCaptor<CoreIdentityOutboxEvent> captured =
        ArgumentCaptor.forClass(CoreIdentityOutboxEvent.class);
    verify(repository, times(2)).save(captured.capture());
    for (CoreIdentityOutboxEvent persisted : captured.getAllValues()) {
      NotificationRequestedEvent event =
          mapper.readValue(persisted.getPayload(), NotificationRequestedEvent.class);
      assertEquals(EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT, persisted.getEventType());
      assertEquals(persisted.getEventType(), persisted.getRoutingKey());
      assertEquals(persisted.getId(), event.eventId());
      assertEquals(NotificationRequestedEvent.CURRENT_SCHEMA_VERSION, event.schemaVersion());
      assertEquals("core-identity-service", event.sourceService());
      assertEquals(recipient.getId(), event.recipientUserId());
      assertEquals(
          event.channel().equals("EMAIL") ? recipient.getEmail() : recipient.getId().toString(),
          event.recipientAddress());
      assertEquals("WORKFLOW_TASK_" + event.channel(), event.templateCode());
      assertEquals(task.getDueAt(), event.scheduledAt());
      assertEquals(
          hasDueDate ? task.getDueAt().toString() : "No fixed due date",
          event.variables().get("dueAt"));
      assertEquals("Review application", event.variables().get("taskTitle"));
      assertEquals(
          UUID.nameUUIDFromBytes(event.idempotencyKey().getBytes(StandardCharsets.UTF_8)),
          persisted.getId());
    }
  }

  @Test
  void workflowNotifications_shouldSkipAlreadyPersistedChannelIds() {
    PlatformUser recipient = user("reviewer@example.test");
    when(repository.existsById(any())).thenReturn(true);
    outbox.enqueueWorkflowTaskNotifications(task(recipient, null), List.of(recipient));
    verify(repository, times(2)).existsById(any());
    verify(repository, never()).save(any());
  }

  @Test
  void studentAccess_shouldSerializeTheProvisionedIdentityAndConversionCorrelation() {
    PlatformUser user = user("student@example.test");
    var request =
        new StudentPortalAccessProvisioningRequestedEvent(
            UUID.randomUUID(),
            1,
            NOW,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "R260001",
            user.getId());
    var assignment =
        new UserRoleAssignment(
            user, new Role("STUDENT", "Student", RoleScope.SYSTEM, true), null, NOW);
    outbox.enqueueStudentPortalAccessProvisioned(
        new StudentPortalAccessProvisioning(request, user, assignment, NOW));
    ArgumentCaptor<CoreIdentityOutboxEvent> saved =
        ArgumentCaptor.forClass(CoreIdentityOutboxEvent.class);
    verify(repository).save(saved.capture());
    var event =
        mapper.readValue(saved.getValue().getPayload(), StudentPortalAccessProvisionedEvent.class);
    assertEquals(request.conversionRequestId(), event.conversionRequestId());
    assertEquals(request.studentId(), event.studentId());
    assertEquals(user.getId(), event.userId());
    assertTrue(event.successful());
    assertNull(event.failureReason());
    assertEquals(
        EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_EVENT,
        saved.getValue().getEventType());
    assertEquals(NOW, event.occurredAt());
    assertEquals(saved.getValue().getId(), event.eventId());
  }

  @Test
  void serializationFailure_shouldNotPersistAnIncompleteMessage() {
    ObjectMapper failedMapper = mock(ObjectMapper.class);
    when(failedMapper.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
    PlatformUser recipient = user("reviewer@example.test");
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                new CoreIdentityIntegrationOutboxService(repository, failedMapper, clock)
                    .enqueueWorkflowTaskNotifications(task(recipient, null), List.of(recipient)));
    assertEquals("Core Identity integration event could not be serialized.", failure.getMessage());
    verify(repository, never()).save(any());
  }

  @Test
  void dispatch_shouldPublishExactMessageAndRequireBrokerConfirmationBeforeSuccess() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    CoreIdentityOutboxEvent event =
        new CoreIdentityOutboxEvent(UUID.randomUUID(), "core.event", "{\"name\":\"Tariro\"}", NOW);
    when(repository.lockNextDispatchBatch(NOW)).thenReturn(List.of(event));
    when(rabbit.invoke(any()))
        .thenAnswer(
            invocation -> {
              RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
              return callback.doInRabbit(rabbit);
            });
    new CoreIdentityOutboxDispatcher(repository, rabbit, clock).dispatchPendingEvents();
    ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
    var ordered = inOrder(rabbit);
    ordered
        .verify(rabbit)
        .send(eq(EmhareMessagingTopology.EVENTS_EXCHANGE), eq("core.event"), message.capture());
    ordered.verify(rabbit).waitForConfirmsOrDie(5000L);
    assertEquals(
        event.getPayload(), new String(message.getValue().getBody(), StandardCharsets.UTF_8));
    assertEquals(
        event.getId().toString(), message.getValue().getMessageProperties().getMessageId());
    assertEquals("application/json", message.getValue().getMessageProperties().getContentType());
    assertEquals(
        "core-identity-service",
        message.getValue().getMessageProperties().getHeader("source-service"));
    assertEquals("PUBLISHED", ReflectionTestUtils.getField(event, "status"));
    assertEquals(NOW, ReflectionTestUtils.getField(event, "publishedAt"));
  }

  @Test
  void dispatch_shouldRetainFailedMessageForRetryAndContinueItsBatch() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    CoreIdentityOutboxEvent failed =
        new CoreIdentityOutboxEvent(UUID.randomUUID(), "core.first", "{}", NOW);
    CoreIdentityOutboxEvent succeeding =
        new CoreIdentityOutboxEvent(UUID.randomUUID(), "core.second", "{}", NOW);
    when(repository.lockNextDispatchBatch(NOW)).thenReturn(List.of(failed, succeeding));
    when(rabbit.invoke(any())).thenThrow(new AmqpException("Unavailable")).thenReturn(null);
    new CoreIdentityOutboxDispatcher(repository, rabbit, clock).dispatchPendingEvents();
    assertEquals("PENDING", ReflectionTestUtils.getField(failed, "status"));
    assertEquals(1, ReflectionTestUtils.getField(failed, "attemptCount"));
    assertEquals(NOW.plusSeconds(2), ReflectionTestUtils.getField(failed, "nextAttemptAt"));
    assertNull(ReflectionTestUtils.getField(failed, "publishedAt"));
    assertEquals("PUBLISHED", ReflectionTestUtils.getField(succeeding, "status"));
  }

  @Test
  void retries_shouldBoundBackoffAndErrorSizeThenDeadLetterAfterTwentyFailures() {
    CoreIdentityOutboxEvent event =
        new CoreIdentityOutboxEvent(UUID.randomUUID(), "core.event", "{}", NOW);
    for (int attempt = 1; attempt <= 20; attempt++) {
      event.scheduleRetry(NOW, new AmqpException("x".repeat(1100)));
      assertEquals(attempt, ReflectionTestUtils.getField(event, "attemptCount"));
      assertEquals("x".repeat(1000), ReflectionTestUtils.getField(event, "lastError"));
      assertEquals(
          attempt == 20 ? "DEAD" : "PENDING", ReflectionTestUtils.getField(event, "status"));
      assertEquals(
          attempt == 20 ? NOW : NOW.plusSeconds(1L << Math.min(attempt, 8)),
          ReflectionTestUtils.getField(event, "nextAttemptAt"));
    }
    CoreIdentityOutboxEvent recovered =
        new CoreIdentityOutboxEvent(UUID.randomUUID(), "core.event", "{}", NOW);
    recovered.scheduleRetry(NOW, new AmqpException("Temporary failure"));
    recovered.markPublished(NOW.plusSeconds(2));
    assertNull(ReflectionTestUtils.getField(recovered, "lastError"));
    assertEquals("PUBLISHED", ReflectionTestUtils.getField(recovered, "status"));
  }

  private PlatformUser user(String email) {
    PlatformUser user = new PlatformUser(UUID.randomUUID(), "reviewer", email, "Reviewer");
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    return user;
  }

  private WorkflowTask task(PlatformUser recipient, Instant due) {
    WorkflowInstance workflow =
        new WorkflowInstance(
            "ADMISSIONS_REVIEW",
            "APPLICATION",
            UUID.randomUUID(),
            "EMH-001",
            "Review application",
            recipient.getId(),
            NOW);
    WorkflowTask task =
        new WorkflowTask(
            workflow,
            "TASK-001",
            "Review application",
            "Verify evidence",
            recipient,
            null,
            WorkflowScopeType.INSTITUTION,
            null,
            due);
    ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
    return task;
  }
}

package zw.ac.uz.emhare.studentrecords.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.StudentRecordsOutboxEvent;
import zw.ac.uz.emhare.studentrecords.infrastructure.persistence.messaging.StudentRecordsOutboxEventRepository;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationModuleOption;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

/** Tests persisted delivery state and actual serialized integration contracts. @author Tinashe K */
class StudentOutboxDeliveryContractTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final ObjectMapper mapper = new ObjectMapper();
  private StudentRecordsOutboxEventRepository repository;
  private StudentRecordsIntegrationOutboxService service;
  private StudentConversionRequest conversion;
  private RegistrationSession registration;
  private RegistrationModule registeredModule;

  @BeforeEach
  void setUp() {
    repository = mock(StudentRecordsOutboxEventRepository.class);
    service = new StudentRecordsIntegrationOutboxService(repository, mapper, clock);
    var event =
        new AcceptedOfferReadyForConversionEvent(
            UUID.randomUUID(),
            2,
            NOW,
            UUID.randomUUID(),
            "APP-26",
            UUID.randomUUID(),
            "OFR-26",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "APL-26",
            "LOCAL",
            "Tariro",
            "Moyo",
            "tariro@example.test",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BSC",
            "Computing",
            UUID.randomUUID(),
            LocalDate.of(2026, 8, 1));
    var student = persisted(new StudentProfile("R260001", event));
    var enrolment = persisted(new StudentProgrammeEnrolment(student, event));
    conversion =
        persisted(
            new StudentConversionRequest(
                event.eventId(), event.applicationId(), event.offerId(), student, enrolment, NOW));
    var module =
        new RegistrationModuleOption(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "CSC101",
            "Foundations",
            "COMPULSORY",
            BigDecimal.valueOf(12),
            BigDecimal.valueOf(50),
            1);
    var catalogue =
        new RegistrationCatalogue(
            UUID.randomUUID(),
            "2026-S1",
            "Semester one",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 12, 31),
            event.programmeVersionId(),
            event.programmeId(),
            "BSC",
            "Computing",
            "2026.1",
            UUID.randomUUID(),
            "SCI",
            "Science",
            UUID.randomUUID(),
            "UG",
            "Undergraduate",
            1,
            List.of(module));
    registration =
        persisted(
            new RegistrationSession(
                "R260001", student, enrolment, catalogue, RegistrationType.NORMAL, NOW));
    registeredModule =
        persisted(
            new RegistrationModule(registration, module, ModuleSelectionSource.AUTO_COMPULSORY));
  }

  @Test
  void initialProvisioningPayloadsRouteToFinanceAndIdentityWithImmutableSourceIds() {
    service.enqueueProvisioningRequests(conversion);
    var captured = capture(2);
    var finance =
        mapper.readValue(
            captured.getFirst().getPayload(),
            StudentFinanceAccountProvisioningRequestedEvent.class);
    var portal =
        mapper.readValue(
            captured.getLast().getPayload(), StudentPortalAccessProvisioningRequestedEvent.class);
    assertEquals(
        EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT,
        captured.getFirst().getRoutingKey());
    assertEquals(
        EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT,
        captured.getLast().getRoutingKey());
    assertEquals(conversion.getId(), finance.conversionRequestId());
    assertEquals(conversion.getStudent().getUserId(), portal.userId());
    assertEquals(conversion.getSourceOfferId(), finance.sourceOfferId());
    assertEquals("R260001", finance.studentNumber());
    assertEquals("tariro@example.test", finance.primaryEmail());
    assertEquals(NOW, portal.occurredAt());
    assertNotEquals(finance.eventId(), portal.eventId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"finance", "portal", "both"})
  void provisioningDoesNotDispatchAlreadyCompletedOwners(String complete) {
    if (!complete.equals("portal")) conversion.recordFinanceProvisioning(true, null);
    if (!complete.equals("finance")) conversion.recordPortalProvisioning(true, null);
    service.enqueueProvisioningRequests(conversion);
    if (complete.equals("both")) verifyNoInteractions(repository);
    else
      assertEquals(
          complete.equals("finance")
              ? EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT
              : EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT,
          capture(1).getFirst().getEventType());
  }

  @Test
  void completedConversionReferencesTheSameStudentOfferAndProgrammeEnrolment() {
    conversion.recordFinanceProvisioning(true, null);
    conversion.recordPortalProvisioning(true, null);
    conversion.complete(NOW);
    service.enqueueConversionCompleted(conversion);
    var event =
        mapper.readValue(capture(1).getFirst().getPayload(), StudentConversionCompletedEvent.class);
    assertEquals(conversion.getSourceApplicationId(), event.applicationId());
    assertEquals(conversion.getSourceOfferId(), event.offerId());
    assertEquals(conversion.getProgrammeEnrolment().getId(), event.programmeEnrolmentId());
    assertEquals(conversion.getStudent().getId(), event.studentId());
  }

  @Test
  void confirmedRosterCarriesTheAuthoritativeModuleAndAcademicSnapshot() {
    registration.submit("Ready", NOW, 0);
    registration.approveAcademically(UUID.randomUUID(), "Reviewed", NOW, 0);
    registration.confirm(UUID.randomUUID(), "Confirmed", NOW, 0);
    service.enqueueRegistrationConfirmed(registration, List.of(registeredModule));
    var event =
        mapper.readValue(
            capture(1).getFirst().getPayload(), StudentRegistrationConfirmedEvent.class);
    assertEquals(registration.getId(), event.registrationSessionId());
    assertEquals(registration.getProgrammeVersionId(), event.programmeVersionId());
    assertEquals(registration.getOwningAcademicUnitId(), event.owningAcademicUnitId());
    assertEquals("SCI", event.owningAcademicUnitCode());
    assertEquals("UG", event.programmeLevelCode());
    assertEquals("2026-S1", event.academicPeriodCode());
    assertEquals(registration.getAcademicPeriodEndsOn(), event.academicPeriodEndsOn());
    assertEquals(1, event.modules().size());
    assertEquals(
        registeredModule.getCurriculumModuleId(), event.modules().getFirst().curriculumModuleId());
    assertEquals(registeredModule.getCreditValue(), event.modules().getFirst().creditValue());
  }

  @Test
  void registrationNotificationsAreIdempotentPerStatusAndChannel() {
    Set<UUID> saved = new HashSet<>();
    when(repository.existsById(any()))
        .thenAnswer(invocation -> saved.contains(invocation.getArgument(0)));
    when(repository.save(any(StudentRecordsOutboxEvent.class)))
        .thenAnswer(
            invocation -> {
              StudentRecordsOutboxEvent event = invocation.getArgument(0);
              saved.add(event.getId());
              return event;
            });
    service.enqueueRegistrationActionNotification(registration, "Complete the declaration");
    service.enqueueRegistrationActionNotification(registration, "Complete the declaration");
    registration.submit("Ready", NOW, 0);
    service.enqueueRegistrationActionNotification(registration, "Await academic review");
    var captured = capture(4);
    var first =
        mapper.readValue(captured.getFirst().getPayload(), NotificationRequestedEvent.class);
    var submitted =
        mapper.readValue(captured.get(2).getPayload(), NotificationRequestedEvent.class);
    assertEquals("EMAIL", first.channel());
    assertEquals("tariro@example.test", first.recipientAddress());
    assertEquals("Complete the declaration", first.variables().get("requiredAction"));
    assertEquals("Await academic review", submitted.variables().get("requiredAction"));
    assertNotEquals(first.eventId(), submitted.eventId());
    assertEquals(4, saved.size());
  }

  @Test
  void serializationFailureCannotPersistAnInvalidIntegrationPayload() {
    ObjectMapper broken = mock(ObjectMapper.class);
    when(broken.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
    var target = new StudentRecordsIntegrationOutboxService(repository, broken, clock);
    assertThrows(IllegalStateException.class, () -> target.enqueueConversionCompleted(conversion));
    verify(repository, never()).save(any());
  }

  @Test
  void dispatcherPublishesOnlyAfterBrokerConfirmationAndIncludesRoutingHeaders() {
    var event = deliveryEvent();
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    when(repository.lockNextDispatchBatch(NOW)).thenReturn(List.of(event));
    when(rabbit.invoke(any()))
        .thenAnswer(
            invocation -> {
              RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
              return callback.doInRabbit(rabbit);
            });
    new StudentRecordsOutboxDispatcher(repository, rabbit, clock).dispatchPendingEvents();
    ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
    var order = inOrder(rabbit);
    order.verify(rabbit).invoke(any());
    order
        .verify(rabbit)
        .send(eq(EmhareMessagingTopology.EVENTS_EXCHANGE), eq("routing-key"), message.capture());
    order.verify(rabbit).waitForConfirmsOrDie(5000L);
    assertEquals(
        event.getId().toString(), message.getValue().getMessageProperties().getMessageId());
    assertEquals(
        "student-records-service",
        message.getValue().getMessageProperties().getHeader("source-service"));
    assertEquals("application/json", message.getValue().getMessageProperties().getContentType());
    assertEquals(
        "{}", new String(message.getValue().getBody(), java.nio.charset.StandardCharsets.UTF_8));
    assertEquals("PUBLISHED", ReflectionTestUtils.getField(event, "status"));
    assertEquals(NOW, ReflectionTestUtils.getField(event, "publishedAt"));
  }

  @Test
  void dispatcherRetainsUnconfirmedMessagesForBoundedRetry() {
    var event = deliveryEvent();
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    when(repository.lockNextDispatchBatch(NOW)).thenReturn(List.of(event));
    when(rabbit.invoke(any()))
        .thenAnswer(
            invocation -> {
              RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
              return callback.doInRabbit(rabbit);
            });
    doThrow(new AmqpException("Broker did not confirm")).when(rabbit).waitForConfirmsOrDie(5000L);
    new StudentRecordsOutboxDispatcher(repository, rabbit, clock).dispatchPendingEvents();
    var order = inOrder(rabbit);
    order
        .verify(rabbit)
        .send(eq(EmhareMessagingTopology.EVENTS_EXCHANGE), eq("routing-key"), any(Message.class));
    order.verify(rabbit).waitForConfirmsOrDie(5000L);
    assertEquals("PENDING", ReflectionTestUtils.getField(event, "status"));
    assertEquals(1, ReflectionTestUtils.getField(event, "attemptCount"));
    assertNull(ReflectionTestUtils.getField(event, "publishedAt"));
    assertEquals(NOW.plusSeconds(2), ReflectionTestUtils.getField(event, "nextAttemptAt"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"short", "long", "null"})
  void exhaustedRetriesKeepBoundedDiagnosticEvidenceForOperators(String message) {
    var event = deliveryEvent();
    String detail =
        message.equals("null") ? null : message.equals("long") ? "x".repeat(1100) : "Unavailable";
    for (int attempt = 1; attempt <= 20; attempt++)
      event.scheduleRetry(NOW, new IllegalStateException(detail));
    assertEquals("DEAD", ReflectionTestUtils.getField(event, "status"));
    assertEquals(20, ReflectionTestUtils.getField(event, "attemptCount"));
    assertEquals(NOW, ReflectionTestUtils.getField(event, "nextAttemptAt"));
    assertEquals(
        message.equals("null")
            ? "IllegalStateException"
            : message.equals("long") ? "x".repeat(1000) : detail,
        ReflectionTestUtils.getField(event, "lastError"));
    event.markPublished(NOW);
    assertNull(ReflectionTestUtils.getField(event, "lastError"));
  }

  private StudentRecordsOutboxEvent deliveryEvent() {
    return new StudentRecordsOutboxEvent(UUID.randomUUID(), "event-type", "routing-key", "{}", NOW);
  }

  private List<StudentRecordsOutboxEvent> capture(int count) {
    ArgumentCaptor<StudentRecordsOutboxEvent> captor =
        ArgumentCaptor.forClass(StudentRecordsOutboxEvent.class);
    verify(repository, times(count)).save(captor.capture());
    return captor.getAllValues();
  }

  private static <T> T persisted(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

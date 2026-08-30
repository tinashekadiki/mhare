package zw.ac.uz.emhare.admissions.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationService;
import zw.ac.uz.emhare.admissions.application.AdmissionsRollingWorkflowService;
import zw.ac.uz.emhare.admissions.application.AdmissionsSelectionOfferService;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.StudentConversionCompletedEvent;

/** Finance and Student Records inbox contract validation and processing order. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsInboundWorkflowContractTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private AdmissionsIntegrationInbox inbox;
  @Mock private AdmissionsApplicationService applications;
  @Mock private AdmissionsRollingWorkflowService rolling;
  @Mock private AdmissionsSelectionOfferService selection;
  private final ObjectMapper mapper = new ObjectMapper();
  private StudentConversionCompletedEventListener conversions;
  private AdmissionsPaymentReferenceEventListener payments;
  private final UUID eventId = UUID.randomUUID();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID offerId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID conversionId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    conversions = new StudentConversionCompletedEventListener(inbox, selection, mapper, clock);
    payments =
        new AdmissionsPaymentReferenceEventListener(inbox, applications, rolling, mapper, clock);
  }

  @Test
  void completedConversionIsClaimedBeforeAllAuthoritativeIdentifiersReachTheWorkflow() {
    Map<String, Object> payload = conversion();
    String json = mapper.writeValueAsString(payload);
    when(inbox.claim(
            eventId,
            EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_EVENT,
            "student-records-service",
            json,
            NOW))
        .thenReturn(true);
    conversions.receive(message(json));
    InOrder order = inOrder(inbox, selection);
    order
        .verify(inbox)
        .claim(
            eventId,
            EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_EVENT,
            "student-records-service",
            json,
            NOW);
    order
        .verify(selection)
        .completeStudentConversion(
            conversionId, applicationId, offerId, studentId, "R260001", userId);
    order.verify(inbox).markProcessed(eventId, NOW);
  }

  @Test
  void confirmedFinanceProjectionIsAppliedBeforeRollingWorkflowAdvances() {
    String json = mapper.writeValueAsString(payment());
    ApplicationPaymentReferenceUpdatedEvent event =
        mapper.readValue(json, ApplicationPaymentReferenceUpdatedEvent.class);
    when(inbox.claim(
            eventId,
            EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_EVENT,
            "finance-service",
            json,
            NOW))
        .thenReturn(true);
    payments.receivePaymentReferenceUpdate(message(json));
    InOrder order = inOrder(inbox, applications, rolling);
    order
        .verify(inbox)
        .claim(
            eventId,
            EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_EVENT,
            "finance-service",
            json,
            NOW);
    order.verify(applications).applyFinancePaymentReferenceUpdate(event);
    order.verify(rolling).advance(applicationId, eventId);
    order.verify(inbox).markProcessed(eventId, NOW);
  }

  @ParameterizedTest
  @ValueSource(strings = {"conversion", "payment"})
  void inboxDuplicateSkipsAllWorkflowSideEffects(String type) {
    receive(type, type.equals("conversion") ? conversion() : payment());
    verifyNoInteractions(selection, applications, rolling);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "conversionRequestId",
        "applicationId",
        "offerId",
        "studentId",
        "userId",
        "studentNumber"
      })
  void incompleteStudentConversionCannotClaimAnInboxRecord(String missing) {
    Map<String, Object> payload = conversion();
    payload.put(missing, null);
    assertThatThrownBy(() -> receive("conversion", payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract is invalid");
    verifyNoInteractions(inbox, selection);
  }

  @Test
  void blankStudentNumberCannotCompleteConversion() {
    Map<String, Object> payload = conversion();
    payload.put("studentNumber", " ");
    assertThatThrownBy(() -> receive("conversion", payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract is invalid");
    verifyNoInteractions(inbox, selection);
  }

  @ParameterizedTest
  @ValueSource(strings = {"eventId", "applicationId", "financePaymentReferenceId"})
  void incompleteFinanceProjectionCannotClaimAnInboxRecord(String missing) {
    Map<String, Object> payload = payment();
    payload.put(missing, null);
    assertThatThrownBy(() -> receive("payment", payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract is invalid");
    verifyNoInteractions(inbox, applications, rolling);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void unknownSchemaVersionsAreRejectedByBothConsumers(int schema) {
    Map<String, Object> conversion = conversion();
    conversion.put("schemaVersion", schema);
    Map<String, Object> payment = payment();
    payment.put("schemaVersion", schema);
    assertThatThrownBy(() -> receive("conversion", conversion))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
    assertThatThrownBy(() -> receive("payment", payment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
    verifyNoInteractions(inbox, selection, applications, rolling);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{invalid", "[]", "{\"eventId\":\"bad-uuid\"}"})
  void malformedJsonIsRejectedBeforeAnyInboxClaim(String json) {
    assertThatThrownBy(() -> conversions.receive(message(json)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("event is invalid");
    assertThatThrownBy(() -> payments.receivePaymentReferenceUpdate(message(json)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payload is invalid");
    verifyNoInteractions(inbox, selection, applications, rolling);
  }

  @ParameterizedTest
  @ValueSource(strings = {"conversion", "payment", "advance"})
  void downstreamFailureIsRethrownWithoutMarkingEventProcessed(String failing) {
    when(inbox.claim(any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
    IllegalStateException unavailable = new IllegalStateException("Dependency unavailable");
    if (failing.equals("conversion"))
      doThrow(unavailable)
          .when(selection)
          .completeStudentConversion(any(), any(), any(), any(), anyString(), any());
    else if (failing.equals("payment"))
      doThrow(unavailable).when(applications).applyFinancePaymentReferenceUpdate(any());
    else doThrow(unavailable).when(rolling).advance(any(), any());
    assertThatThrownBy(
            () ->
                receive(
                    failing.equals("conversion") ? "conversion" : "payment",
                    failing.equals("conversion") ? conversion() : payment()))
        .isSameAs(unavailable);
    verify(inbox, never()).markProcessed(any(), any());
    if (failing.equals("payment")) verifyNoInteractions(rolling);
  }

  private Map<String, Object> conversion() {
    return new LinkedHashMap<>(
        Map.of(
            "eventId",
            eventId,
            "schemaVersion",
            StudentConversionCompletedEvent.CURRENT_SCHEMA_VERSION,
            "occurredAt",
            NOW.toString(),
            "conversionRequestId",
            conversionId,
            "applicationId",
            applicationId,
            "offerId",
            offerId,
            "studentId",
            studentId,
            "userId",
            userId,
            "studentNumber",
            "R260001"));
  }

  private Map<String, Object> payment() {
    return new LinkedHashMap<>(
        Map.of(
            "eventId",
            eventId,
            "schemaVersion",
            ApplicationPaymentReferenceUpdatedEvent.CURRENT_SCHEMA_VERSION,
            "occurredAt",
            NOW.toString(),
            "applicationId",
            applicationId,
            "financePaymentReferenceId",
            UUID.randomUUID(),
            "reference",
            "PAY-123",
            "status",
            "CONFIRMED",
            "workflowCleared",
            true,
            "stateSequence",
            1L,
            "requiredForSubmission",
            true));
  }

  private void receive(String type, Map<String, Object> payload) {
    Message message = message(mapper.writeValueAsString(payload));
    if (type.equals("conversion")) conversions.receive(message);
    else payments.receivePaymentReferenceUpdate(message);
  }

  private Message message(String json) {
    return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
  }
}

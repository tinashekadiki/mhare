package zw.ac.uz.emhare.studentrecords.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.studentrecords.conversion.StudentConversionService;

/**
 * @author Tinashe K
 */
class StudentLifecycleEventContractTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final UUID eventId = UUID.randomUUID(), conversionId = UUID.randomUUID();
  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
  private StudentRecordsIntegrationInbox inbox;
  private StudentConversionService conversions;
  private AcceptedOfferConversionEventListener accepted;
  private StudentProvisioningResultEventListener provisioning;

  @BeforeEach
  void setUp() {
    inbox = mock(StudentRecordsIntegrationInbox.class);
    conversions = mock(StudentConversionService.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    accepted = new AcceptedOfferConversionEventListener(inbox, conversions, mapper, clock);
    provisioning = new StudentProvisioningResultEventListener(inbox, conversions, mapper, clock);
    when(inbox.claim(eq(eventId), anyString(), anyString(), anyString(), eq(NOW))).thenReturn(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"offer", "finance", "portal"})
  void claimedEventUsesOwnedSourceAndIsMarkedProcessedOnlyAfterBusinessHandling(String kind) {
    String payload = mapper.writeValueAsString(event(kind));
    receive(kind, message(payload));
    var order = inOrder(inbox, conversions);
    order.verify(inbox).claim(eventId, eventType(kind), source(kind), payload, NOW);
    switch (kind) {
      case "offer" ->
          order
              .verify(conversions)
              .startConversion(any(AcceptedOfferReadyForConversionEvent.class));
      case "finance" ->
          order
              .verify(conversions)
              .recordFinanceProvisioning(conversionId, false, "Account creation rejected");
      case "portal" -> order.verify(conversions).recordPortalProvisioning(conversionId, true, null);
      default -> throw new AssertionError(kind);
    }
    order.verify(inbox).markProcessed(eventId, NOW);
  }

  @ParameterizedTest
  @ValueSource(strings = {"offer", "finance", "portal"})
  void duplicateEventCannotTriggerBusinessHandlingAgain(String kind) {
    when(inbox.claim(eq(eventId), anyString(), anyString(), anyString(), eq(NOW)))
        .thenReturn(false);
    receive(kind, message(mapper.writeValueAsString(event(kind))));
    verifyNoInteractions(conversions);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"offer", "finance", "portal"})
  void malformedJsonFailsBeforeClaimingInboxOrChangingBusinessState(String kind) {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> receive(kind, message("{invalid-json")));
    assertEquals(
        kind.equals("offer")
            ? "Accepted-offer event payload is invalid."
            : "Provisioning result payload is invalid.",
        error.getMessage());
    verifyNoInteractions(inbox, conversions);
  }

  @ParameterizedTest
  @ValueSource(strings = {"offer", "finance", "portal"})
  void businessFailureCannotBeAcknowledgedAsProcessed(String kind) {
    IllegalStateException error = new IllegalStateException("Dependency unavailable");
    if (kind.equals("offer")) when(conversions.startConversion(any())).thenThrow(error);
    if (kind.equals("finance"))
      doThrow(error)
          .when(conversions)
          .recordFinanceProvisioning(any(), anyBoolean(), nullable(String.class));
    if (kind.equals("portal"))
      doThrow(error)
          .when(conversions)
          .recordPortalProvisioning(any(), anyBoolean(), nullable(String.class));
    assertSame(
        error,
        assertThrows(
            IllegalStateException.class,
            () -> receive(kind, message(mapper.writeValueAsString(event(kind))))));
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void inboxClaimsAreBasedOnAtomicInsertedRowCount(int rows) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(rows);
    assertEquals(
        rows == 1,
        new StudentRecordsIntegrationInbox(jdbc).claim(eventId, "event-type", "source", "{}", NOW));
    verify(jdbc)
        .update(
            contains("ON CONFLICT (event_id) DO NOTHING"),
            eq(eventId),
            eq("event-type"),
            eq("source"),
            eq("{}"),
            eq(java.sql.Timestamp.from(NOW)));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void processedAcknowledgementRequiresExactlyOneClaimedInboxRecord(int rows) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(rows);
    StudentRecordsIntegrationInbox target = new StudentRecordsIntegrationInbox(jdbc);
    if (rows == 1) assertDoesNotThrow(() -> target.markProcessed(eventId, NOW));
    else assertThrows(IllegalStateException.class, () -> target.markProcessed(eventId, NOW));
    verify(jdbc)
        .update(contains("processed_at IS NULL"), eq(java.sql.Timestamp.from(NOW)), eq(eventId));
  }

  private Object event(String kind) {
    return switch (kind) {
      case "offer" ->
          new AcceptedOfferReadyForConversionEvent(
              eventId,
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
      case "finance" ->
          new StudentFinanceAccountProvisionedEvent(
              eventId,
              1,
              NOW,
              conversionId,
              UUID.randomUUID(),
              null,
              false,
              "Account creation rejected");
      case "portal" ->
          new StudentPortalAccessProvisionedEvent(
              eventId, 1, NOW, conversionId, UUID.randomUUID(), UUID.randomUUID(), true, null);
      default -> throw new AssertionError(kind);
    };
  }

  private String source(String kind) {
    return switch (kind) {
      case "offer" -> "admissions-service";
      case "finance" -> "finance-service";
      case "portal" -> "core-identity-service";
      default -> throw new AssertionError(kind);
    };
  }

  private String eventType(String kind) {
    return switch (kind) {
      case "offer" -> EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_EVENT;
      case "finance" -> EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_EVENT;
      case "portal" -> EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_EVENT;
      default -> throw new AssertionError(kind);
    };
  }

  private void receive(String kind, Message message) {
    switch (kind) {
      case "offer" -> accepted.receive(message);
      case "finance" -> provisioning.receiveFinanceResult(message);
      case "portal" -> provisioning.receivePortalResult(message);
      default -> throw new AssertionError(kind);
    }
  }

  private Message message(String payload) {
    return new Message(payload.getBytes(StandardCharsets.UTF_8), new MessageProperties());
  }
}

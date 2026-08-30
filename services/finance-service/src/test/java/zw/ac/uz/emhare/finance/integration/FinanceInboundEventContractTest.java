package zw.ac.uz.emhare.finance.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.finance.payment.FinanceApplicationPaymentService;
import zw.ac.uz.emhare.finance.payment.application.command.CreateApplicationPaymentReferenceCommand;
import zw.ac.uz.emhare.finance.student.StudentFinanceAccountService;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;

/**
 * @author Tinashe K
 */
class FinanceInboundEventContractTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID EVENT = UUID.randomUUID(),
      APPLICATION = UUID.randomUUID(),
      USER = UUID.randomUUID(),
      KEYCLOAK = UUID.randomUUID(),
      CONVERSION = UUID.randomUUID(),
      STUDENT = UUID.randomUUID(),
      OFFER = UUID.randomUUID();
  private final ObjectMapper mapper = new ObjectMapper();
  private final FinanceIntegrationInbox inbox = mock(FinanceIntegrationInbox.class);
  private final FinanceApplicationPaymentService payments =
      mock(FinanceApplicationPaymentService.class);
  private final StudentFinanceAccountService accounts = mock(StudentFinanceAccountService.class);
  private final FinanceIntegrationOutboxService outbox =
      mock(FinanceIntegrationOutboxService.class);
  private final ApplicationFeeRequiredEventListener fees =
      new ApplicationFeeRequiredEventListener(
          inbox, payments, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
  private final StudentFinanceAccountProvisioningEventListener provision =
      new StudentFinanceAccountProvisioningEventListener(
          inbox, accounts, outbox, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void validFeeEventClaimsExactContractThenCreatesReferenceBeforeMarkingProcessed() {
    var event = feeEvent("");
    String json = mapper.writeValueAsString(event);
    when(inbox.claim(
            EVENT,
            EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT,
            "admissions-service",
            json,
            NOW))
        .thenReturn(true);
    fees.receiveApplicationFeeRequired(message(json));
    InOrder order = inOrder(inbox, payments);
    order
        .verify(inbox)
        .claim(
            EVENT,
            EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT,
            "admissions-service",
            json,
            NOW);
    order
        .verify(payments)
        .ensurePaymentReference(
            new CreateApplicationPaymentReferenceCommand(
                APPLICATION, USER, KEYCLOAK, new BigDecimal("25.00"), "USD", true));
    order.verify(inbox).markProcessed(EVENT, NOW);
  }

  @Test
  void duplicateFeeEventDoesNotRepeatFinancialSideEffects() {
    fees.receiveApplicationFeeRequired(message(mapper.writeValueAsString(feeEvent(""))));
    verifyNoInteractions(payments);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void failedFeeProvisioningIsNotMarkedProcessed() {
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    when(payments.ensurePaymentReference(any()))
        .thenThrow(new IllegalStateException("Finance unavailable"));
    assertThrows(
        IllegalStateException.class,
        () -> fees.receiveApplicationFeeRequired(message(mapper.writeValueAsString(feeEvent("")))));
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"event", "schema", "application", "user", "keycloak", "not-required"})
  void invalidFeeContractFailsBeforeInboxClaim(String invalidField) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fees.receiveApplicationFeeRequired(
                message(mapper.writeValueAsString(feeEvent(invalidField)))));
    verifyNoInteractions(inbox, payments);
  }

  @Test
  void validProvisioningClaimsThenCreatesAccountAndDurableReplyBeforeAcknowledgement() {
    var event = provisioningEvent("");
    String json = mapper.writeValueAsString(event);
    var account = new StudentFinanceAccount(event, NOW);
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    when(accounts.ensureAccount(event)).thenReturn(account);
    provision.receive(message(json));
    InOrder order = inOrder(inbox, accounts, outbox);
    order
        .verify(inbox)
        .claim(
            EVENT,
            EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT,
            "student-records-service",
            json,
            NOW);
    order.verify(accounts).ensureAccount(event);
    order.verify(outbox).enqueueStudentFinanceAccountProvisioned(CONVERSION, STUDENT, account);
    order.verify(inbox).markProcessed(EVENT, NOW);
  }

  @Test
  void duplicateProvisioningDoesNotCreateAccountOrPublishDuplicateReply() {
    provision.receive(message(mapper.writeValueAsString(provisioningEvent(""))));
    verifyNoInteractions(accounts, outbox);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void accountOrOutboxFailureLeavesProvisioningUnacknowledgedForTransactionalRetry() {
    var event = provisioningEvent("");
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    when(accounts.ensureAccount(event)).thenThrow(new IllegalStateException("Account conflict"));
    assertThrows(
        IllegalStateException.class,
        () -> provision.receive(message(mapper.writeValueAsString(event))));
    verifyNoInteractions(outbox);
    doReturn(new StudentFinanceAccount(event, NOW)).when(accounts).ensureAccount(event);
    doThrow(new IllegalStateException("Outbox persistence unavailable"))
        .when(outbox)
        .enqueueStudentFinanceAccountProvisioned(any(), any(), any());
    assertThrows(
        IllegalStateException.class,
        () -> provision.receive(message(mapper.writeValueAsString(event))));
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "event",
        "schema",
        "conversion",
        "student",
        "user",
        "offer",
        "number-null",
        "number-blank"
      })
  void invalidProvisioningContractFailsBeforeSideEffects(String invalidField) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            provision.receive(message(mapper.writeValueAsString(provisioningEvent(invalidField)))));
    verifyNoInteractions(inbox, accounts, outbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{invalid", "[]", "\"not-an-event\""})
  void malformedPayloadsAreRejectedForBothInboundContracts(String json) {
    assertThrows(
        IllegalArgumentException.class, () -> fees.receiveApplicationFeeRequired(message(json)));
    assertThrows(IllegalArgumentException.class, () -> provision.receive(message(json)));
    verifyNoInteractions(inbox, payments, accounts, outbox);
  }

  private ApplicationFeeRequiredEvent feeEvent(String invalidField) {
    return new ApplicationFeeRequiredEvent(
        "event".equals(invalidField) ? null : EVENT,
        "schema".equals(invalidField) ? 99 : 1,
        NOW,
        "application".equals(invalidField) ? null : APPLICATION,
        "user".equals(invalidField) ? null : USER,
        "keycloak".equals(invalidField) ? null : KEYCLOAK,
        new BigDecimal("25.00"),
        "USD",
        !"not-required".equals(invalidField));
  }

  private StudentFinanceAccountProvisioningRequestedEvent provisioningEvent(String invalidField) {
    return new StudentFinanceAccountProvisioningRequestedEvent(
        "event".equals(invalidField) ? null : EVENT,
        "schema".equals(invalidField) ? 99 : 1,
        NOW,
        "conversion".equals(invalidField) ? null : CONVERSION,
        "student".equals(invalidField) ? null : STUDENT,
        "number-null".equals(invalidField)
            ? null
            : "number-blank".equals(invalidField) ? " " : "R260001A",
        "user".equals(invalidField) ? null : USER,
        "offer".equals(invalidField) ? null : OFFER,
        "student@example.test");
  }

  private Message message(String json) {
    return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
  }
}

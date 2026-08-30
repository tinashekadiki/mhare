package zw.ac.uz.emhare.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.notifications.api.model.NotificationApiModels.*;
import zw.ac.uz.emhare.notifications.domain.model.*;
import zw.ac.uz.emhare.notifications.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class NotificationGovernanceServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final UUID maker = UUID.randomUUID(),
      checker = UUID.randomUUID(),
      userId = UUID.randomUUID();
  private NotificationTemplateRepository templates;
  private NotificationConsentRepository consents;
  private NotificationRequestRepository requests;
  private NotificationDeliveryAttemptRepository attempts;
  private NotificationEventInboxRepository inbox;
  private NotificationProviderCallbackRepository callbacks;
  private InAppNotificationRepository inApp;
  private NotificationRequestAttachmentRepository attachments;
  private NotificationDeliveryProvider provider;
  private NotificationDeliveryOutboxService outbox;
  private NotificationService service;
  private NotificationTemplate template;

  @BeforeEach
  void setUp() {
    templates = mock(NotificationTemplateRepository.class);
    consents = mock(NotificationConsentRepository.class);
    requests = mock(NotificationRequestRepository.class);
    attempts = mock(NotificationDeliveryAttemptRepository.class);
    inbox = mock(NotificationEventInboxRepository.class);
    callbacks = mock(NotificationProviderCallbackRepository.class);
    inApp = mock(InAppNotificationRepository.class);
    attachments = mock(NotificationRequestAttachmentRepository.class);
    provider = mock(NotificationDeliveryProvider.class);
    outbox = mock(NotificationDeliveryOutboxService.class);
    service =
        new NotificationService(
            templates,
            consents,
            requests,
            attempts,
            inbox,
            callbacks,
            inApp,
            attachments,
            provider,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC));
    template =
        template(
            NotificationTemplate.Channel.EMAIL,
            NotificationTemplate.Category.TRANSACTIONAL,
            "Hello {{name}}");
    template.activate(checker, "Approved wording", NOW, 0);
    when(templates.findFirstByCodeIgnoreCaseAndChannelAndLocaleAndStatusOrderByTemplateVersionDesc(
            anyString(), any(), anyString(), eq(NotificationTemplate.Status.ACTIVE)))
        .thenAnswer(invocation -> Optional.of(template));
    when(requests.nextRequestNumber()).thenReturn(42L);
    when(requests.saveAndFlush(any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(templates.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(consents.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(inApp.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(callbacks.saveAndFlush(any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(provider.code()).thenReturn("SMTP");
  }

  @Test
  void templateCreationAndAuditedDraftUpdatePreserveCodeChannelAndVersion() {
    var created =
        service.createTemplate(
            new CreateTemplate(
                " notice ",
                2,
                "Notice",
                "EVENT",
                NotificationTemplate.Channel.EMAIL,
                NotificationTemplate.Category.WORKFLOW,
                "en-ZW",
                "Subject",
                "Body"),
            maker);
    assertEquals("NOTICE", created.code());
    assertEquals(NotificationTemplate.Status.DRAFT, created.status());
    assertEquals(maker, created.preparedByUserId());
    template =
        template(
            NotificationTemplate.Channel.EMAIL, NotificationTemplate.Category.WORKFLOW, "Body");
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));
    var updated =
        service.updateTemplate(
            template.getId(),
            new UpdateTemplate(
                "Revised",
                "NEW_EVENT",
                NotificationTemplate.Category.SECURITY,
                null,
                "New body",
                0));
    assertEquals("Revised", updated.name());
    assertEquals("NEW_EVENT", updated.eventType());
    assertEquals("NOTICE", updated.code());
    assertNull(updated.subjectTemplate());
  }

  @Test
  void templateApprovalUsesIndependentOperatorAndRetirementCannotReactivateHistory() {
    template =
        template(
            NotificationTemplate.Channel.EMAIL, NotificationTemplate.Category.WORKFLOW, "Body");
    when(templates.findById(template.getId())).thenReturn(Optional.of(template));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.transitionTemplate(
                template.getId(),
                new TemplateTransition(NotificationTemplate.Status.ACTIVE, "Reviewed", 0),
                maker));
    assertEquals(
        NotificationTemplate.Status.ACTIVE,
        service
            .transitionTemplate(
                template.getId(),
                new TemplateTransition(NotificationTemplate.Status.ACTIVE, "Reviewed", 0),
                checker)
            .status());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.updateTemplate(
                template.getId(),
                new UpdateTemplate(
                    "Changed", "EVENT", NotificationTemplate.Category.WORKFLOW, null, "Body", 0)));
    assertEquals(
        NotificationTemplate.Status.RETIRED,
        service
            .transitionTemplate(
                template.getId(),
                new TemplateTransition(NotificationTemplate.Status.RETIRED, "Superseded", 0),
                checker)
            .status());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.transitionTemplate(
                template.getId(),
                new TemplateTransition(NotificationTemplate.Status.ACTIVE, "Again", 0),
                checker));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.transitionTemplate(
                template.getId(),
                new TemplateTransition(NotificationTemplate.Status.DRAFT, "Invalid", 0),
                checker));
  }

  @Test
  void consentChangesRequireExistingVersionAndNormalizeRecipientKey() {
    var command =
        new RecordConsent(
            userId,
            " USER ",
            NotificationTemplate.Channel.EMAIL,
            NotificationTemplate.Category.MARKETING,
            NotificationConsent.Status.OPTED_IN,
            "Self service",
            "Preference screen",
            null);
    assertEquals("user", service.recordConsent(command).recipientKey());
    var consent =
        new NotificationConsent(
            userId,
            "USER",
            NotificationTemplate.Channel.EMAIL,
            NotificationTemplate.Category.MARKETING,
            NotificationConsent.Status.OPTED_IN,
            "Self service",
            null,
            NOW);
    when(consents.findFirstByRecipientKeyIgnoreCaseAndChannelAndCategoryAndEffectiveUntilIsNull(
            anyString(), any(), any()))
        .thenReturn(Optional.of(consent));
    assertThrows(IllegalStateException.class, () -> service.recordConsent(command));
    var updated =
        service.recordConsent(
            new RecordConsent(
                userId,
                "USER",
                command.channel(),
                command.category(),
                NotificationConsent.Status.OPTED_OUT,
                "Self service",
                "Opt out request",
                0L));
    assertEquals(NotificationConsent.Status.OPTED_OUT, updated.status());
    assertEquals("Opt out request", updated.evidenceReference());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.recordConsent(
                new RecordConsent(
                    userId,
                    "USER",
                    command.channel(),
                    command.category(),
                    NotificationConsent.Status.OPTED_IN,
                    "Self service",
                    null,
                    9L)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "opted-in", "opted-out"})
  void marketingIsQueuedOnlyWithExplicitConsent(String preference) {
    template =
        activeTemplate(
            NotificationTemplate.Channel.EMAIL,
            NotificationTemplate.Category.MARKETING,
            "Hello {{name}}");
    if (!preference.equals("missing"))
      when(consents.findFirstByRecipientKeyIgnoreCaseAndChannelAndCategoryAndEffectiveUntilIsNull(
              anyString(), any(), any()))
          .thenReturn(
              Optional.of(
                  new NotificationConsent(
                      userId,
                      "recipient",
                      template.getChannel(),
                      template.getCategory(),
                      preference.equals("opted-in")
                          ? NotificationConsent.Status.OPTED_IN
                          : NotificationConsent.Status.OPTED_OUT,
                      "Self service",
                      null,
                      NOW)));
    var result = service.queue(command());
    assertEquals(
        preference.equals("opted-in")
            ? NotificationRequest.Status.QUEUED
            : NotificationRequest.Status.SUPPRESSED,
        result.status());
    assertEquals(
        preference.equals("missing")
            ? "CONSENT_MISSING"
            : preference.equals("opted-in") ? "OPTED_IN" : "OPTED_OUT",
        result.consentDecision());
    if (!preference.equals("opted-in")) assertNull(result.nextAttemptAt());
  }

  @Test
  void transactionalRenderingEscapesReplacementCharactersAndDefaultsSchedulingAndAttempts() {
    var command =
        new QueueNotification(
            "key",
            "admissions-service",
            UUID.randomUUID(),
            "EVENT",
            "NOTICE",
            template.getChannel(),
            "en-ZW",
            userId,
            "RECIPIENT",
            "recipient@example.test",
            null,
            null,
            null,
            Map.of("name", "$1\\quoted"));
    var result = service.queue(command);
    assertEquals("Hello $1\\quoted", result.body());
    assertEquals("NOT_REQUIRED", result.consentDecision());
    assertEquals(NOW, result.scheduledAt());
    assertEquals(5, result.maxAttempts());
    assertEquals(NotificationRequest.Priority.NORMAL, result.priority());
    assertEquals("NTF-0000000042", result.requestNumber());
    verifyNoInteractions(consents);
  }

  @Test
  void staticTemplatesAllowAbsentVariablesAndSubject() {
    template =
        activeTemplate(
            NotificationTemplate.Channel.SMS,
            NotificationTemplate.Category.TRANSACTIONAL,
            "Static notice");
    var result =
        service.queue(
            new QueueNotification(
                "key",
                "source",
                UUID.randomUUID(),
                "EVENT",
                "NOTICE",
                template.getChannel(),
                "en-ZW",
                null,
                "recipient",
                "+263771234567",
                NotificationRequest.Priority.LOW,
                NOW.plusSeconds(60),
                2,
                null));
    assertNull(result.subject());
    assertEquals("Static notice", result.body());
    assertEquals(NOW.plusSeconds(60), result.scheduledAt());
    assertEquals(2, result.maxAttempts());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"no-template", "event-mismatch", "missing-variable", "in-app-without-user"})
  void invalidQueueRequestsCannotPersistDeliveryIntent(String failure) {
    var command = command();
    if (failure.equals("no-template"))
      when(templates
              .findFirstByCodeIgnoreCaseAndChannelAndLocaleAndStatusOrderByTemplateVersionDesc(
                  anyString(), any(), anyString(), any()))
          .thenReturn(Optional.empty());
    if (failure.equals("event-mismatch")) {
      template =
          new NotificationTemplate(
              "NOTICE",
              1,
              "Notice",
              "OTHER_EVENT",
              NotificationTemplate.Channel.EMAIL,
              NotificationTemplate.Category.WORKFLOW,
              "en-ZW",
              null,
              "Body",
              maker);
      template.activate(checker, "Approved wording", NOW, 0);
    }
    if (failure.equals("missing-variable"))
      template =
          activeTemplate(
              NotificationTemplate.Channel.EMAIL,
              NotificationTemplate.Category.WORKFLOW,
              "Hello {{missing}}");
    if (failure.equals("in-app-without-user")) {
      template =
          activeTemplate(
              NotificationTemplate.Channel.IN_APP,
              NotificationTemplate.Category.WORKFLOW,
              "Hello {{name}}");
      command =
          new QueueNotification(
              "key",
              "source",
              UUID.randomUUID(),
              "EVENT",
              "NOTICE",
              template.getChannel(),
              "en-ZW",
              null,
              "recipient",
              "recipient",
              NotificationRequest.Priority.NORMAL,
              NOW,
              3,
              Map.of("name", "Tariro"));
    }
    var invalid = command;
    RuntimeException error = assertThrows(RuntimeException.class, () -> service.queue(invalid));
    assertTrue(
        error
            .getMessage()
            .contains(
                switch (failure) {
                  case "no-template" -> "No active notification template";
                  case "event-mismatch" -> "does not match";
                  case "missing-variable" -> "variable 'missing'";
                  default -> "recipient user ID";
                }));
    verify(requests, never()).saveAndFlush(any());
    verifyNoInteractions(attachments);
  }

  @Test
  void queueIdempotencyReturnsExistingEvidenceBeforeTemplateLookup() {
    var request = request();
    when(requests.findByIdempotencyKey("key")).thenReturn(Optional.of(request));
    assertEquals(request.getId(), service.queue(command()).id());
    verifyNoInteractions(templates, attachments);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentQueueInsertionResolvesTheWinningIdempotentRequestOrPropagatesConflict(
      boolean winnerPresent) {
    var existing = request();
    when(requests.findByIdempotencyKey("key"))
        .thenReturn(Optional.empty(), winnerPresent ? Optional.of(existing) : Optional.empty());
    var conflict = new DataIntegrityViolationException("Unique key");
    doThrow(conflict).when(requests).saveAndFlush(any());
    if (winnerPresent) assertEquals(existing.getId(), service.queue(command()).id());
    else
      assertSame(
          conflict,
          assertThrows(DataIntegrityViolationException.class, () -> service.queue(command())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"sent", "retry", "permanent", "exhausted"})
  void dispatchRecordsProviderAttemptAndSchedulesOnlyRecoverableDeliveryFailures(String outcome) {
    var request = request();
    if (outcome.equals("exhausted")) {
      request.startAttempt();
      request.deliveryFailed("SMTP", "ERR", "Unavailable", true, NOW, NOW);
      request.startAttempt();
      request.deliveryFailed("SMTP", "ERR", "Unavailable", true, NOW, NOW);
    }
    when(requests.lockDue(eq(NOW), any())).thenReturn(List.of(request));
    when(provider.deliver(request))
        .thenReturn(
            outcome.equals("sent")
                ? NotificationDeliveryProvider.DeliveryResult.sent(
                    "provider-42", Map.of("accepted", true))
                : NotificationDeliveryProvider.DeliveryResult.failed(
                    !outcome.equals("permanent"), "ERR", "Delivery rejected", Map.of()));
    service.dispatchDue();
    var attempt = org.mockito.ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
    verify(attempts).save(attempt.capture());
    assertEquals(request.getId(), attempt.getValue().getNotificationRequestId());
    if (outcome.equals("sent")) {
      assertEquals(NotificationRequest.Status.SENT, request.getStatus());
      assertEquals(
          NotificationRequest.ProviderDeliveryStatus.ACCEPTED, request.getProviderDeliveryStatus());
      verify(outbox).enqueue(request, "SENT", "provider-42", null);
    } else {
      assertEquals(
          outcome.equals("retry")
              ? NotificationRequest.Status.RETRY_SCHEDULED
              : NotificationRequest.Status.FAILED,
          request.getStatus());
      if (outcome.equals("retry")) assertEquals(NOW.plusSeconds(60), request.getNextAttemptAt());
      else assertNull(request.getNextAttemptAt());
      verify(outbox).enqueue(request, "FAILED", null, "Delivery rejected");
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "Provider unavailable"})
  void providerExceptionsBecomeRecoverableAttemptsWithSafeErrorText(String message) {
    var request = request();
    when(provider.deliver(request)).thenThrow(new IllegalStateException(message));
    service.dispatch(request, NOW);
    assertEquals(NotificationRequest.Status.RETRY_SCHEDULED, request.getStatus());
    assertEquals("PROVIDER_EXCEPTION", request.getLastErrorCode());
    assertEquals(
        message == null || message.isBlank() ? "IllegalStateException" : message,
        request.getLastErrorMessage());
  }

  @Test
  void failedRequestManualRetryRequiresEvidenceAndPreservesMonotonicAttempts() {
    var request = request();
    request.startAttempt();
    request.deliveryFailed("SMTP", "ERR", "Failed", false, NOW, null);
    when(requests.findById(request.getId())).thenReturn(Optional.of(request));
    var result =
        service.retry(request.getId(), new ManualAction("  Authorised manual retry  ", 0), checker);
    assertEquals(NotificationRequest.Status.RETRY_SCHEDULED, result.status());
    assertEquals(6, result.maxAttempts());
    assertEquals(1, result.attemptCount());
    assertEquals(checker, result.manualRetryByUserId());
    assertEquals("Authorised manual retry", result.manualRetryReason());
    assertNull(result.lastErrorMessage());
  }

  @Test
  void cancellationAndMissingRecordsFailClosed() {
    var request = request();
    when(requests.findById(request.getId())).thenReturn(Optional.of(request));
    assertEquals(
        NotificationRequest.Status.CANCELLED,
        service
            .cancel(request.getId(), new ManualAction("No longer required", 0), checker)
            .status());
    assertEquals(checker, request.getCancelledByUserId());
    assertThrows(
        IllegalStateException.class,
        () -> service.cancel(request.getId(), new ManualAction("Again", 0), checker));
    assertThrows(
        NoSuchElementException.class,
        () -> service.cancel(UUID.randomUUID(), new ManualAction("Reason", 0), checker));
    assertThrows(
        NoSuchElementException.class,
        () ->
            service.updateTemplate(
                UUID.randomUUID(),
                new UpdateTemplate(
                    "Name", "EVENT", NotificationTemplate.Category.WORKFLOW, null, "Body", 0)));
  }

  @ParameterizedTest
  @EnumSource(NotificationProviderCallback.DeliveryStatus.class)
  void providerCallbacksUpdateOnlyDeliveryEvidenceAndEmitOutcome(
      NotificationProviderCallback.DeliveryStatus status) {
    var request = request();
    request.startAttempt();
    request.sent("SMTP", "message", NOW);
    when(requests.findFirstByProviderCodeIgnoreCaseAndProviderMessageId("SMTP", "message"))
        .thenReturn(Optional.of(request));
    var callback =
        new ProviderCallbackPayload(
            "event", "message", status, NOW.plusSeconds(5), "ERR", "Provider detail");
    var result = service.recordProviderCallback("SMTP", callback, Map.of("signed", true));
    assertEquals(status, result.deliveryStatus());
    assertEquals(NotificationRequest.Status.SENT, request.getStatus());
    assertEquals(status.name(), request.getProviderDeliveryStatus().name());
    verify(outbox).enqueue(request, status.name(), "message", "Provider detail");
  }

  @Test
  void staleDeliveryEvidenceDoesNotReplaceNewerOutcomeAndUnknownProviderMessagesAreRejected() {
    var request = request();
    request.startAttempt();
    request.sent("SMTP", "message", NOW);
    request.applyProviderStatus(
        NotificationRequest.ProviderDeliveryStatus.DELIVERED, NOW.plusSeconds(10), null);
    request.applyProviderStatus(
        NotificationRequest.ProviderDeliveryStatus.BOUNCED, NOW.plusSeconds(5), "Old bounce");
    assertEquals(
        NotificationRequest.ProviderDeliveryStatus.DELIVERED, request.getProviderDeliveryStatus());
    assertEquals(NOW.plusSeconds(10), request.getProviderStatusAt());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.recordProviderCallback(
                "SMTP",
                new ProviderCallbackPayload(
                    "event",
                    "unknown",
                    NotificationProviderCallback.DeliveryStatus.DELIVERED,
                    NOW,
                    null,
                    null),
                Map.of("signed", true)));
  }

  @Test
  void duplicateProviderCallbackReturnsItsImmutableEvidenceWithoutRepeatingOutcome() {
    var callback =
        persisted(
            new NotificationProviderCallback(
                "SMTP",
                "event",
                "message",
                NotificationProviderCallback.DeliveryStatus.DELIVERED,
                NOW,
                NOW,
                UUID.randomUUID(),
                null,
                null,
                Map.of("signed", true)));
    when(callbacks.findByProviderCodeIgnoreCaseAndProviderEventId("SMTP", "event"))
        .thenReturn(Optional.of(callback));
    assertEquals(
        callback.getId(),
        service
            .recordProviderCallback(
                "SMTP",
                new ProviderCallbackPayload(
                    "event",
                    "message",
                    NotificationProviderCallback.DeliveryStatus.DELIVERED,
                    NOW,
                    null,
                    null),
                Map.of("signed", true))
            .id());
    verifyNoInteractions(requests, outbox);
  }

  @Test
  void inAppReadIsRecipientOwnedIdempotentAndVersioned() {
    template =
        template(
            NotificationTemplate.Channel.IN_APP, NotificationTemplate.Category.WORKFLOW, "Body");
    var request = request();
    var notification = persisted(new InAppNotification(request, NOW));
    when(inApp.findAllByRecipientUserIdOrderByDeliveredAtDesc(userId))
        .thenReturn(List.of(notification));
    when(inApp.findByIdAndRecipientUserId(notification.getId(), userId))
        .thenReturn(Optional.of(notification));
    assertEquals(notification.getId(), service.myInAppNotifications(userId).getFirst().id());
    assertEquals(NOW, service.markInAppRead(notification.getId(), userId, 0).readAt());
    assertEquals(NOW, service.markInAppRead(notification.getId(), userId, 0).readAt());
    assertThrows(
        IllegalStateException.class, () -> service.markInAppRead(notification.getId(), userId, 9));
    assertThrows(
        NoSuchElementException.class,
        () -> service.markInAppRead(notification.getId(), UUID.randomUUID(), 0));
  }

  private NotificationTemplate activeTemplate(
      NotificationTemplate.Channel channel, NotificationTemplate.Category category, String body) {
    var value = template(channel, category, body);
    value.activate(checker, "Approved wording", NOW, 0);
    return value;
  }

  private NotificationTemplate template(
      NotificationTemplate.Channel channel, NotificationTemplate.Category category, String body) {
    return persisted(
        new NotificationTemplate(
            "NOTICE", 1, "Notice", "EVENT", channel, category, "en-ZW", null, body, maker));
  }

  private QueueNotification command() {
    return new QueueNotification(
        "key",
        "admissions-service",
        UUID.randomUUID(),
        "EVENT",
        "NOTICE",
        template.getChannel(),
        "en-ZW",
        userId,
        "recipient",
        "recipient@example.test",
        NotificationRequest.Priority.NORMAL,
        NOW,
        3,
        Map.of("name", "Tariro"));
  }

  private NotificationRequest request() {
    return persisted(
        new NotificationRequest(
            "NTF-1",
            "key",
            "admissions-service",
            UUID.randomUUID(),
            "EVENT",
            template,
            userId,
            "recipient",
            "recipient@example.test",
            "Notice",
            "Body",
            NotificationRequest.Priority.NORMAL,
            "NOT_REQUIRED",
            NOW,
            3,
            false));
  }

  private static <T> T persisted(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

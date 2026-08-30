package zw.ac.uz.emhare.notifications;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.common.messaging.NotificationAttachmentReference;
import zw.ac.uz.emhare.notifications.domain.model.*;

/**
 * @author Tinashe K
 */
class NotificationEvidenceValidationTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final UUID maker = UUID.randomUUID(), userId = UUID.randomUUID();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "request",
        "sequence",
        "reference",
        "document",
        "bucket",
        "key",
        "checksum",
        "file",
        "contentType"
      })
  void attachmentsRequireACompleteStoredChecksumAddressedReference(String missing) {
    var reference =
        new NotificationAttachmentReference(
            missing.equals("document") ? null : UUID.randomUUID(),
            "DOC-1",
            missing.equals("bucket") ? null : "documents",
            missing.equals("key") ? " " : "offer.pdf",
            missing.equals("checksum") ? null : "A".repeat(64),
            missing.equals("file") ? " " : "offer.pdf",
            missing.equals("contentType") ? null : "application/pdf");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NotificationRequestAttachment(
                missing.equals("request") ? null : UUID.randomUUID(),
                missing.equals("sequence") ? 0 : 1,
                missing.equals("reference") ? null : reference));
  }

  @Test
  void attachmentSnapshotsSanitizeFilenameAndRetainChecksumAndS3Ownership() {
    UUID requestId = UUID.randomUUID(), documentId = UUID.randomUUID();
    var reference =
        new NotificationAttachmentReference(
            documentId,
            "DOC-1",
            " documents ",
            " offers/1.pdf ",
            "A".repeat(64),
            " offer/letter\r\n\".pdf ",
            " application/pdf ");
    var attachment = new NotificationRequestAttachment(requestId, 1, reference);
    assertEquals(requestId, attachment.getNotificationRequestId());
    assertEquals(documentId, attachment.getSourceDocumentId());
    assertEquals("offer-letter---.pdf", attachment.getFileName());
    assertEquals("a".repeat(64), attachment.getChecksumSha256());
    assertEquals("s3://documents/offers/1.pdf", attachment.getStorageUri());
    assertEquals("application/pdf", attachment.getContentType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"version", "channel", "category", "maker"})
  void templatesRequireValidIdentityAndOwnership(String missing) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NotificationTemplate(
                "NOTICE",
                missing.equals("version") ? 0 : 1,
                "Notice",
                "EVENT",
                missing.equals("channel") ? null : NotificationTemplate.Channel.EMAIL,
                missing.equals("category") ? null : NotificationTemplate.Category.TRANSACTIONAL,
                "en-ZW",
                null,
                "Body",
                missing.equals("maker") ? null : maker));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankTemplateIdentityCannotBecomeGovernedReferenceData(String code) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NotificationTemplate(
                code,
                1,
                "Notice",
                "EVENT",
                NotificationTemplate.Channel.EMAIL,
                NotificationTemplate.Category.TRANSACTIONAL,
                "en-ZW",
                null,
                "Body",
                maker));
  }

  @ParameterizedTest
  @ValueSource(strings = {"channel", "category", "status", "time"})
  void consentsRequireChannelCategoryDecisionAndEffectiveTime(String missing) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NotificationConsent(
                userId,
                "recipient",
                missing.equals("channel") ? null : NotificationTemplate.Channel.EMAIL,
                missing.equals("category") ? null : NotificationTemplate.Category.MARKETING,
                missing.equals("status") ? null : NotificationConsent.Status.OPTED_IN,
                "Self service",
                null,
                missing.equals("time") ? null : NOW));
  }

  @Test
  void staleTemplateWritesAndWrongLifecycleTransitionsAreRejected() {
    var template = template();
    assertThrows(
        IllegalStateException.class,
        () ->
            template.updateDraft(
                "Changed", "EVENT", NotificationTemplate.Category.WORKFLOW, null, "Body", 9));
    assertThrows(
        IllegalStateException.class,
        () -> template.activate(UUID.randomUUID(), "Reviewed", NOW, 9));
    assertThrows(
        IllegalStateException.class, () -> template.retire(UUID.randomUUID(), "Retire", NOW, 0));
    template.activate(UUID.randomUUID(), "Reviewed", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> template.activate(UUID.randomUUID(), "Again", NOW, 0));
    template.retire(UUID.randomUUID(), "Superseded", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> template.retire(UUID.randomUUID(), "Again", NOW, 0));
  }

  @Test
  void sentOrCancelledDeliveryCannotBeCancelledAndSuppressedIntentCannotDispatch() {
    var sent = request(false);
    sent.startAttempt();
    sent.sent("SMTP", "message", NOW);
    assertThrows(IllegalStateException.class, () -> sent.cancel(maker, "Withdraw", NOW, 0));
    assertThrows(IllegalStateException.class, sent::startAttempt);
    var suppressed = request(true);
    assertThrows(IllegalStateException.class, suppressed::startAttempt);
    suppressed.cancel(maker, "Withdraw", NOW, 0);
    assertThrows(IllegalStateException.class, () -> suppressed.cancel(maker, "Again", NOW, 0));
  }

  @Test
  void manualRetryRequiresFailedStateCurrentVersionActorTimeAndBoundedEvidence() {
    var request = request(false);
    assertThrows(
        IllegalStateException.class, () -> request.retryNow(maker, "Retry approved", NOW, 0));
    request.startAttempt();
    request.deliveryFailed("SMTP", null, "Unavailable", false, NOW, null);
    assertThrows(
        IllegalStateException.class, () -> request.retryNow(maker, "Retry approved", NOW, 9));
    assertThrows(
        NullPointerException.class, () -> request.retryNow(null, "Retry approved", NOW, 0));
    assertThrows(
        NullPointerException.class, () -> request.retryNow(maker, "Retry approved", null, 0));
    assertThrows(IllegalArgumentException.class, () -> request.retryNow(maker, "short", NOW, 0));
    assertThrows(
        IllegalArgumentException.class, () -> request.retryNow(maker, "x".repeat(1001), NOW, 0));
  }

  @Test
  void callbackEvidenceRequiresSentStateStatusAndProviderTime() {
    var request = request(false);
    assertThrows(
        IllegalStateException.class,
        () ->
            request.applyProviderStatus(
                NotificationRequest.ProviderDeliveryStatus.DELIVERED, NOW, null));
    request.startAttempt();
    request.sent("SMTP", "message", NOW);
    assertThrows(NullPointerException.class, () -> request.applyProviderStatus(null, NOW, null));
    assertThrows(
        NullPointerException.class,
        () ->
            request.applyProviderStatus(
                NotificationRequest.ProviderDeliveryStatus.DELIVERED, null, null));
  }

  @Test
  void inAppEvidenceRejectsAnotherRecipientAndPreservesFirstReadTimestamp() {
    var notification = new InAppNotification(request(false), NOW);
    assertThrows(
        IllegalArgumentException.class, () -> notification.markRead(UUID.randomUUID(), NOW, 0));
    notification.markRead(userId, NOW, 0);
    notification.markRead(userId, NOW.plusSeconds(60), 0);
    assertEquals(NOW, notification.getReadAt());
    assertEquals(userId, notification.getReadByUserId());
    var noOwner =
        new NotificationRequest(
            "NTF",
            "key",
            "source",
            null,
            "EVENT",
            template(),
            null,
            "recipient",
            "recipient",
            null,
            "Body",
            null,
            "NOT_REQUIRED",
            NOW,
            3,
            false);
    assertThrows(
        IllegalArgumentException.class,
        () -> new InAppNotification(noOwner, NOW).markRead(userId, NOW, 0));
  }

  private NotificationTemplate template() {
    return new NotificationTemplate(
        "NOTICE",
        1,
        "Notice",
        "EVENT",
        NotificationTemplate.Channel.EMAIL,
        NotificationTemplate.Category.TRANSACTIONAL,
        "en-ZW",
        null,
        "Body",
        maker);
  }

  private NotificationRequest request(boolean suppressed) {
    return new NotificationRequest(
        "NTF",
        "key",
        "source",
        null,
        "EVENT",
        template(),
        userId,
        "recipient",
        "recipient@example.test",
        null,
        "Body",
        null,
        suppressed ? "OPTED_OUT" : "NOT_REQUIRED",
        NOW,
        3,
        suppressed);
  }
}

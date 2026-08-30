package zw.ac.uz.emhare.admissions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Immutable document versions and monotonic delivery evidence. @author Tinashe K */
class OfferDeliveryEvidenceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private final UUID actor = UUID.randomUUID();
  private AdmissionOffer offer;
  private OfferDocumentVersion document;
  private OfferPublication publication;

  @BeforeEach
  void setUp() {
    Application application =
        identified(
            new Application(
                UUID.randomUUID(),
                "AUG26",
                "August intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                3,
                new Applicant(
                    UUID.randomUUID(),
                    "A000001",
                    "LOCAL",
                    "Tariro",
                    "Moyo",
                    "applicant@example.test"),
                new ApplicationType("UNDERGRAD", "Undergraduate", false, false),
                "APP-1",
                false));
    ApplicationProgrammeChoice choice =
        identified(
            new ApplicationProgrammeChoice(
                application,
                new ProgrammeSelectionSnapshot(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "BSC",
                    "Science",
                    "BSc",
                    UUID.randomUUID(),
                    "Computing",
                    "2026"),
                1));
    offer =
        identified(
            new AdmissionOffer(
                application,
                choice,
                new ProgrammeChoiceDecision(
                    application,
                    choice,
                    DecisionOutcome.ADMIT,
                    "Approved admission",
                    null,
                    actor,
                    NOW),
                "OFFER-1"));
    document = new OfferDocumentVersion(offer, 1, actor, NOW);
    document.store(UUID.randomUUID(), "DOC-1", "documents", "offer.pdf", "checksum", NOW);
    publication = new OfferPublication(offer, document, 1, actor, UUID.randomUUID(), NOW);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"offer", "publication", "attempt", "event", "recipient", "blankRecipient"})
  void queuedDispatchRequiresPublicationAttemptEventAndRecipient(String missing) {
    assertThatThrownBy(
            () ->
                new OfferDispatch(
                    missing.equals("offer") ? null : offer,
                    missing.equals("publication") ? null : publication,
                    missing.equals("attempt") ? 0 : 1,
                    missing.equals("event") ? null : UUID.randomUUID(),
                    missing.equals("recipient")
                        ? null
                        : missing.equals("blankRecipient") ? " " : "applicant@example.test",
                    NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("publication, delivery attempt");
  }

  @Test
  void queuedAttemptRetainsExactPublicationAndEventIdentity() {
    UUID notification = UUID.randomUUID();
    OfferDispatch dispatch =
        new OfferDispatch(offer, publication, 2, notification, " applicant@example.test ", NOW);
    assertThat(dispatch.getStatus()).isEqualTo(OfferDispatchStatus.QUEUED);
    assertThat(dispatch.getOfferPublication()).isSameAs(publication);
    assertThat(dispatch.getNotificationEventId()).isEqualTo(notification);
    assertThat(dispatch.getAttemptNumber()).isEqualTo(2);
    assertThat(dispatch.getDeliveryMethodCode()).isEqualTo("EMAIL");
    assertThat(dispatch.getSentTo()).isEqualTo("applicant@example.test");
  }

  @ParameterizedTest
  @EnumSource(
      value = OfferDispatchStatus.class,
      names = {"FAILED", "BOUNCED"})
  void failureEvidenceRequiresReasonAndCannotBeOverwrittenByOlderDelivery(
      OfferDispatchStatus status) {
    OfferDispatch dispatch =
        new OfferDispatch(offer, publication, 1, UUID.randomUUID(), "applicant@example.test", NOW);
    assertThatThrownBy(() -> dispatch.recordStatus(status, "id-1", null, NOW.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
    assertThatThrownBy(() -> dispatch.recordStatus(status, "id-1", " ", NOW.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
    dispatch.recordStatus(status, " provider-id ", " Mailbox unavailable ", NOW.plusSeconds(2));
    dispatch.recordStatus(OfferDispatchStatus.SENT, "older", null, NOW.plusSeconds(1));
    assertThat(dispatch.getStatus()).isEqualTo(status);
    assertThat(dispatch.getProviderMessageId()).isEqualTo("provider-id");
    assertThat(dispatch.getFailureReason()).isEqualTo("Mailbox unavailable");
    assertThat(dispatch.getSentAt()).isEqualTo(NOW.plusSeconds(2));
  }

  @Test
  void successfulDeliveryCanClearFailureEvidenceAndRejectsMissingEventTime() {
    OfferDispatch dispatch =
        new OfferDispatch(offer, publication, 1, UUID.randomUUID(), "applicant@example.test", NOW);
    assertThatThrownBy(() -> dispatch.recordStatus(OfferDispatchStatus.SENT, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("time");
    dispatch.recordStatus(
        OfferDispatchStatus.FAILED, "id", "Temporary failure", NOW.plusSeconds(1));
    dispatch.recordStatus(OfferDispatchStatus.SENT, " ", " ", NOW.plusSeconds(2));
    assertThat(dispatch.getStatus()).isEqualTo(OfferDispatchStatus.SENT);
    assertThat(dispatch.getFailureReason()).isNull();
    assertThat(dispatch.getProviderMessageId()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"method", "blankMethod", "recipient", "blankRecipient"})
  void legacySentEvidenceStillRequiresMethodAndDestination(String missing) {
    assertThatThrownBy(
            () ->
                new OfferDispatch(
                    offer,
                    missing.equals("method") ? null : missing.equals("blankMethod") ? " " : "EMAIL",
                    missing.equals("recipient")
                        ? null
                        : missing.equals("blankRecipient") ? " " : "applicant@example.test",
                    null,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void legacySentEvidenceWithUnknownTimeCanReceiveDatedStatus() {
    OfferDispatch dispatch = new OfferDispatch(offer, "EMAIL", "applicant@example.test", " ", null);
    dispatch.recordStatus(OfferDispatchStatus.SENT, null, null, NOW);
    assertThat(dispatch.getSentAt()).isEqualTo(NOW);
    assertThat(dispatch.getProviderMessageId()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"version", "requester", "requestedAt"})
  void documentVersionRequiresPositiveSequenceRequesterAndTime(String missing) {
    assertThatThrownBy(
            () ->
                new OfferDocumentVersion(
                    offer,
                    missing.equals("version") ? 0 : 1,
                    missing.equals("requester") ? null : actor,
                    missing.equals("requestedAt") ? null : NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "number", "bucket", "key", "checksum", "time"})
  void storingDocumentRequiresCompleteDurabilityEvidence(String missing) {
    OfferDocumentVersion requested = new OfferDocumentVersion(offer, 2, actor, NOW);
    assertThatThrownBy(
            () ->
                requested.store(
                    missing.equals("id") ? null : UUID.randomUUID(),
                    missing.equals("number") ? " " : "DOC-2",
                    missing.equals("bucket") ? null : "documents",
                    missing.equals("key") ? " " : "offer.pdf",
                    missing.equals("checksum") ? null : "checksum",
                    missing.equals("time") ? null : NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
    assertThat(requested.getStatus()).isEqualTo(OfferDocumentVersionStatus.REQUESTED);
  }

  @Test
  void storedVersionIsIdempotentForItsDocumentButImmutableAgainstReplacement() {
    document.store(
        document.getGeneratedDocumentId(),
        "IGNORED",
        "other",
        "other",
        "other",
        NOW.plusSeconds(1));
    assertThat(document.getDocumentNumber()).isEqualTo("DOC-1");
    assertThatThrownBy(
            () ->
                document.store(
                    UUID.randomUUID(), "DOC-2", "documents", "other.pdf", "checksum", NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("another document");
    assertThatThrownBy(() -> document.fail("Late error"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("requested");
  }

  @Test
  void failedGenerationIsRetainedAndRequiresANewDocumentVersion() {
    OfferDocumentVersion failed = new OfferDocumentVersion(offer, 2, actor, NOW);
    failed.fail(" Rendering failed ");
    assertThat(failed.getFailureReason()).isEqualTo("Rendering failed");
    assertThatThrownBy(
            () ->
                failed.store(UUID.randomUUID(), "DOC-2", "documents", "offer.pdf", "checksum", NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("requested");
    assertThatThrownBy(() -> new OfferPublication(offer, failed, 2, actor, UUID.randomUUID(), NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stored");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void failedGenerationRequiresReason(String reason) {
    OfferDocumentVersion requested = new OfferDocumentVersion(offer, 2, actor, NOW);
    assertThatThrownBy(() -> requested.fail(reason))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void portalPublicationSupersessionIsIdempotentAndIndependentOfEmailFailure() {
    publication.recordEmailStatus(
        OfferEmailDeliveryStatus.FAILED, null, "Mailbox unavailable", NOW.plusSeconds(1));
    assertThat(publication.isCurrentPublication()).isTrue();
    publication.supersede(NOW.plusSeconds(2));
    publication.supersede(NOW.plusSeconds(3));
    assertThat(publication.isCurrentPublication()).isFalse();
    assertThat(publication.getSupersededAt()).isEqualTo(NOW.plusSeconds(2));
    publication.recordEmailStatus(OfferEmailDeliveryStatus.SENT, "older", null, NOW);
    assertThat(publication.getEmailDeliveryStatus()).isEqualTo(OfferEmailDeliveryStatus.FAILED);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

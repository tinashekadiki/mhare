package zw.ac.uz.emhare.admissions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Direct-offer ownership, publication and amendment invariants. @author Tinashe K */
class AdmissionOfferInvariantTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final LocalDate COMMENCEMENT = LocalDate.of(2026, 9, 1);
  private final UUID actor = UUID.randomUUID();

  @ParameterizedTest
  @ValueSource(strings = {"decision", "choice", "application"})
  void directOfferRequiresTheSameAdmittedChoiceAndOwningApplication(String invalid) {
    Application application = application();
    ApplicationProgrammeChoice choice = choice(application);
    ProgrammeChoiceDecision decision =
        new ProgrammeChoiceDecision(
            application,
            invalid.equals("choice") ? choice(application) : choice,
            invalid.equals("decision") ? DecisionOutcome.REJECT : DecisionOutcome.ADMIT,
            "Decision reason",
            null,
            actor,
            NOW);
    assertThatThrownBy(
            () ->
                new AdmissionOffer(
                    invalid.equals("application") ? application() : application,
                    choice,
                    decision,
                    "OFFER-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "deadline",
        "past",
        "equal",
        "commencement",
        "conditions",
        "blankConditions",
        "registration",
        "orientation"
      })
  void draftTermsRequireFutureDeadlineConditionalEvidenceAndChronologicalDates(String invalid) {
    AdmissionOffer offer = offer();
    OfferType type =
        invalid.equals("conditions") || invalid.equals("blankConditions")
            ? OfferType.CONDITIONAL
            : OfferType.FIRM;
    assertThatThrownBy(
            () ->
                offer.updateTerms(
                    type,
                    invalid.equals("blankConditions") ? " " : null,
                    invalid.equals("deadline")
                        ? null
                        : invalid.equals("past")
                            ? NOW.minusSeconds(1)
                            : invalid.equals("equal") ? NOW : NOW.plusSeconds(3600),
                    invalid.equals("registration")
                        ? COMMENCEMENT.plusDays(1)
                        : COMMENCEMENT.minusDays(1),
                    invalid.equals("orientation") ? COMMENCEMENT.plusDays(1) : COMMENCEMENT,
                    invalid.equals("commencement") ? null : COMMENCEMENT,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.DRAFT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "ACCEPTED", "DECLINED", "WITHDRAWN"})
  void termsCannotBeEditedAfterTheOfferLeavesItsUnansweredEditableStates(String state) {
    AdmissionOffer offer = offer();
    terms(offer);
    OfferDocumentVersion document = stored(offer);
    offer.linkCurrentDocumentVersion(document);
    if (state.equals("APPROVED")) offer.approve(actor, NOW);
    else if (state.equals("WITHDRAWN")) offer.withdraw(actor, "Decision withdrawn");
    else {
      publish(offer, document);
      offer.respond(OfferResponseType.valueOf(state));
    }
    assertThatThrownBy(
            () ->
                offer.updateTerms(
                    OfferType.FIRM, null, NOW.plusSeconds(7200), null, null, COMMENCEMENT, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only a draft or unanswered");
  }

  @ParameterizedTest
  @ValueSource(strings = {"requested", "foreignOffer"})
  void onlyAStoredDocumentFromThisOfferCanBecomeCurrent(String invalid) {
    AdmissionOffer offer = offer();
    OfferDocumentVersion document =
        invalid.equals("requested")
            ? new OfferDocumentVersion(offer, 1, actor, NOW)
            : stored(offer());
    assertThatThrownBy(() -> offer.linkCurrentDocumentVersion(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not belong");
    assertThat(offer.getCurrentDocumentVersion()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"noCurrent", "differentCurrent", "missingTerms", "approved"})
  void publicationRequiresCurrentStoredVersionCompleteTermsAndPublishableState(String invalid) {
    AdmissionOffer offer = offer();
    OfferDocumentVersion document = stored(offer);
    if (!invalid.equals("missingTerms")) terms(offer);
    if (!invalid.equals("noCurrent"))
      offer.linkCurrentDocumentVersion(
          invalid.equals("differentCurrent") ? stored(offer) : document);
    if (invalid.equals("approved")) offer.approve(actor, NOW);
    assertThatThrownBy(() -> publish(offer, document)).isInstanceOf(IllegalStateException.class);
    assertThat(offer.getCurrentPublication()).isNull();
  }

  @Test
  void unansweredPublishedTermsCanBeAmendedButResponseWaitsForNewPublication() {
    AdmissionOffer offer = offer();
    terms(offer);
    OfferDocumentVersion original = stored(offer);
    offer.linkCurrentDocumentVersion(original);
    publish(offer, original);
    offer.updateTerms(
        OfferType.CONDITIONAL,
        " Present original certificate ",
        NOW.plusSeconds(7200),
        COMMENCEMENT.minusDays(1),
        COMMENCEMENT,
        COMMENCEMENT,
        NOW);
    assertThat(offer.isAmendmentPending()).isTrue();
    assertThat(offer.getConditionsText()).isEqualTo("Present original certificate");
    assertThatThrownBy(() -> offer.respond(OfferResponseType.ACCEPTED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be published");
    OfferDocumentVersion revised = stored(offer);
    offer.linkCurrentDocumentVersion(revised);
    publish(offer, revised);
    assertThat(offer.isAmendmentPending()).isFalse();
    offer.respond(OfferResponseType.ACCEPTED);
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
  }

  @Test
  void repeatedConversionRequestRetainsTheOriginalEventForDownstreamIdempotency() {
    AdmissionOffer offer = offer();
    terms(offer);
    OfferDocumentVersion document = stored(offer);
    offer.linkCurrentDocumentVersion(document);
    publish(offer, document);
    offer.respond(OfferResponseType.ACCEPTED);
    UUID original = offer.requestConversion(NOW);
    UUID repeated = offer.requestConversion(NOW.plusSeconds(1));
    assertThat(repeated).isEqualTo(original);
    assertThat(offer.getConversionRequestedAt()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void expirationRequiresAnAuditableReasonEvenAfterTheAcceptanceDeadline(String reason) {
    AdmissionOffer offer = offer();
    terms(offer);
    OfferDocumentVersion document = stored(offer);
    offer.linkCurrentDocumentVersion(document);
    publish(offer, document);
    assertThatThrownBy(() -> offer.expire(NOW.plusSeconds(7200), reason))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expiry reason is required");
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.SENT);
  }

  private Application application() {
    return identified(
        new Application(
            UUID.randomUUID(),
            "AUG26",
            "August intake",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            3,
            new Applicant(
                UUID.randomUUID(), "A000001", "LOCAL", "Tariro", "Moyo", "applicant@example.test"),
            new ApplicationType("UNDERGRAD", "Undergraduate", false, false),
            "APP-1",
            false));
  }

  private ApplicationProgrammeChoice choice(Application application) {
    return identified(
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
  }

  private AdmissionOffer offer() {
    Application application = application();
    ApplicationProgrammeChoice choice = choice(application);
    return identified(
        new AdmissionOffer(
            application,
            choice,
            new ProgrammeChoiceDecision(
                application, choice, DecisionOutcome.ADMIT, "Approved admission", null, actor, NOW),
            "OFFER-1"));
  }

  private void terms(AdmissionOffer offer) {
    offer.updateTerms(OfferType.FIRM, null, NOW.plusSeconds(3600), null, null, COMMENCEMENT, NOW);
  }

  private OfferDocumentVersion stored(AdmissionOffer offer) {
    OfferDocumentVersion document = identified(new OfferDocumentVersion(offer, 1, actor, NOW));
    document.store(UUID.randomUUID(), "DOC-1", "documents", "offer.pdf", "sha256", NOW);
    return document;
  }

  private void publish(AdmissionOffer offer, OfferDocumentVersion document) {
    offer.publish(
        new OfferPublication(offer, document, 1, actor, UUID.randomUUID(), NOW), actor, NOW);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

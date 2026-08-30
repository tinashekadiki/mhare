package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;

/**
 * User-facing six-stage timeline from persisted application and offer evidence. @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class AdmissionsWorkflowTimelineTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final String REASON = "Verified evidence supports this decision.";
  @Mock private ApplicationRepository applications;
  @Mock private ApplicationClearanceRepository clearances;
  @Mock private ApplicationProgrammeChoiceRepository choices;
  @Mock private AcademicReviewRepository reviews;
  @Mock private AcademicRecommendationRepository recommendations;
  @Mock private ProgrammeChoiceDecisionRepository decisions;
  @Mock private AdmissionOfferRepository offers;
  @Mock private OfferResponseRepository responses;
  @InjectMocks private AdmissionsApplicationWorkflowProgressService service;
  private final UUID actor = UUID.randomUUID();
  private Application application;
  private ApplicationProgrammeChoice choice;

  @BeforeEach
  void setUp() {
    ApplicationType type =
        identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false));
    application =
        identified(
            new Application(
                UUID.randomUUID(),
                "AUG26",
                "August intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                3,
                identified(
                    new Applicant(
                        UUID.randomUUID(),
                        "A000001",
                        "LOCAL",
                        "Tariro",
                        "Moyo",
                        "applicant@example.test")),
                type,
                "APP-1",
                false));
    choice =
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
    lenient().when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    lenient()
        .when(choices.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()))
        .thenReturn(List.of(choice));
  }

  @Test
  void draftAndSubmittedApplicationsRemainAtVerificationUntilClearance() {
    var draft = service.progress(application.getId());
    assertThat(draft.currentStageCode()).isEqualTo("VERIFICATION");
    assertThat(draft.stages().get(0).statusLabel()).isEqualTo("Awaiting submission");
    assertThat(draft.stages())
        .extracting(AdmissionsApplicationWorkflowProgress.WorkflowStage::state)
        .containsExactly("CURRENT", "PENDING", "PENDING", "PENDING", "PENDING", "PENDING");
    application.submit(REASON);
    assertThat(service.progress(application.getId()).stages().get(0).statusLabel())
        .isEqualTo("Checks in progress");
    verified();
    var cleared = service.progress(application.getId());
    assertThat(cleared.currentStageCode()).isEqualTo("ELIGIBILITY");
    assertThat(cleared.stages().get(0).occurredAt()).isEqualTo(NOW);
    assertThat(cleared.stages().get(1).statusLabel()).isEqualTo("Ready to evaluate");
  }

  @Test
  void missingApplicationDoesNotProduceAnInventedTimeline() {
    assertThatThrownBy(() -> service.progress(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application not found.");
  }

  @Test
  void reasonedEligibilityResolutionRemainsCurrentAndCarriesPointsTimestamp() {
    application.submit(REASON);
    verified();
    application.recordCalculatedPoints(new BigDecimal("12"), NOW);
    choice.recordEvaluation(EvaluationStatus.REQUIRES_REVIEW, REASON);
    var timeline = service.progress(application.getId());
    assertThat(timeline.currentStageCode()).isEqualTo("ELIGIBILITY");
    assertThat(timeline.stages().get(1).statusLabel()).isEqualTo("Admissions review required");
    assertThat(timeline.stages().get(1).occurredAt()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @EnumSource(
      value = EvaluationStatus.class,
      names = {"ELIGIBLE", "CONDITIONALLY_ELIGIBLE"})
  void eligibleChoiceWithoutReviewMakesAcademicReviewTheNextAction(EvaluationStatus status) {
    application.submit(REASON);
    verified();
    choice.recordEvaluation(status, REASON);
    var timeline = service.progress(application.getId());
    assertThat(timeline.currentStageCode()).isEqualTo("ACADEMIC_REVIEW");
    assertThat(timeline.stages().get(1).statusLabel()).isEqualTo("Eligible choice found");
    assertThat(timeline.stages().get(2).statusLabel()).isEqualTo("Ready for academic review");
  }

  @Test
  void openReviewShowsResolvedUnitAndRecommendationMovesWorkToAdmissions() {
    application.submit(REASON);
    verified();
    choice.recordEvaluation(EvaluationStatus.ELIGIBLE, REASON);
    choice.enterAcademicReview();
    AcademicReview review = review();
    when(reviews.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(review));
    var opened = service.progress(application.getId());
    assertThat(opened.currentStageCode()).isEqualTo("ACADEMIC_REVIEW");
    assertThat(opened.stages().get(2).detail()).isEqualTo("Science");
    review.claim(actor, NOW, 0);
    assertThat(service.progress(application.getId()).stages().get(2).occurredAt()).isEqualTo(NOW);
    review.markRecommended(actor, 0);
    AcademicRecommendation recommendation =
        new AcademicRecommendation(
            review, 1, RecommendationOutcome.RECOMMEND_ADMIT, REASON, actor, NOW);
    when(recommendations
            .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(
                review.getId()))
        .thenReturn(List.of(recommendation));
    var recommended = service.progress(application.getId());
    assertThat(recommended.currentStageCode()).isEqualTo("ADMISSION_DECISION");
    assertThat(recommended.stages().get(2).statusLabel()).isEqualTo("Recommend admit");
    assertThat(recommended.stages().get(3).statusLabel()).isEqualTo("Awaiting Admissions decision");
  }

  @ParameterizedTest
  @EnumSource(OfferResponseType.class)
  void publishedOfferAndExactResponseDriveFinalTwoStages(OfferResponseType responseType) {
    application.submit(REASON);
    verified();
    choice.recordEvaluation(EvaluationStatus.ELIGIBLE, REASON);
    choice.enterAcademicReview();
    choice.recordDecision(DecisionOutcome.ADMIT, REASON);
    AcademicReview review = review();
    review.claim(actor, NOW, 0);
    review.markRecommended(actor, 0);
    review.complete(NOW);
    AcademicRecommendation recommendation =
        new AcademicRecommendation(
            review, 1, RecommendationOutcome.RECOMMEND_ADMIT, REASON, actor, NOW);
    when(reviews.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(review));
    when(recommendations
            .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(
                review.getId()))
        .thenReturn(List.of(recommendation));
    ProgrammeChoiceDecision decision =
        new ProgrammeChoiceDecision(
            application, choice, DecisionOutcome.ADMIT, REASON, recommendation, actor, NOW);
    when(decisions.findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(
            application.getId()))
        .thenReturn(List.of(decision));
    AdmissionOffer offer = identified(new AdmissionOffer(application, choice, decision, "OFFER-1"));
    when(offers.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(offer));
    var drafted = service.progress(application.getId());
    assertThat(drafted.currentStageCode()).isEqualTo("OFFER");
    assertThat(drafted.stages().get(4).detail()).isEqualTo("OFFER-1 · BSC");
    offer.updateTerms(
        OfferType.FIRM, null, NOW.plusSeconds(3600), null, null, LocalDate.of(2026, 9, 1), NOW);
    OfferDocumentVersion document = identified(new OfferDocumentVersion(offer, 1, actor, NOW));
    document.store(UUID.randomUUID(), "DOC-1", "documents", "offer.pdf", "checksum", NOW);
    offer.linkCurrentDocumentVersion(document);
    OfferPublication publication =
        identified(new OfferPublication(offer, document, 1, actor, UUID.randomUUID(), NOW));
    offer.publish(publication, actor, NOW);
    var published = service.progress(application.getId());
    assertThat(published.currentStageCode()).isEqualTo("RESPONSE");
    assertThat(published.stages().get(4).state()).isEqualTo("COMPLETED");
    assertThat(published.stages().get(5).statusLabel()).isEqualTo("Awaiting applicant response");
    offer.updateTerms(
        OfferType.FIRM, null, NOW.plusSeconds(7200), null, null, LocalDate.of(2026, 9, 1), NOW);
    var amended = service.progress(application.getId());
    assertThat(amended.stages().get(4).statusLabel()).isEqualTo("Amendment pending");
    assertThat(amended.stages().get(5).statusLabel()).isEqualTo("Blocked by pending amendment");
    offer.publish(publication, actor, NOW);
    offer.respond(responseType);
    when(responses.findByOfferId(offer.getId()))
        .thenReturn(
            Optional.of(
                new OfferResponse(
                    offer, publication, responseType, NOW, actor, "Applicant reply")));
    var answered = service.progress(application.getId());
    assertThat(answered.currentStageCode()).isEqualTo("RESPONSE");
    assertThat(answered.stages()).allMatch(stage -> stage.state().equals("COMPLETED"));
    assertThat(answered.stages().get(5).occurredAt()).isEqualTo(NOW);
  }

  @Test
  void rejectionWithoutAcademicDecisionEndsAtDecisionAndMarksOfferNotApplicable() {
    application.submit(REASON);
    application.moveToUnderReview(actor, REASON);
    choice.recordEvaluation(EvaluationStatus.NOT_ELIGIBLE, REASON);
    application.applyEvaluationOutcome(false, true, REASON);
    application.rejectAfterAllChoices(REASON);
    var timeline = service.progress(application.getId());
    assertThat(timeline.currentStageCode()).isEqualTo("ADMISSION_DECISION");
    assertThat(timeline.stages().get(0).state()).isEqualTo("COMPLETED");
    assertThat(timeline.stages().get(1).statusLabel()).isEqualTo("No eligible choice");
    assertThat(timeline.stages().get(3).statusLabel()).isEqualTo("Rejected");
    assertThat(timeline.stages().get(3).detail()).isEqualTo(REASON);
    assertThat(timeline.stages().get(4).state()).isEqualTo("NOT_APPLICABLE");
  }

  private void verified() {
    when(clearances.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
            application.getId(), ApplicationClearanceOutcome.CONFIRMED))
        .thenReturn(
            Optional.of(
                new ApplicationClearance(application, actor, REASON, "Identity checked", NOW)));
  }

  private AcademicReview review() {
    return identified(
        new AcademicReview(
            application,
            choice,
            choice.getOwningAcademicUnitId(),
            "CS",
            "Computing",
            UUID.randomUUID(),
            "SCI",
            "Science",
            "[]"));
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

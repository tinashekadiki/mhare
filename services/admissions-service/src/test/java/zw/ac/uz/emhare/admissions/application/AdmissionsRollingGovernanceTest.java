package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.*;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.*;

/**
 * Real-aggregate coverage of rolling clearance, recommendations and decisions. @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class AdmissionsRollingGovernanceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final String REASON = "Verified evidence supports this decision.";
  @Mock private ApplicationRepository applications;
  @Mock private ApplicationProgrammeChoiceRepository choices;
  @Mock private AdmissionRequirementSetRepository requirements;
  @Mock private ApplicationEvaluationRepository evaluations;
  @Mock private AcademicReviewRepository reviews;
  @Mock private AcademicRecommendationRepository recommendations;
  @Mock private ProgrammeChoiceDecisionRepository decisions;
  @Mock private AdmissionOfferRepository offers;
  @Mock private ApplicationStatusEventRepository applicationEvents;
  @Mock private OfferStatusEventRepository offerEvents;
  @Mock private QualificationEligibilityService eligibility;
  @Mock private AcademicSetupCatalogueClient academic;
  @Mock private AdmissionsIdentifierGenerator identifiers;
  @Mock private AdmissionsDocumentService documents;
  @Mock private ApplicationSectionRepository sections;
  @Mock private ApplicantQualificationSittingRepository qualifications;
  @Mock private ApplicationClearanceRepository clearances;
  @Mock private AdmissionsIntegrationOutboxService outbox;
  @Mock private ApplicationDuplicateCheckService duplicates;
  private AdmissionsRollingWorkflowService service;
  private final UUID actor = UUID.randomUUID();
  private final UUID unit = UUID.randomUUID();
  private ApplicationType type;
  private Application application;
  private List<ApplicationProgrammeChoice> programmeChoices;

  @BeforeEach
  void setUp() {
    service =
        new AdmissionsRollingWorkflowService(
            applications,
            choices,
            requirements,
            evaluations,
            reviews,
            recommendations,
            decisions,
            offers,
            applicationEvents,
            offerEvents,
            eligibility,
            academic,
            identifiers,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            documents,
            sections,
            qualifications,
            clearances,
            outbox,
            duplicates);
    type = identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false));
    application = newApplication(false);
    programmeChoices = new ArrayList<>();
    lenient().when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    lenient()
        .when(choices.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()))
        .thenAnswer(invocation -> programmeChoices);
    lenient()
        .when(reviews.saveAndFlush(any()))
        .thenAnswer(invocation -> identified(invocation.getArgument(0)));
    lenient()
        .when(decisions.saveAndFlush(any()))
        .thenAnswer(invocation -> identified(invocation.getArgument(0)));
    lenient()
        .when(offers.saveAndFlush(any()))
        .thenAnswer(invocation -> identified(invocation.getArgument(0)));
  }

  @Test
  void advanceClearsVerifiedEvidenceAndPersistsDuplicateEvidenceOnce() {
    application.submit(REASON);
    choice(EvaluationStatus.REQUIRES_REVIEW);
    readyEvidence();
    service.advance(application.getId(), actor);
    service.advance(application.getId(), actor);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    ArgumentCaptor<ApplicationClearance> capture =
        ArgumentCaptor.forClass(ApplicationClearance.class);
    verify(clearances).save(capture.capture());
    assertThat(capture.getValue().getOutcome()).isEqualTo(ApplicationClearanceOutcome.CONFIRMED);
    verify(outbox).enqueueVerificationDecisionNotification(application);
    verify(applicationEvents).save(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"duplicate", "payment", "documents", "section", "noQualifications", "unverified"})
  void advanceKeepsIncompleteEvidenceAtSubmittedWithoutSideEffects(String blocker) {
    if (blocker.equals("payment")) {
      application = newApplication(true);
      when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    }
    application.submit(REASON);
    readyEvidence();
    switch (blocker) {
      case "duplicate" ->
          when(duplicates.check(application))
              .thenReturn(
                  new ApplicationDuplicateCheckService.DuplicateCheckResult(
                      false, "Duplicate identity."));
      case "documents" -> when(documents.isReadyForReview(application)).thenReturn(false);
      case "section" ->
          when(sections.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                  application.getId()))
              .thenReturn(List.of(section(true, false)));
      case "noQualifications" ->
          when(qualifications.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(
                  application.getId()))
              .thenReturn(List.of());
      case "unverified" ->
          when(qualifications.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(
                  application.getId()))
              .thenReturn(
                  List.of(
                      new ApplicantQualificationSitting(
                          application, QualificationLevel.A_LEVEL, null, "01", "02", 2025)));
      default -> {}
    }
    service.advance(application.getId(), actor);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    verifyNoInteractions(clearances, applicationEvents, outbox);
  }

  @Test
  void advancePreservesExistingClearanceWhileIgnoringOptionalIncompleteSections() {
    application.submit(REASON);
    choice(EvaluationStatus.REQUIRES_REVIEW);
    readyEvidence();
    when(sections.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId()))
        .thenReturn(List.of(section(false, false), section(true, true)));
    when(clearances.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
            application.getId(), ApplicationClearanceOutcome.CONFIRMED))
        .thenReturn(
            Optional.of(
                new ApplicationClearance(application, actor, REASON, "Checks passed.", NOW)));
    service.advance(application.getId(), actor);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    verify(clearances, never()).save(any());
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,ELIGIBLE,UNDER_ACADEMIC_REVIEW",
    "true,false,NOT_ELIGIBLE,REJECTED",
    "false,true,REQUIRES_REVIEW,UNDER_REVIEW"
  })
  void advanceEvaluatesRequirementsAndRecordsAuditableOutcome(
      boolean missing, boolean advancedRules, String outcome, String status) {
    underReview();
    ApplicationProgrammeChoice choice = choice(null);
    AdmissionRequirementSet requirement =
        requirement(choice, advancedRules, LocalDate.of(2026, 1, 1));
    when(requirements.findApprovedForRouteForUpdate(
            choice.getProgrammeId(), type.getId(), application.getIntakeId()))
        .thenReturn(List.of(requirement));
    when(eligibility.evaluateRequirements(application, requirement))
        .thenReturn(
            new QualificationEligibilityService.RequirementEvaluation(
                new BigDecimal("12"),
                missing ? List.of("ENGLISH") : List.of(),
                List.of(Map.of("code", "ENGLISH")),
                Map.of("points", 12)));
    if (!missing && !advancedRules) hierarchy(choice);
    service.advance(application.getId(), actor);
    assertThat(application.getStatus().name()).isEqualTo(status);
    ArgumentCaptor<ApplicationEvaluation> capture =
        ArgumentCaptor.forClass(ApplicationEvaluation.class);
    verify(evaluations).save(capture.capture());
    assertThat(capture.getValue().getStatus().name()).isEqualTo(outcome);
    assertThat(capture.getValue().getTotalPoints()).isEqualByComparingTo("12");
    assertThat(capture.getValue().getRequirementSet()).isSameAs(requirement);
    if (missing) assertThat(choice.getEvaluationSummary()).contains("ENGLISH");
    if (!missing && !advancedRules) {
      assertThat(choice.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW);
      ArgumentCaptor<AcademicReview> reviewCapture = ArgumentCaptor.forClass(AcademicReview.class);
      verify(reviews).saveAndFlush(reviewCapture.capture());
      assertThat(reviewCapture.getValue().getRecommendationAcademicUnitId()).isEqualTo(unit);
      assertThat(reviewCapture.getValue().getHierarchyPathJson()).contains("SCI");
    }
  }

  @Test
  void advanceSelectsLatestEffectiveRequirementAndDoesNotDuplicateEvaluation() {
    underReview();
    ApplicationProgrammeChoice choice = choice(null);
    AdmissionRequirementSet old = requirement(choice, false, LocalDate.of(2025, 1, 1));
    AdmissionRequirementSet latest = requirement(choice, false, LocalDate.of(2026, 1, 1));
    AdmissionRequirementSet future = requirement(choice, false, LocalDate.of(2027, 1, 1));
    when(requirements.findApprovedForRouteForUpdate(
            choice.getProgrammeId(), type.getId(), application.getIntakeId()))
        .thenReturn(List.of(old, future, latest));
    when(eligibility.evaluateRequirements(application, latest))
        .thenReturn(
            new QualificationEligibilityService.RequirementEvaluation(
                BigDecimal.ZERO, List.of("POINTS"), List.of(), Map.of()));
    when(evaluations.existsByProgrammeChoiceIdAndRequirementSetIdAndDeletedAtIsNull(
            choice.getId(), latest.getId()))
        .thenReturn(true);
    service.advance(application.getId(), actor);
    verify(eligibility).evaluateRequirements(application, latest);
    verify(evaluations, never()).save(any());
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
  }

  @Test
  void advanceDoesNotReevaluateEligibleChoiceOrOpenAnotherActiveReview() {
    underReview();
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    when(reviews.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(review(choice)));
    service.advance(application.getId(), actor);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ELIGIBLE);
    verifyNoInteractions(requirements, eligibility, academic);
  }

  @ParameterizedTest
  @ValueSource(strings = {"COMPLETED", "CANCELLED"})
  void advanceIgnoresClosedHistoricalReviews(String status) {
    underReview();
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.CONDITIONALLY_ELIGIBLE);
    AcademicReview previous = review(choice);
    if (status.equals("COMPLETED")) {
      previous.claim(actor, NOW, 0);
      previous.markRecommended(actor, 0);
      previous.complete(NOW);
    } else previous.cancel(NOW);
    when(reviews.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(previous));
    hierarchy(choice);
    service.advance(application.getId(), actor);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_ACADEMIC_REVIEW);
    verify(reviews).saveAndFlush(any());
  }

  @Test
  void recalculationStoresServerCalculatedPointsBeforeResolvingChoices() {
    underReview();
    choice(EvaluationStatus.REQUIRES_REVIEW);
    when(eligibility.recalculateApplicationPoints(application.getId()))
        .thenReturn(
            new QualificationPointsCalculator.EligibilitySnapshot(
                new BigDecimal("15"), List.of(), List.of()));
    service.recalculateEligibility(application.getId(), actor);
    assertThat(application.getCalculatedTotalPoints()).isEqualByComparingTo("15");
    assertThat(application.getPointsCalculatedAt()).isEqualTo(NOW);
  }

  @Test
  void recalculationRejectsDraftBeforeCallingTheCalculator() {
    assertThatThrownBy(() -> service.recalculateEligibility(application.getId(), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("under review");
    verifyNoInteractions(eligibility);
  }

  @ParameterizedTest
  @ValueSource(strings = {" eligible ", "CONDITIONALLY_ELIGIBLE", "NOT_ELIGIBLE"})
  void manualResolutionRecordsReasonAndOpensOnlyEligibleReview(String outcome) {
    underReview();
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.REQUIRES_REVIEW);
    if (!outcome.equals("NOT_ELIGIBLE")) hierarchy(choice);
    service.resolveEligibility(
        application.getId(), choice.getId(), outcome, "  " + REASON + "  ", actor);
    assertThat(choice.getEvaluationSummary()).isEqualTo(REASON);
    assertThat(application.getStatus())
        .isEqualTo(
            outcome.equals("NOT_ELIGIBLE")
                ? ApplicationStatus.REJECTED
                : ApplicationStatus.UNDER_ACADEMIC_REVIEW);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"REQUIRES_REVIEW", "unsupported"})
  void manualResolutionRejectsUnsupportedOutcomes(String outcome) {
    underReview();
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.REQUIRES_REVIEW);
    assertThatThrownBy(
            () ->
                service.resolveEligibility(
                    application.getId(), choice.getId(), outcome, REASON, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(choice.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.REQUIRES_REVIEW);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void manualResolutionRequiresReason(String reason) {
    underReview();
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.REQUIRES_REVIEW);
    assertThatThrownBy(
            () ->
                service.resolveEligibility(
                    application.getId(), choice.getId(), "ELIGIBLE", reason, actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A reason is required.");
  }

  @Test
  void manualResolutionCannotOverrideAlreadyEligibleChoice() {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    assertThatThrownBy(
            () ->
                service.resolveEligibility(
                    application.getId(), choice.getId(), "NOT_ELIGIBLE", REASON, actor))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"nullProfile", "nullUser", "inactive", "nullRoles", "wrongRole", "wrongUnit"})
  void recommendationRequiresActiveExactAcademicUnitAssignment(String invalidScope) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    AcademicReview review = review(choice);
    lookupReview(choice, review);
    CoreCurrentUserProfile profile = profile(actor, unit, "ACTIVE", "ACADEMIC_UNIT_STAFF");
    profile =
        switch (invalidScope) {
          case "nullProfile" -> null;
          case "nullUser" -> new CoreCurrentUserProfile(null, profile.roleAssignments());
          case "inactive" -> profile(actor, unit, "DISABLED", "ACADEMIC_UNIT_STAFF");
          case "nullRoles" -> new CoreCurrentUserProfile(profile.user(), null);
          case "wrongRole" -> profile(actor, unit, "ACTIVE", "ADMISSIONS_MANAGER");
          default -> profile(actor, UUID.randomUUID(), "ACTIVE", "ACADEMIC_UNIT_STAFF");
        };
    CoreCurrentUserProfile suppliedProfile = profile;
    assertThatThrownBy(
            () ->
                service.recommend(
                    application.getId(),
                    choice.getId(),
                    "RECOMMEND_ADMIT",
                    REASON,
                    suppliedProfile))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(review.getStatus()).isEqualTo(AcademicReviewStatus.OPEN);
    verifyNoInteractions(recommendations);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OPEN", "RETURNED", "CLAIMED"})
  void recommendationClaimsOpenOrReturnedReviewAndSequencesAdvisoryEvidence(String status) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    AcademicReview review = review(choice);
    if (!status.equals("OPEN")) review.claim(actor, NOW, 0);
    if (status.equals("RETURNED")) {
      review.markRecommended(actor, 0);
      review.returnForReconsideration();
    }
    lookupReview(choice, review);
    when(recommendations.countByAcademicReviewIdAndDeletedAtIsNull(review.getId())).thenReturn(2);
    service.recommend(
        application.getId(),
        choice.getId(),
        " recommend_admit ",
        " " + REASON + " ",
        profile(actor, unit, "ACTIVE", "ACADEMIC_UNIT_STAFF"));
    assertThat(review.getStatus()).isEqualTo(AcademicReviewStatus.RECOMMENDED);
    ArgumentCaptor<AcademicRecommendation> capture =
        ArgumentCaptor.forClass(AcademicRecommendation.class);
    verify(recommendations).save(capture.capture());
    assertThat(capture.getValue().getRecommendationSequence()).isEqualTo(3);
    assertThat(capture.getValue().getReason()).isEqualTo(REASON);
    assertThat(capture.getValue().getReviewStatus())
        .isEqualTo(AcademicRecommendationReviewStatus.PENDING);
    verifyNoInteractions(decisions, offers);
  }

  @Test
  void returningRecommendationPreservesHistoryAndReopensReview() {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    AcademicReview review = recommendedReview(choice);
    AcademicRecommendation recommendation =
        pendingRecommendation(review, RecommendationOutcome.RECOMMEND_ADMIT);
    service.returnRecommendation(application.getId(), choice.getId(), " " + REASON + " ", actor);
    assertThat(review.getStatus()).isEqualTo(AcademicReviewStatus.RETURNED);
    assertThat(review.getClaimedByUserId()).isNull();
    assertThat(recommendation.getReviewStatus())
        .isEqualTo(AcademicRecommendationReviewStatus.RETURNED);
    assertThat(recommendation.getReviewReason()).isEqualTo(REASON);
  }

  @ParameterizedTest
  @CsvSource({
    "RECOMMEND_ADMIT,ADMIT,APPROVED",
    "RECOMMEND_REJECT,ADMIT,OVERRIDDEN",
    "RECOMMEND_REJECT,REJECT,APPROVED",
    "RECOMMEND_ADMIT,REJECT,OVERRIDDEN"
  })
  void decisionAuditsRecommendationAgreementAndProducesOnlyAdmittedOffers(
      RecommendationOutcome recommended,
      DecisionOutcome decision,
      AcademicRecommendationReviewStatus reviewStatus) {
    underReview();
    application.applyEvaluationOutcome(true, true, REASON);
    application.enterAcademicReview(REASON);
    ApplicationProgrammeChoice first = choice(EvaluationStatus.ELIGIBLE);
    first.enterAcademicReview();
    ApplicationProgrammeChoice lower =
        choice(
            decision == DecisionOutcome.ADMIT
                ? EvaluationStatus.ELIGIBLE
                : EvaluationStatus.NOT_ELIGIBLE);
    AcademicReview review = recommendedReview(first);
    AcademicRecommendation recommendation = pendingRecommendation(review, recommended);
    if (decision == DecisionOutcome.ADMIT)
      when(identifiers.nextOfferNumber(application.getIntakeCode())).thenReturn("OFFER-2026-1");
    AdmissionOfferSummary summary =
        service.decide(application.getId(), first.getId(), decision.name(), REASON, actor);
    assertThat(recommendation.getReviewStatus()).isEqualTo(reviewStatus);
    assertThat(review.getStatus()).isEqualTo(AcademicReviewStatus.COMPLETED);
    assertThat(application.getStatus())
        .isEqualTo(
            decision == DecisionOutcome.ADMIT
                ? ApplicationStatus.ADMITTED
                : ApplicationStatus.REJECTED);
    if (decision == DecisionOutcome.ADMIT) {
      assertThat(summary.status()).isEqualTo("DRAFT");
      assertThat(summary.offerBatchId()).isNull();
      assertThat(summary.offerNumber()).isEqualTo("OFFER-2026-1");
      assertThat(lower.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.REJECTED);
      verify(offerEvents).save(any());
    } else {
      assertThat(summary).isNull();
      verifyNoInteractions(offers, offerEvents);
    }
  }

  @Test
  void rejectionOpensNextConditionalChoiceWithoutRejectingTheApplication() {
    underReview();
    application.applyEvaluationOutcome(true, true, REASON);
    application.enterAcademicReview(REASON);
    ApplicationProgrammeChoice first = choice(EvaluationStatus.ELIGIBLE);
    first.enterAcademicReview();
    ApplicationProgrammeChoice second = choice(EvaluationStatus.CONDITIONALLY_ELIGIBLE);
    pendingRecommendation(recommendedReview(first), RecommendationOutcome.RECOMMEND_REJECT);
    hierarchy(second);
    assertThat(service.decide(application.getId(), first.getId(), "REJECT", REASON, actor))
        .isNull();
    assertThat(second.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_ACADEMIC_REVIEW);
    verify(choices).saveAndFlush(second);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void repeatedDecisionReturnsExistingOutcomeWithoutDuplicatingEvidence(boolean hasOffer) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    when(decisions.existsByProgrammeChoiceIdAndDeletedAtIsNull(choice.getId())).thenReturn(true);
    if (hasOffer) {
      ProgrammeChoiceDecision decision =
          new ProgrammeChoiceDecision(
              application, choice, DecisionOutcome.ADMIT, REASON, null, actor, NOW);
      AdmissionOffer existing =
          identified(new AdmissionOffer(application, choice, decision, "OFFER-EXISTING"));
      when(offers.findByProgrammeChoiceIdAndDeletedAtIsNull(choice.getId()))
          .thenReturn(Optional.of(existing));
    }
    AdmissionOfferSummary summary =
        service.decide(application.getId(), choice.getId(), "ADMIT", REASON, actor);
    if (hasOffer) assertThat(summary.offerNumber()).isEqualTo("OFFER-EXISTING");
    else assertThat(summary).isNull();
    verifyNoInteractions(reviews, recommendations, offerEvents, applicationEvents);
    verify(decisions, never()).saveAndFlush(any());
  }

  @Test
  void commandsRejectMissingApplicationChoiceOrCrossApplicationChoice() {
    assertThatThrownBy(() -> service.advance(UUID.randomUUID(), actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application not found.");
    assertThatThrownBy(
            () ->
                service.resolveEligibility(
                    application.getId(), UUID.randomUUID(), "ELIGIBLE", REASON, actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Programme choice not found.");
    Application other = newApplication(false);
    ApplicationProgrammeChoice foreign =
        identified(new ApplicationProgrammeChoice(other, snapshot(), 1));
    when(choices.findById(foreign.getId())).thenReturn(Optional.of(foreign));
    assertThatThrownBy(
            () ->
                service.resolveEligibility(
                    application.getId(), foreign.getId(), "ELIGIBLE", REASON, actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not belong");
  }

  @ParameterizedTest
  @ValueSource(strings = {"recommend", "return", "decide"})
  void commandsRequireExistingAcademicReview(String command) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    assertThatThrownBy(
            () -> {
              switch (command) {
                case "recommend" ->
                    service.recommend(
                        application.getId(),
                        choice.getId(),
                        "RECOMMEND_ADMIT",
                        REASON,
                        profile(actor, unit, "ACTIVE", "ACADEMIC_UNIT_STAFF"));
                case "return" ->
                    service.returnRecommendation(
                        application.getId(), choice.getId(), REASON, actor);
                default ->
                    service.decide(application.getId(), choice.getId(), "ADMIT", REASON, actor);
              }
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Academic review");
  }

  @ParameterizedTest
  @ValueSource(strings = {"return", "decide"})
  void admissionActionsRequirePendingRecommendation(String command) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    lookupReview(choice, review(choice));
    assertThatThrownBy(
            () -> {
              if (command.equals("return"))
                service.returnRecommendation(application.getId(), choice.getId(), REASON, actor);
              else service.decide(application.getId(), choice.getId(), "ADMIT", REASON, actor);
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recommendation");
  }

  private Application newApplication(boolean feeRequired) {
    return identified(
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
            feeRequired));
  }

  private void underReview() {
    application.submit(REASON);
    application.moveToUnderReview(actor, REASON);
  }

  private ApplicationProgrammeChoice choice(EvaluationStatus status) {
    ApplicationProgrammeChoice choice =
        identified(
            new ApplicationProgrammeChoice(application, snapshot(), programmeChoices.size() + 1));
    if (status != null) choice.recordEvaluation(status, REASON);
    programmeChoices.add(choice);
    lenient().when(choices.findById(choice.getId())).thenReturn(Optional.of(choice));
    return choice;
  }

  private ProgrammeSelectionSnapshot snapshot() {
    return new ProgrammeSelectionSnapshot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BSC",
        "Science",
        "BSc",
        UUID.randomUUID(),
        "Computing",
        "2026");
  }

  private AcademicReview review(ApplicationProgrammeChoice choice) {
    return identified(
        new AcademicReview(
            application,
            choice,
            choice.getOwningAcademicUnitId(),
            "CS",
            "Computing",
            unit,
            "SCI",
            "Science",
            "[]"));
  }

  private void lookupReview(ApplicationProgrammeChoice choice, AcademicReview review) {
    when(reviews.findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(
            application.getId(), choice.getId()))
        .thenReturn(Optional.of(review));
  }

  private AcademicReview recommendedReview(ApplicationProgrammeChoice choice) {
    AcademicReview review = review(choice);
    review.claim(actor, NOW, 0);
    review.markRecommended(actor, 0);
    lookupReview(choice, review);
    return review;
  }

  private AcademicRecommendation pendingRecommendation(
      AcademicReview review, RecommendationOutcome outcome) {
    AcademicRecommendation recommendation =
        identified(new AcademicRecommendation(review, 1, outcome, REASON, actor, NOW));
    when(recommendations.findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(
            review.getId(), AcademicRecommendationReviewStatus.PENDING))
        .thenReturn(Optional.of(recommendation));
    return recommendation;
  }

  private AdmissionRequirementSet requirement(
      ApplicationProgrammeChoice choice, boolean advanced, LocalDate effectiveFrom) {
    AdmissionRequirementSet set =
        identified(
            new AdmissionRequirementSet(
                choice.getProgrammeId(),
                type,
                application.getIntakeId(),
                effectiveFrom.toString(),
                effectiveFrom,
                null,
                BigDecimal.TEN,
                null,
                null,
                false,
                false,
                advanced ? "{}" : null,
                advanced ? "advanced_rules_v1" : null));
    set.approve(actor, NOW);
    return set;
  }

  private void hierarchy(ApplicationProgrammeChoice choice) {
    AcademicUnitHierarchyNode highest =
        new AcademicUnitHierarchyNode(
            unit, UUID.randomUUID(), "FACULTY", null, "SCI", "Science", "ACTIVE", null, null, 0);
    AcademicUnitHierarchyNode owning =
        new AcademicUnitHierarchyNode(
            choice.getOwningAcademicUnitId(),
            UUID.randomUUID(),
            "DEPARTMENT",
            unit,
            "CS",
            "Computing",
            "ACTIVE",
            null,
            null,
            0);
    when(academic.getProgrammeHierarchy(choice.getProgrammeId()))
        .thenReturn(
            new ProgrammeHierarchyResolution(
                choice.getProgrammeId(),
                "BSC",
                "Science",
                owning,
                highest,
                List.of(owning, highest)));
  }

  private void readyEvidence() {
    lenient()
        .when(duplicates.check(application))
        .thenReturn(
            new ApplicationDuplicateCheckService.DuplicateCheckResult(
                true, "Identity checks passed."));
    lenient().when(documents.isReadyForReview(application)).thenReturn(true);
    lenient()
        .when(
            sections.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                application.getId()))
        .thenReturn(List.of(section(true, true)));
    ApplicantQualificationSitting sitting =
        new ApplicantQualificationSitting(
            application, QualificationLevel.A_LEVEL, null, "01", "02", 2025);
    sitting.verify(actor, NOW);
    lenient()
        .when(
            qualifications.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(
                application.getId()))
        .thenReturn(List.of(sitting));
  }

  private ApplicationSection section(boolean required, boolean complete) {
    ApplicationSection section =
        new ApplicationSection(
            application,
            new ApplicationTypeSection(
                type, "PERSONAL", "Personal details", required, false, 1, 1));
    if (complete) section.recordStatus(ApplicationSectionStatus.COMPLETE, REASON, NOW);
    return section;
  }

  private CoreCurrentUserProfile profile(
      UUID userId, UUID academicUnit, String status, String role) {
    return new CoreCurrentUserProfile(
        new CoreUserSummary(
            userId, UUID.randomUUID(), "staff", "staff@example.test", "Academic Staff", status),
        List.of(
            new CoreRoleAssignmentSummary(
                UUID.randomUUID(), UUID.randomUUID(), role, "Academic Staff", academicUnit)));
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

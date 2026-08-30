package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.*;

/** Public work-item contracts backed by real admissions aggregates. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsWorkItemCaseTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final String REASON = "Verified evidence supports this decision.";
  @Mock private ApplicationRepository applications;
  @Mock private ApplicationProgrammeChoiceRepository choices;
  @Mock private AcademicReviewRepository reviews;
  @Mock private AcademicRecommendationRepository recommendations;
  @Mock private ProgrammeChoiceDecisionRepository decisions;
  @Mock private AdmissionOfferRepository offers;
  @Mock private OfferConditionRepository conditions;
  @Mock private OfferResponseRepository responses;
  @Mock private OfferDocumentVersionRepository documents;
  @Mock private OfferPublicationRepository publications;
  @Mock private ApplicationStatusEventRepository events;
  @Mock private ApplicantApplicationWorkspaceService workspace;
  @Mock private AdmissionsApplicationWorkflowProgressService progress;
  @Mock private ApplicationDuplicateCheckService duplicates;
  @InjectMocks private AdmissionsWorkItemService service;
  private final UUID actor = UUID.randomUUID();
  private final UUID academicUnit = UUID.randomUUID();
  private Application application;
  private ApplicationType type;
  private List<ApplicationProgrammeChoice> programmeChoices;

  @BeforeEach
  void setUp() {
    type = identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false));
    application = application(false);
    programmeChoices = new ArrayList<>();
    lenient().when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    lenient()
        .when(choices.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()))
        .thenAnswer(call -> programmeChoices);
  }

  @Test
  void missingApplicationFailsBeforeLoadingOtherCaseEvidence() {
    assertThatThrownBy(() -> service.get(UUID.randomUUID(), officer()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application not found.");
    verifyNoInteractions(workspace, reviews, offers);
  }

  @Test
  void draftCaseKeepsAbsentWorkflowEvidenceExplicitAndShowsSubmissionBlocker() {
    var result = service.get(application.getId(), officer());
    assertThat(result.academicReview()).isNull();
    assertThat(result.academicRecommendation()).isNull();
    assertThat(result.admissionDecision()).isNull();
    assertThat(result.offer()).isNull();
    assertThat(result.documentVersions()).isEmpty();
    assertThat(result.publications()).isEmpty();
    assertThat(result.blockers()).containsExactly("Application has not been submitted.");
    verify(workspace).staffWorkspace(application.getId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"noReview", "noRoles", "wrongRole", "wrongUnit"})
  void academicCaseAccessRejectsUnassignedOrDifferentUnitStaff(String kind) {
    AcademicReview review = review(choice(EvaluationStatus.ELIGIBLE));
    if (!kind.equals("noReview")) givenReview(review);
    CoreCurrentUserProfile profile =
        kind.equals("noRoles")
            ? profile(null, null)
            : reviewer(
                kind.equals("wrongUnit") ? UUID.randomUUID() : academicUnit,
                kind.equals("wrongRole") ? "OTHER" : "ACADEMIC_UNIT_STAFF");
    assertThatThrownBy(() -> service.get(application.getId(), profile))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("outside");
    verifyNoInteractions(workspace, offers);
  }

  @Test
  void exactAcademicUnitCanReadCaseWithoutSystemReviewPermission() {
    AcademicReview review = review(choice(EvaluationStatus.ELIGIBLE));
    givenReview(review);
    var result = service.get(application.getId(), reviewer(academicUnit, "ACADEMIC_UNIT_STAFF"));
    assertThat(result.academicReview().recommendationAcademicUnitId()).isEqualTo(academicUnit);
    assertThat(result.availableActions()).containsExactly("RECORD_ACADEMIC_RECOMMENDATION");
    verify(workspace).staffWorkspace(application.getId());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void submittedCaseReportsDuplicateBlockerOnlyWhenChecksFail(boolean passed) {
    application.submit(REASON);
    when(duplicates.check(application))
        .thenReturn(
            new ApplicationDuplicateCheckService.DuplicateCheckResult(
                passed, "Duplicate national ID for intake."));
    var result = service.get(application.getId(), officer());
    if (passed) assertThat(result.blockers()).isEmpty();
    else assertThat(result.blockers()).containsExactly("Duplicate national ID for intake.");
  }

  @ParameterizedTest
  @EnumSource(AcademicRecommendationReviewStatus.class)
  void academicRecommendationActionFollowsRecordedReviewStatus(
      AcademicRecommendationReviewStatus status) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    AcademicReview review = review(choice);
    givenReview(review);
    AcademicRecommendation recommendation =
        new AcademicRecommendation(
            review, 1, RecommendationOutcome.RECOMMEND_ADMIT, REASON, actor, NOW);
    switch (status) {
      case APPROVED -> recommendation.approve(actor, REASON, NOW);
      case OVERRIDDEN -> recommendation.override(actor, REASON, NOW);
      case RETURNED -> recommendation.returnForReconsideration(actor, REASON, NOW);
      default -> {}
    }
    when(recommendations
            .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(
                review.getId()))
        .thenReturn(List.of(recommendation));
    var result = service.get(application.getId(), reviewer(academicUnit, "ACADEMIC_UNIT_STAFF"));
    assertThat(result.academicRecommendation().reviewStatus()).isEqualTo(status.name());
    assertThat(result.availableActions().contains("RECORD_ACADEMIC_RECOMMENDATION"))
        .isEqualTo(status == AcademicRecommendationReviewStatus.RETURNED);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void decisionActionsExistOnlyBeforeChoiceHasItsOwnDecision(boolean alreadyDecided) {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    AcademicReview review = review(choice);
    givenReview(review);
    when(recommendations
            .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(
                review.getId()))
        .thenReturn(
            List.of(
                new AcademicRecommendation(
                    review, 1, RecommendationOutcome.RECOMMEND_ADMIT, REASON, actor, NOW)));
    when(decisions.existsByProgrammeChoiceIdAndDeletedAtIsNull(choice.getId()))
        .thenReturn(alreadyDecided);
    var result = service.get(application.getId(), officer("ADMISSIONS_DECISION_MAKE"));
    assertThat(result.availableActions().contains("RECORD_ADMISSION_DECISION"))
        .isEqualTo(!alreadyDecided);
    assertThat(result.availableActions().contains("RETURN_ACADEMIC_RECOMMENDATION"))
        .isEqualTo(!alreadyDecided);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void eligibilityActionsDistinguishRecalculationFromReasonedResolution(boolean requiresReview) {
    choice(requiresReview ? EvaluationStatus.REQUIRES_REVIEW : EvaluationStatus.ELIGIBLE);
    var result = service.get(application.getId(), officer("ADMISSIONS_ELIGIBILITY_REVIEW"));
    assertThat(result.availableActions()).contains("RECALCULATE_ELIGIBILITY");
    assertThat(result.availableActions().contains("RESOLVE_ELIGIBILITY")).isEqualTo(requiresReview);
    assertThat(result.blockers().stream().anyMatch(value -> value.contains("reasoned eligibility")))
        .isEqualTo(requiresReview);
  }

  @ParameterizedTest
  @ValueSource(strings = {"noTerms", "termsOnly", "documentOnly", "ready"})
  void publishActionRequiresBothOfferTermsAndStoredDocument(String state) {
    AdmissionOffer offer = offer(choice(EvaluationStatus.ELIGIBLE));
    givenOffer(offer);
    if (state.equals("termsOnly") || state.equals("ready")) terms(offer);
    if (state.equals("documentOnly") || state.equals("ready"))
      offer.linkCurrentDocumentVersion(storedDocument(offer));
    var result = service.get(application.getId(), officer("ADMISSIONS_OFFER_MANAGE"));
    assertThat(result.availableActions()).contains("UPDATE_OFFER", "GENERATE_OFFER_DOCUMENT");
    assertThat(result.availableActions().contains("PUBLISH_AND_SEND"))
        .isEqualTo(state.equals("ready"));
  }

  @ParameterizedTest
  @EnumSource(OfferEmailDeliveryStatus.class)
  void emailRetryAvailableOnlyForFailedOrBouncedPublishedOffers(OfferEmailDeliveryStatus status) {
    AdmissionOffer offer = offer(choice(EvaluationStatus.ELIGIBLE));
    givenOffer(offer);
    OfferPublication publication = publish(offer);
    publication.recordEmailStatus(
        status,
        "provider-1",
        status == OfferEmailDeliveryStatus.FAILED || status == OfferEmailDeliveryStatus.BOUNCED
            ? "Mailbox unavailable"
            : null,
        NOW.plusSeconds(1));
    var result = service.get(application.getId(), officer("ADMISSIONS_OFFER_MANAGE"));
    assertThat(result.availableActions().contains("RETRY_EMAIL"))
        .isEqualTo(
            status == OfferEmailDeliveryStatus.FAILED
                || status == OfferEmailDeliveryStatus.BOUNCED);
    assertThat(result.offer().status()).isEqualTo("SENT");
  }

  @ParameterizedTest
  @EnumSource(OfferResponseType.class)
  void answeredOffersCannotBeEditedOrRepublished(OfferResponseType response) {
    AdmissionOffer offer = offer(choice(EvaluationStatus.ELIGIBLE));
    givenOffer(offer);
    publish(offer);
    offer.respond(response);
    var result = service.get(application.getId(), officer("ADMISSIONS_OFFER_MANAGE"));
    assertThat(result.availableActions())
        .doesNotContain(
            "UPDATE_OFFER", "GENERATE_OFFER_DOCUMENT", "PUBLISH_AND_SEND", "RETRY_EMAIL");
  }

  @Test
  void amendedPublishedOfferExplainsWhyResponseIsBlocked() {
    AdmissionOffer offer = offer(choice(EvaluationStatus.ELIGIBLE));
    givenOffer(offer);
    publish(offer);
    terms(offer);
    var result = service.get(application.getId(), officer("ADMISSIONS_OFFER_MANAGE"));
    assertThat(result.blockers())
        .contains("The replacement offer letter must be published before response.");
    assertThat(result.offer().amendmentPending()).isTrue();
  }

  @Test
  void fullCaseProjectsDecisionDocumentPublicationResponseConditionAndAuditHistory() {
    ApplicationProgrammeChoice choice = choice(EvaluationStatus.ELIGIBLE);
    AcademicReview review = review(choice);
    givenReview(review);
    AcademicRecommendation recommendation =
        identified(
            new AcademicRecommendation(
                review, 1, RecommendationOutcome.RECOMMEND_ADMIT, REASON, actor, NOW));
    when(recommendations
            .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(
                review.getId()))
        .thenReturn(List.of(recommendation));
    ProgrammeChoiceDecision decision =
        identified(
            new ProgrammeChoiceDecision(
                application, choice, DecisionOutcome.ADMIT, REASON, recommendation, actor, NOW));
    when(decisions.findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(
            application.getId()))
        .thenReturn(List.of(decision));
    AdmissionOffer offer = identified(new AdmissionOffer(application, choice, decision, "OFFER-1"));
    givenOffer(offer);
    OfferPublication publication = publish(offer);
    publication.recordEmailStatus(
        OfferEmailDeliveryStatus.FAILED, null, "Mailbox unavailable", NOW.plusSeconds(1));
    OfferDocumentVersion failed =
        identified(new OfferDocumentVersion(offer, 2, actor, NOW.plusSeconds(1)));
    failed.fail("Generation failed");
    when(documents.findAllByOfferIdAndDeletedAtIsNullOrderByDocumentVersionDesc(offer.getId()))
        .thenReturn(List.of(failed, publication.getDocumentVersion()));
    when(publications.findAllByOfferIdAndDeletedAtIsNullOrderByPublicationSequenceDesc(
            offer.getId()))
        .thenReturn(List.of(publication));
    OfferCondition condition =
        identified(new OfferCondition(offer, "TRANSCRIPT", "Provide final transcript", true));
    condition.satisfy(actor, "Original checked", NOW);
    when(conditions.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offer.getId()))
        .thenReturn(List.of(condition));
    when(responses.findByOfferId(offer.getId()))
        .thenReturn(
            Optional.of(
                new OfferResponse(
                    offer,
                    publication,
                    OfferResponseType.ACCEPTED,
                    NOW,
                    actor,
                    "Accepted online")));
    when(events.findAllByApplicationIdOrderByChangedAtDesc(application.getId()))
        .thenReturn(
            List.of(
                new ApplicationStatusEvent(
                    application,
                    ApplicationStatus.ADMITTED,
                    ApplicationStatus.OFFERED,
                    REASON,
                    actor),
                new ApplicationStatusEvent(
                    application, null, ApplicationStatus.DRAFT, "Created", actor)));
    var result = service.get(application.getId(), officer());
    assertThat(result.admissionDecision().decision()).isEqualTo("ADMIT");
    assertThat(result.admissionDecision().decidedByUserId()).isEqualTo(actor);
    assertThat(result.documentVersions())
        .extracting(AdmissionsWorkItemViews.OfferDocumentVersionView::status)
        .containsExactly("FAILED", "STORED");
    assertThat(result.documentVersions().get(0).failureReason()).isEqualTo("Generation failed");
    assertThat(result.publications().get(0).emailFailureReason()).isEqualTo("Mailbox unavailable");
    assertThat(result.publications().get(0).documentVersionId())
        .isEqualTo(publication.getDocumentVersion().getId());
    assertThat(result.offer().conditions().get(0).status()).isEqualTo("SATISFIED");
    assertThat(result.offer().response().response()).isEqualTo("ACCEPTED");
    assertThat(result.auditHistory())
        .extracting(AdmissionsWorkItemViews.AuditEventView::fromStatus)
        .containsExactly("ADMITTED", null);
  }

  @ParameterizedTest
  @CsvSource({"-5,0,0,1", "2,500,2,100", "1,25,1,25"})
  void listClampsPaginationAndRequestsMostRecentlyUpdatedFirst(
      int page, int size, int expectedPage, int expectedSize) {
    when(applications.findAll(any(Specification.class), any(Pageable.class)))
        .thenAnswer(
            invocation -> new PageImpl<Application>(List.of(), invocation.getArgument(1), 0));
    var result = service.list(null, null, null, null, null, null, null, page, size, officer());
    assertThat(result.page()).isEqualTo(expectedPage);
    assertThat(result.size()).isEqualTo(expectedSize);
    ArgumentCaptor<Pageable> capture = ArgumentCaptor.forClass(Pageable.class);
    verify(applications).findAll(any(Specification.class), capture.capture());
    assertThat(capture.getValue().getSort().getOrderFor("updatedAt").isDescending()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NOT_REQUIRED", "PENDING", "PAID", "WAIVED"})
  void listShowsActualPaymentClearanceAndApplicationIdentity(String state) {
    if (!state.equals("NOT_REQUIRED")) {
      type.associateFeeStructure(UUID.randomUUID(), "APP", "Application fee");
      application = application(true);
      when(choices.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()))
          .thenReturn(List.of());
      if (state.equals("PAID")) application.confirmPayment(NOW);
      if (state.equals("WAIVED")) application.overridePayment(actor, REASON);
    }
    when(progress.progress(application.getId()))
        .thenReturn(new AdmissionsApplicationWorkflowProgress("VERIFICATION", List.of()));
    when(applications.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(application)));
    var row =
        service.list(null, null, null, null, null, null, null, 0, 10, officer()).content().get(0);
    assertThat(row.applicantName()).isEqualTo("Tariro Moyo");
    assertThat(row.applicationNumber()).isEqualTo("APP-1");
    assertThat(row.paymentState()).isEqualTo(state);
    assertThat(row.stage()).isEqualTo("VERIFICATION");
    assertThat(row.programmeId()).isNull();
    assertThat(row.blockers().stream().anyMatch(value -> value.contains("fee")))
        .isEqualTo(state.equals("PENDING"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ELIGIBLE", "ADMITTED", "OFFERED", "CONVERTED", "REJECTED"})
  void listSelectsActiveProgrammeAndFallsBackToRejectedHistory(String status) {
    ApplicationProgrammeChoice first = choice(EvaluationStatus.ELIGIBLE);
    first.enterAcademicReview();
    first.recordDecision(DecisionOutcome.REJECT, REASON);
    ApplicationProgrammeChoice selected = choice(EvaluationStatus.ELIGIBLE);
    if (!status.equals("ELIGIBLE")) {
      selected.enterAcademicReview();
      selected.recordDecision(
          status.equals("REJECTED") ? DecisionOutcome.REJECT : DecisionOutcome.ADMIT, REASON);
      if (status.equals("OFFERED") || status.equals("CONVERTED")) selected.markOffered(REASON);
      if (status.equals("CONVERTED")) {
        selected.recordOfferResponse(OfferResponseType.ACCEPTED, REASON);
        selected.markConverted(REASON);
      }
    }
    when(progress.progress(application.getId()))
        .thenReturn(new AdmissionsApplicationWorkflowProgress("OFFER", List.of()));
    when(applications.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(application)));
    var row =
        service.list(null, null, null, null, null, null, null, 0, 10, officer()).content().get(0);
    assertThat(row.programmeId())
        .isEqualTo(status.equals("REJECTED") ? first.getProgrammeId() : selected.getProgrammeId());
  }

  @Test
  void listUsesOfferOutcomeWhenOfferExists() {
    AdmissionOffer offer = offer(choice(EvaluationStatus.ELIGIBLE));
    givenOffer(offer);
    publish(offer);
    when(progress.progress(application.getId()))
        .thenReturn(new AdmissionsApplicationWorkflowProgress("RESPONSE", List.of()));
    when(applications.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(application)));
    assertThat(
            service
                .list(null, null, null, null, null, null, null, 0, 10, officer())
                .content()
                .get(0)
                .outcome())
        .isEqualTo("SENT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "PAID", "WAIVED", "NOT_REQUIRED"})
  void queueAndCasePreserveSnapshottedFeePolicyAfterRoutePolicyChanges(String paymentState) {
    ApplicationFeePolicySnapshot pricing =
        paymentState.equals("NOT_REQUIRED")
            ? ApplicationFeePolicySnapshot.feeFree(REASON, actor, NOW)
            : ApplicationFeePolicySnapshot.financeStructure(
                UUID.randomUUID(),
                "APP",
                "Application fee",
                1,
                UUID.randomUUID(),
                "UNDERGRAD",
                "LOCAL",
                new BigDecimal("20.00"),
                "USD",
                NOW);
    application =
        identified(
            new Application(
                UUID.randomUUID(),
                "AUG26",
                "August intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                3,
                application.getApplicant(),
                type,
                "APP-1",
                pricing));
    if (paymentState.equals("NOT_REQUIRED"))
      type.associateFeeStructure(UUID.randomUUID(), "NEW-FEE", "New fee");
    else type.recordFeeFreeDecision(actor, REASON, NOW.plusSeconds(1));
    if (paymentState.equals("PAID")) application.confirmPayment(NOW);
    if (paymentState.equals("WAIVED")) application.overridePayment(actor, REASON);
    when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    when(progress.progress(application.getId()))
        .thenReturn(new AdmissionsApplicationWorkflowProgress("VERIFICATION", List.of()));
    when(applications.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(application)));
    var row =
        service.list(null, null, null, null, null, null, null, 0, 10, officer()).content().get(0);
    var detail = service.get(application.getId(), officer());
    assertThat(row.paymentState()).isEqualTo(paymentState);
    assertThat(row.blockers().stream().anyMatch(value -> value.contains("fee")))
        .isEqualTo(paymentState.equals("PENDING"));
    assertThat(detail.blockers().stream().anyMatch(value -> value.contains("fee")))
        .isEqualTo(paymentState.equals("PENDING"));
  }

  private Application application(boolean fee) {
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
            fee));
  }

  private ApplicationProgrammeChoice choice(EvaluationStatus status) {
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
                programmeChoices.size() + 1));
    choice.recordEvaluation(status, REASON);
    programmeChoices.add(choice);
    return choice;
  }

  private AcademicReview review(ApplicationProgrammeChoice choice) {
    return identified(
        new AcademicReview(
            application,
            choice,
            choice.getOwningAcademicUnitId(),
            "CS",
            "Computing",
            academicUnit,
            "SCI",
            "Science",
            "[]"));
  }

  private void givenReview(AcademicReview review) {
    when(reviews.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(review));
  }

  private AdmissionOffer offer(ApplicationProgrammeChoice choice) {
    return identified(
        new AdmissionOffer(
            application,
            choice,
            new ProgrammeChoiceDecision(
                application, choice, DecisionOutcome.ADMIT, REASON, null, actor, NOW),
            "OFFER-1"));
  }

  private void givenOffer(AdmissionOffer offer) {
    when(offers.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
        .thenReturn(List.of(offer));
  }

  private void terms(AdmissionOffer offer) {
    offer.updateTerms(
        OfferType.FIRM, null, NOW.plusSeconds(86400), null, null, LocalDate.of(2026, 9, 1), NOW);
  }

  private OfferDocumentVersion storedDocument(AdmissionOffer offer) {
    OfferDocumentVersion document = identified(new OfferDocumentVersion(offer, 1, actor, NOW));
    document.store(UUID.randomUUID(), "DOC-1", "documents", "offer.pdf", "checksum", NOW);
    return document;
  }

  private OfferPublication publish(AdmissionOffer offer) {
    terms(offer);
    OfferDocumentVersion document = storedDocument(offer);
    offer.linkCurrentDocumentVersion(document);
    OfferPublication publication =
        identified(new OfferPublication(offer, document, 1, actor, UUID.randomUUID(), NOW));
    offer.publish(publication, actor, NOW);
    return publication;
  }

  private CoreCurrentUserProfile officer(String... additional) {
    List<String> permissions = new ArrayList<>(List.of("ADMISSIONS_APPLICATION_REVIEW"));
    permissions.addAll(List.of(additional));
    return profile(List.of(), permissions);
  }

  private CoreCurrentUserProfile reviewer(UUID unit, String role) {
    return profile(
        List.of(
            new CoreRoleAssignmentSummary(
                UUID.randomUUID(), UUID.randomUUID(), role, "Academic staff", unit)),
        List.of());
  }

  private CoreCurrentUserProfile profile(
      List<CoreRoleAssignmentSummary> roles, List<String> permissions) {
    return new CoreCurrentUserProfile(
        new CoreUserSummary(
            actor, UUID.randomUUID(), "staff", "staff@example.test", "Staff", "ACTIVE"),
        roles,
        List.of(),
        permissions,
        true);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

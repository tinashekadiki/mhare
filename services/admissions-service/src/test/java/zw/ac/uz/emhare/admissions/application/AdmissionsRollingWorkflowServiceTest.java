package zw.ac.uz.emhare.admissions.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendation;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendationReviewStatus;
import zw.ac.uz.emhare.admissions.domain.model.AcademicReview;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearance;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearanceOutcome;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationSection;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.QualificationResultStatus;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceDecision;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceStatus;
import zw.ac.uz.emhare.admissions.domain.model.RecommendationOutcome;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicRecommendationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicReviewRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionRequirementSetRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationSittingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationClearanceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationEvaluationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ProgrammeChoiceDecisionRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;

/** Regression coverage for the ADR-0014 rolling workflow. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsRollingWorkflowServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationProgrammeChoiceRepository choiceRepository;
    @Mock private AdmissionRequirementSetRepository requirementSetRepository;
    @Mock private ApplicationEvaluationRepository evaluationRepository;
    @Mock private AcademicReviewRepository reviewRepository;
    @Mock private AcademicRecommendationRepository recommendationRepository;
    @Mock private ProgrammeChoiceDecisionRepository decisionRepository;
    @Mock private AdmissionOfferRepository offerRepository;
    @Mock private ApplicationStatusEventRepository applicationStatusEventRepository;
    @Mock private OfferStatusEventRepository offerStatusEventRepository;
    @Mock private QualificationEligibilityService eligibilityService;
    @Mock private AcademicSetupCatalogueClient academicSetupCatalogueClient;
    @Mock private AdmissionsIdentifierGenerator identifierGenerator;
    @Mock private AdmissionsDocumentService documentService;
    @Mock private ApplicationSectionRepository sectionRepository;
    @Mock private ApplicantQualificationSittingRepository qualificationRepository;
    @Mock private ApplicationClearanceRepository clearanceRepository;
    @Mock private AdmissionsIntegrationOutboxService outboxService;
    @Mock private ApplicationDuplicateCheckService duplicateCheckService;

    private AdmissionsRollingWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsRollingWorkflowService(
                applicationRepository,
                choiceRepository,
                requirementSetRepository,
                evaluationRepository,
                reviewRepository,
                recommendationRepository,
                decisionRepository,
                offerRepository,
                applicationStatusEventRepository,
                offerStatusEventRepository,
                eligibilityService,
                academicSetupCatalogueClient,
                identifierGenerator,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC),
                documentService,
                sectionRepository,
                qualificationRepository,
                clearanceRepository,
                outboxService,
                duplicateCheckService);
    }

    @Test
    void decide_shouldOpenAndPersistNextEligibleChoice_whenHigherPreferenceIsRejected() {
        UUID applicationId = UUID.randomUUID();
        UUID rejectedChoiceId = UUID.randomUUID();
        UUID nextChoiceId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Application application = mock(Application.class);
        ApplicationProgrammeChoice rejectedChoice = mock(ApplicationProgrammeChoice.class);
        ApplicationProgrammeChoice nextChoice = mock(ApplicationProgrammeChoice.class);
        AcademicReview rejectedReview = mock(AcademicReview.class);
        AcademicReview nextReview = mock(AcademicReview.class);
        AcademicRecommendation recommendation = mock(AcademicRecommendation.class);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(application.getId()).thenReturn(applicationId);
        when(application.getStatus()).thenReturn(ApplicationStatus.UNDER_ACADEMIC_REVIEW);
        when(choiceRepository.findById(rejectedChoiceId)).thenReturn(Optional.of(rejectedChoice));
        when(rejectedChoice.getApplication()).thenReturn(application);
        when(rejectedChoice.getChoiceRank()).thenReturn(1);
        when(nextChoice.getApplication()).thenReturn(application);
        when(nextChoice.getId()).thenReturn(nextChoiceId);
        when(nextChoice.getChoiceRank()).thenReturn(2);
        when(nextChoice.getChoiceStatus()).thenReturn(ProgrammeChoiceStatus.ELIGIBLE);
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(rejectedChoice, nextChoice));
        when(reviewRepository.findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(
                applicationId, rejectedChoiceId)).thenReturn(Optional.of(rejectedReview));
        when(reviewRepository.findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(
                applicationId, nextChoiceId)).thenReturn(Optional.of(nextReview));
        when(rejectedReview.getId()).thenReturn(UUID.randomUUID());
        when(recommendationRepository.findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(
                rejectedReview.getId(), AcademicRecommendationReviewStatus.PENDING))
                .thenReturn(Optional.of(recommendation));
        when(recommendation.getRecommendation()).thenReturn(RecommendationOutcome.RECOMMEND_REJECT);
        when(decisionRepository.saveAndFlush(any(ProgrammeChoiceDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.decide(
                applicationId,
                rejectedChoiceId,
                "REJECT",
                "The applicant does not meet the programme-specific expectations.",
                actorUserId);

        verify(nextChoice).enterAcademicReview();
        verify(choiceRepository).saveAndFlush(nextChoice);
    }

    @Test
    void returnRecommendation_shouldReopenReviewForARevisedRecommendation() {
        UUID applicationId = UUID.randomUUID();
        UUID choiceId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Application application = mock(Application.class);
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class);
        AcademicReview review = mock(AcademicReview.class);
        AcademicRecommendation recommendation = mock(AcademicRecommendation.class);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(application.getId()).thenReturn(applicationId);
        when(choiceRepository.findById(choiceId)).thenReturn(Optional.of(choice));
        when(choice.getApplication()).thenReturn(application);
        when(reviewRepository.findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(applicationId, choiceId))
                .thenReturn(Optional.of(review));
        when(review.getId()).thenReturn(UUID.randomUUID());
        when(recommendationRepository.findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(
                review.getId(), AcademicRecommendationReviewStatus.PENDING))
                .thenReturn(Optional.of(recommendation));

        service.returnRecommendation(
                applicationId,
                choiceId,
                "The recommendation needs clearer evidence from the submitted qualifications.",
                actorUserId);

        verify(recommendation).returnForReconsideration(
                actorUserId,
                "The recommendation needs clearer evidence from the submitted qualifications.",
                Instant.parse("2026-08-12T08:00:00Z"));
        verify(review).returnForReconsideration();
        verify(recommendationRepository).save(recommendation);
        verify(reviewRepository).saveAndFlush(review);
    }

    @Test
    void advance_shouldAutomaticallyClearAReadySubmittedApplicationWithDuplicateEvidence() {
        UUID applicationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Application application = mock(Application.class);
        ApplicationSection requiredSection = mock(ApplicationSection.class);
        ApplicantQualificationSitting qualification = mock(ApplicantQualificationSitting.class);
        var duplicateResult = new ApplicationDuplicateCheckService.DuplicateCheckResult(true, "Identity and application checks passed.");

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(application.getId()).thenReturn(applicationId);
        when(application.getStatus()).thenReturn(ApplicationStatus.SUBMITTED);
        when(application.canEnterReview()).thenReturn(true);
        when(duplicateCheckService.check(application)).thenReturn(duplicateResult);
        when(documentService.isReadyForReview(application)).thenReturn(true);
        when(sectionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(applicationId))
                .thenReturn(List.of(requiredSection));
        when(requiredSection.isRequired()).thenReturn(true);
        when(requiredSection.isComplete()).thenReturn(true);
        when(qualificationRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(applicationId))
                .thenReturn(List.of(qualification));
        when(qualification.getVerificationStatus()).thenReturn(QualificationResultStatus.VERIFIED);
        when(clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED)).thenReturn(
                        Optional.empty(), Optional.of(mock(ApplicationClearance.class)));

        service.advance(applicationId, actorUserId);
        service.advance(applicationId, actorUserId);

        verify(application, org.mockito.Mockito.times(2)).moveToUnderReview(
                actorUserId, "Application entered rolling admissions processing.");
        verify(clearanceRepository).save(any(ApplicationClearance.class));
        verify(outboxService, org.mockito.Mockito.times(2)).enqueueVerificationDecisionNotification(application);
    }

    @Test
    void advance_shouldKeepSubmittedApplicationBlocked_whenDuplicateChecksFail() {
        UUID applicationId = UUID.randomUUID();
        Application application = mock(Application.class);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(application.getStatus()).thenReturn(ApplicationStatus.SUBMITTED);
        when(duplicateCheckService.check(application)).thenReturn(
                new ApplicationDuplicateCheckService.DuplicateCheckResult(false, "Duplicate application found."));

        service.advance(applicationId, UUID.randomUUID());

        verify(application, org.mockito.Mockito.never()).moveToUnderReview(any(), any());
    }

    @Test
    void advance_shouldIgnoreApplicationsOutsideAutomaticRollingStages() {
        UUID applicationId = UUID.randomUUID();
        Application application = mock(Application.class);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(application.getStatus()).thenReturn(ApplicationStatus.OFFERED);

        service.advance(applicationId, UUID.randomUUID());

        verify(duplicateCheckService, org.mockito.Mockito.never()).check(application);
    }
}

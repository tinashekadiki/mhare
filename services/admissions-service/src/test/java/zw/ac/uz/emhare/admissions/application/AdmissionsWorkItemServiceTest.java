package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendation;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendationReviewStatus;
import zw.ac.uz.emhare.admissions.domain.model.AcademicReview;
import zw.ac.uz.emhare.admissions.domain.model.AcademicReviewStatus;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceStatus;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicRecommendationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicReviewRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferConditionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDocumentVersionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferPublicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferResponseRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ProgrammeChoiceDecisionRepository;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreRoleAssignmentSummary;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreUserSummary;

/** Action-level usability regressions for the unified Admissions case. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsWorkItemServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationProgrammeChoiceRepository choiceRepository;
    @Mock private AcademicReviewRepository reviewRepository;
    @Mock private AcademicRecommendationRepository recommendationRepository;
    @Mock private ProgrammeChoiceDecisionRepository decisionRepository;
    @Mock private AdmissionOfferRepository offerRepository;
    @Mock private OfferConditionRepository conditionRepository;
    @Mock private OfferResponseRepository responseRepository;
    @Mock private OfferDocumentVersionRepository documentVersionRepository;
    @Mock private OfferPublicationRepository publicationRepository;
    @Mock private ApplicationStatusEventRepository statusEventRepository;
    @Mock private ApplicantApplicationWorkspaceService workspaceService;
    @Mock private AdmissionsApplicationWorkflowProgressService progressService;
    @Mock private ApplicationDuplicateCheckService duplicateCheckService;

    private AdmissionsWorkItemService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsWorkItemService(
                applicationRepository,
                choiceRepository,
                reviewRepository,
                recommendationRepository,
                decisionRepository,
                offerRepository,
                conditionRepository,
                responseRepository,
                documentVersionRepository,
                publicationRepository,
                statusEventRepository,
                workspaceService,
                progressService,
                duplicateCheckService);
    }

    @Test
    void availableActions_shouldAllowDecisionAndReturnForCurrentChoice_whenEarlierChoiceWasRejected() {
        UUID currentChoiceId = UUID.randomUUID();
        Application application = mock(Application.class);
        ApplicationProgrammeChoice currentChoice = mock(ApplicationProgrammeChoice.class);
        AcademicReview review = mock(AcademicReview.class);
        AcademicRecommendation recommendation = mock(AcademicRecommendation.class);

        when(review.getProgrammeChoice()).thenReturn(currentChoice);
        when(currentChoice.getId()).thenReturn(currentChoiceId);
        when(recommendation.getReviewStatus()).thenReturn(AcademicRecommendationReviewStatus.PENDING);
        when(decisionRepository.existsByProgrammeChoiceIdAndDeletedAtIsNull(currentChoiceId)).thenReturn(false, true);

        List<String> actions = availableActions(application, review, recommendation, admissionsOfficerProfile());

        assertThat(actions).contains("RECORD_ADMISSION_DECISION", "RETURN_ACADEMIC_RECOMMENDATION");
        assertThat(availableActions(application, review, recommendation, admissionsOfficerProfile()))
                .doesNotContain("RECORD_ADMISSION_DECISION", "RETURN_ACADEMIC_RECOMMENDATION");
    }

    @Test
    void availableActions_shouldOfferOneRevisedRecommendationAction_whenAdmissionsReturnsTheReview() {
        UUID academicUnitId = UUID.randomUUID();
        Application application = mock(Application.class);
        AcademicReview review = mock(AcademicReview.class);
        AcademicRecommendation returnedRecommendation = mock(AcademicRecommendation.class);

        when(review.getRecommendationAcademicUnitId()).thenReturn(academicUnitId);
        when(returnedRecommendation.getReviewStatus()).thenReturn(AcademicRecommendationReviewStatus.RETURNED);

        List<String> actions = availableActions(
                application,
                review,
                returnedRecommendation,
                academicReviewerProfile(academicUnitId));

        assertThat(actions).containsExactly("RECORD_ACADEMIC_RECOMMENDATION");
    }

    @Test
    void caseBlockers_shouldSurfaceFailedDuplicateEvidenceForSubmittedApplication() {
        Application application = mock(Application.class);
        ApplicationType applicationType = mock(ApplicationType.class);
        when(application.getStatus()).thenReturn(ApplicationStatus.SUBMITTED);
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getApplicationType()).thenReturn(applicationType);
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId())).thenReturn(List.of());
        when(duplicateCheckService.check(application)).thenReturn(
                new ApplicationDuplicateCheckService.DuplicateCheckResult(false, "Duplicate intake application found."));

        assertThat(caseBlockers(application)).containsExactly("Duplicate intake application found.");
    }

    @Test
    void caseBlockers_shouldStayClearWhenSubmittedDuplicateChecksPass() {
        Application application = mock(Application.class);
        ApplicationType applicationType = mock(ApplicationType.class);
        when(application.getStatus()).thenReturn(ApplicationStatus.SUBMITTED);
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getApplicationType()).thenReturn(applicationType);
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId())).thenReturn(List.of());
        when(duplicateCheckService.check(application)).thenReturn(
                new ApplicationDuplicateCheckService.DuplicateCheckResult(true, "Checks passed."));

        assertThat(caseBlockers(application)).isEmpty();
    }

    @Test
    void caseBlockers_shouldSkipDuplicateChecksOutsideSubmittedStatus() {
        Application application = mock(Application.class);
        ApplicationType applicationType = mock(ApplicationType.class);
        when(application.getStatus()).thenReturn(ApplicationStatus.UNDER_REVIEW);
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getApplicationType()).thenReturn(applicationType);
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId())).thenReturn(List.of());

        assertThat(caseBlockers(application)).isEmpty();
    }

    @Test
    void availableActions_shouldCoverNoAccessEligibilityAndUndecidedRecommendationPaths() {
        Application application = mock(Application.class);
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class);
        AcademicReview review = mock(AcademicReview.class);
        UUID applicationId = UUID.randomUUID();
        UUID academicUnitId = UUID.randomUUID();
        when(application.getId()).thenReturn(applicationId);
        when(choice.getChoiceStatus()).thenReturn(ProgrammeChoiceStatus.REQUIRES_REVIEW);
        when(review.getRecommendationAcademicUnitId()).thenReturn(academicUnitId);
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId)).thenReturn(List.of(choice));

        assertThat(availableActions(application, null, null, noAccessProfile())).isEmpty();
        assertThat(availableActions(application, review, null, academicReviewerProfile(academicUnitId)))
                .containsExactly("RECORD_ACADEMIC_RECOMMENDATION");
        assertThat(availableActions(application, null, null, eligibilityProfile()))
                .containsExactly("RECALCULATE_ELIGIBILITY", "RESOLVE_ELIGIBILITY");
    }

    @Test
    void availableActions_shouldNotReopenPendingAcademicRecommendationOrDecideWithoutRecommendation() {
        UUID academicUnitId = UUID.randomUUID();
        Application application = mock(Application.class);
        AcademicReview review = mock(AcademicReview.class);
        AcademicRecommendation pendingRecommendation = mock(AcademicRecommendation.class);
        when(review.getRecommendationAcademicUnitId()).thenReturn(academicUnitId);
        when(pendingRecommendation.getReviewStatus()).thenReturn(AcademicRecommendationReviewStatus.PENDING);

        assertThat(availableActions(application, review, pendingRecommendation, academicReviewerProfile(academicUnitId)))
                .doesNotContain("RECORD_ACADEMIC_RECOMMENDATION");
        assertThat(availableActions(application, review, null, admissionsOfficerProfile()))
                .doesNotContain("RECORD_ADMISSION_DECISION");
    }

    @SuppressWarnings("unchecked")
    private List<String> availableActions(
            Application application,
            AcademicReview review,
            AcademicRecommendation recommendation,
            CoreCurrentUserProfile profile) {
        return (List<String>) ReflectionTestUtils.invokeMethod(
                service,
                "availableActions",
                application,
                review,
                recommendation,
                null,
                profile);
    }

    @SuppressWarnings("unchecked")
    private List<String> caseBlockers(Application application) {
        return (List<String>) ReflectionTestUtils.invokeMethod(service, "caseBlockers", application, null);
    }

    private CoreCurrentUserProfile admissionsOfficerProfile() {
        return new CoreCurrentUserProfile(
                activeUser(),
                List.of(),
                List.of(),
                List.of("ADMISSIONS_DECISION_MAKE"),
                true);
    }

    private CoreCurrentUserProfile academicReviewerProfile(UUID academicUnitId) {
        return new CoreCurrentUserProfile(
                activeUser(),
                List.of(new CoreRoleAssignmentSummary(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "ACADEMIC_UNIT_STAFF",
                        "Academic Unit Staff",
                        academicUnitId)),
                List.of(),
                List.of("ADMISSIONS_ACADEMIC_UNIT_RECOMMEND"),
                true);
    }

    private CoreCurrentUserProfile noAccessProfile() {
        return new CoreCurrentUserProfile(activeUser(), List.of(), List.of(), List.of(), true);
    }

    private CoreCurrentUserProfile eligibilityProfile() {
        return new CoreCurrentUserProfile(
                activeUser(), List.of(), List.of(), List.of("ADMISSIONS_ELIGIBILITY_REVIEW"), true);
    }

    private CoreUserSummary activeUser() {
        return new CoreUserSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "staff@example.test",
                "staff@example.test",
                "Admissions Staff",
                "ACTIVE");
    }
}

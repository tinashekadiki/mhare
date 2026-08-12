package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;

/** Verifies the six-stage rolling applicant workflow projection. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsApplicationWorkflowProgressServiceTest {
    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationClearanceRepository clearanceRepository;
    @Mock ApplicationProgrammeChoiceRepository choiceRepository;
    @Mock AcademicReviewRepository reviewRepository;
    @Mock AcademicRecommendationRepository recommendationRepository;
    @Mock ProgrammeChoiceDecisionRepository decisionRepository;
    @Mock AdmissionOfferRepository offerRepository;
    @Mock OfferResponseRepository responseRepository;
    private AdmissionsApplicationWorkflowProgressService service;

    @BeforeEach void setUp(){service=new AdmissionsApplicationWorkflowProgressService(applicationRepository,
            clearanceRepository,choiceRepository,reviewRepository,recommendationRepository,decisionRepository,
            offerRepository,responseRepository);}

    @Test
    void admittedApplicantWithoutOfferIsShownAtDirectOfferStage(){
        UUID applicationId=UUID.randomUUID();Instant confirmedAt=Instant.parse("2027-04-02T08:00:00Z");
        Application application=mock(Application.class);ApplicationClearance clearance=mock(ApplicationClearance.class);
        ApplicationProgrammeChoice choice=mock(ApplicationProgrammeChoice.class);AcademicReview review=mock(AcademicReview.class);
        AcademicRecommendation recommendation=mock(AcademicRecommendation.class);ProgrammeChoiceDecision decision=mock(ProgrammeChoiceDecision.class);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(applicationId,ApplicationClearanceOutcome.CONFIRMED)).thenReturn(Optional.of(clearance));
        when(clearance.getConfirmedAt()).thenReturn(confirmedAt);when(application.getStatus()).thenReturn(ApplicationStatus.ADMITTED);
        when(choice.getChoiceStatus()).thenReturn(ProgrammeChoiceStatus.ADMITTED);when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId)).thenReturn(List.of(choice));
        when(reviewRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)).thenReturn(List.of(review));
        when(review.getId()).thenReturn(UUID.randomUUID());
        when(recommendationRepository.findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(review.getId())).thenReturn(List.of(recommendation));
        when(recommendation.getRecommendation()).thenReturn(RecommendationOutcome.RECOMMEND_ADMIT);when(recommendation.getReason()).thenReturn("Meets academic requirements.");
        when(decisionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(applicationId)).thenReturn(List.of(decision));
        when(decision.getDecision()).thenReturn(DecisionOutcome.ADMIT);when(decision.getReason()).thenReturn("Admissions approved direct admission.");
        when(offerRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)).thenReturn(List.of());

        AdmissionsApplicationWorkflowProgress progress=service.progress(applicationId);

        assertThat(progress.currentStageCode()).isEqualTo("OFFER");
        assertThat(progress.stages()).extracting(AdmissionsApplicationWorkflowProgress.WorkflowStage::code)
                .containsExactly("VERIFICATION","ELIGIBILITY","ACADEMIC_REVIEW","ADMISSION_DECISION","OFFER","RESPONSE");
        assertThat(progress.stages()).extracting(AdmissionsApplicationWorkflowProgress.WorkflowStage::state)
                .containsExactly("COMPLETED","COMPLETED","COMPLETED","COMPLETED","CURRENT","PENDING");
    }
}

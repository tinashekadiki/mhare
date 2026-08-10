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

/** Verifies the five-stage applicant workflow projection. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsApplicationWorkflowProgressServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationClearanceRepository clearanceRepository;
    @Mock AcademicReviewAssignmentRepository assignmentRepository;
    @Mock AcademicUnitRecommendationRepository recommendationRepository;
    @Mock SelectionDecisionRepository selectionDecisionRepository;
    @Mock AdmissionOfferRepository offerRepository;

    private AdmissionsApplicationWorkflowProgressService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsApplicationWorkflowProgressService(
                applicationRepository, clearanceRepository, assignmentRepository,
                recommendationRepository, selectionDecisionRepository, offerRepository);
    }

    @Test
    void selectedApplicantWithoutAnOfferIsShownAtTheOfferStage() {
        UUID applicationId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Instant confirmedAt = Instant.parse("2027-04-02T08:00:00Z");
        Instant releasedAt = Instant.parse("2027-04-03T08:00:00Z");
        Instant recommendedAt = Instant.parse("2027-04-04T08:00:00Z");
        Instant decidedAt = Instant.parse("2027-04-05T08:00:00Z");
        Application application = mock(Application.class);
        ApplicationClearance clearance = mock(ApplicationClearance.class);
        AcademicReviewAssignment assignment = mock(AcademicReviewAssignment.class);
        AcademicUnitRecommendation recommendation = mock(AcademicUnitRecommendation.class);
        SelectionDecision decision = mock(SelectionDecision.class);
        SelectionRound selectionRound = mock(SelectionRound.class);
        ApplicationProgrammeChoice programmeChoice = mock(ApplicationProgrammeChoice.class);
        UUID selectionRoundId = UUID.randomUUID();
        UUID programmeChoiceId = UUID.randomUUID();

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED)).thenReturn(Optional.of(clearance));
        when(clearance.getConfirmedAt()).thenReturn(confirmedAt);
        when(assignmentRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByReleasedAtDesc(applicationId))
                .thenReturn(List.of(assignment));
        when(assignment.getId()).thenReturn(assignmentId);
        when(assignment.getRecommendationAcademicUnitName()).thenReturn("Highest Academic Unit");
        when(assignment.getSelectionRound()).thenReturn(selectionRound);
        when(assignment.getProgrammeChoice()).thenReturn(programmeChoice);
        when(assignment.getReleasedAt()).thenReturn(releasedAt);
        when(selectionRound.getId()).thenReturn(selectionRoundId);
        when(programmeChoice.getId()).thenReturn(programmeChoiceId);
        when(programmeChoice.getProgrammeCode()).thenReturn("HSC");
        when(programmeChoice.getProgrammeName()).thenReturn("Bachelor of Science Computer Science");
        when(recommendationRepository
                .findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(assignmentId))
                .thenReturn(List.of(recommendation));
        when(recommendation.getRecommendation()).thenReturn(SelectionDecisionType.SELECT);
        when(recommendation.getRecommendedAt()).thenReturn(recommendedAt);
        when(selectionDecisionRepository.findBySelectionRoundIdAndProgrammeChoiceIdAndDeletedAtIsNull(
                selectionRoundId, programmeChoiceId)).thenReturn(Optional.of(decision));
        when(decision.getDecision()).thenReturn(SelectionDecisionType.SELECT);
        when(decision.getProgrammeChoice()).thenReturn(programmeChoice);
        when(decision.getDecidedAt()).thenReturn(decidedAt);
        when(offerRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId))
                .thenReturn(List.of());

        AdmissionsApplicationWorkflowProgress progress = service.progress(applicationId);

        assertThat(progress.currentStageCode()).isEqualTo("OFFER");
        assertThat(progress.stages()).extracting(AdmissionsApplicationWorkflowProgress.WorkflowStage::state)
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "CURRENT");
        assertThat(progress.stages().get(4).statusLabel()).isEqualTo("Ready for offer");
        assertThat(progress.stages().get(1).statusLabel()).isEqualTo("Released to Highest Academic Unit");
    }
}

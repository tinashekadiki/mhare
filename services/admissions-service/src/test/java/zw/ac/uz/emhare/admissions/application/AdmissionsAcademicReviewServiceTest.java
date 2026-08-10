package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicUnitHierarchyNode;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.ProgrammeHierarchyResolution;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.*;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsAcademicReviewServiceTest {
    @Mock SelectionRoundRepository roundRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationProgrammeChoiceRepository choiceRepository;
    @Mock ApplicationClearanceRepository clearanceRepository;
    @Mock AcademicReviewAssignmentRepository assignmentRepository;
    @Mock AcademicUnitRecommendationRepository recommendationRepository;
    @Mock AcademicSetupCatalogueClient academicSetupClient;
    @Mock AdmissionsSelectionOfferService selectionOfferService;
    @Mock AdmissionsIntegrationOutboxService outboxService;
    private AdmissionsAcademicReviewService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsAcademicReviewService(roundRepository, applicationRepository, choiceRepository,
                clearanceRepository, assignmentRepository, recommendationRepository, academicSetupClient,
                selectionOfferService, outboxService, new ObjectMapper(),
                Clock.fixed(Instant.parse("2028-01-10T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void descendantUnitStaffCannotClaimRootUnitAssignment() {
        UUID assignmentId = UUID.randomUUID();
        UUID rootUnitId = UUID.randomUUID();
        UUID lowerUnitId = UUID.randomUUID();
        AcademicReviewAssignment assignment = mock(AcademicReviewAssignment.class);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignment.getRecommendationAcademicUnitId()).thenReturn(rootUnitId);
        CoreCurrentUserProfile lowerUnitStaff = profile(lowerUnitId);

        assertThatThrownBy(() -> service.claim(assignmentId, 0, lowerUnitStaff))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("exact highest academic unit");
        verify(assignment, never()).claim(any(), any(), anyLong());
    }

    @Test
    void inactiveRootUnitStaffCannotClaimAnAssignment() {
        UUID assignmentId = UUID.randomUUID();
        UUID rootUnitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AcademicReviewAssignment assignment = mock(AcademicReviewAssignment.class);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignment.getRecommendationAcademicUnitId()).thenReturn(rootUnitId);
        CoreCurrentUserProfile inactiveStaff = new CoreCurrentUserProfile(
                new CoreUserSummary(userId, UUID.randomUUID(), "inactive-reviewer",
                        "inactive@example.test", "Inactive Reviewer", "INACTIVE"),
                List.of(new CoreRoleAssignmentSummary(
                        UUID.randomUUID(), UUID.randomUUID(), "ACADEMIC_UNIT_STAFF", "Academic Unit Staff", rootUnitId)));

        assertThatThrownBy(() -> service.claim(assignmentId, 0, inactiveStaff))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("exact highest academic unit");
        verify(assignment, never()).claim(any(), any(), anyLong());
    }

    @Test
    void workQueueIsScopedToTheStaffMembersExactAssignedRoot() {
        UUID rootUnitId = UUID.randomUUID();
        when(assignmentRepository
                .findAllByRecommendationAcademicUnitIdAndStatusInAndDeletedAtIsNullOrderByDueAtAscReleasedAtAsc(
                        eq(rootUnitId), anyList())).thenReturn(List.of());

        service.listMyAssignments(profile(rootUnitId));

        verify(assignmentRepository)
                .findAllByRecommendationAcademicUnitIdAndStatusInAndDeletedAtIsNullOrderByDueAtAscReleasedAtAsc(
                        eq(rootUnitId), anyList());
    }

    @Test
    void academicReviewerCanOpenOnlyAnApplicationAssignedToTheirExactRootUnit() {
        UUID assignmentId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID rootUnitId = UUID.randomUUID();
        AcademicReviewAssignment assignment = mock(AcademicReviewAssignment.class);
        Application application = mock(Application.class);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignment.getRecommendationAcademicUnitId()).thenReturn(rootUnitId);
        when(assignment.getApplication()).thenReturn(application);
        when(application.getId()).thenReturn(applicationId);

        assertThat(service.applicationIdForScopedAcademicReviewer(assignmentId, profile(rootUnitId)))
                .isEqualTo(applicationId);
        assertThatThrownBy(() -> service.applicationIdForScopedAcademicReviewer(
                assignmentId, profile(UUID.randomUUID())))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("exact highest academic unit");
    }

    @Test
    void ineligibleHigherChoiceOpensTheNextEligibleChoiceAndSnapshotsItsRoot() {
        UUID selectionRoundId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID rootUnitId = UUID.randomUUID();
        UUID leafUnitId = UUID.randomUUID();
        UUID firstProgrammeId = UUID.randomUUID();
        UUID secondProgrammeId = UUID.randomUUID();
        SelectionRound round = openRound(selectionRoundId, admissionCycleId);
        Application application = application(applicationId);
        ApplicationProgrammeChoice ineligibleFirstChoice = choice(
                application, 1, ProgrammeChoiceStatus.INELIGIBLE, firstProgrammeId);
        ApplicationProgrammeChoice eligibleSecondChoice = choice(application, 2, ProgrammeChoiceStatus.ELIGIBLE, secondProgrammeId);
        ProgrammeHierarchyResolution hierarchy = hierarchy(secondProgrammeId, rootUnitId, leafUnitId);

        when(roundRepository.findById(selectionRoundId)).thenReturn(Optional.of(round));
        when(applicationRepository.findByAdmissionCycleId(admissionCycleId)).thenReturn(List.of(application));
        when(clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED)).thenReturn(Optional.of(mock(ApplicationClearance.class)));
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(ineligibleFirstChoice, eligibleSecondChoice));
        when(academicSetupClient.getProgrammeHierarchy(firstProgrammeId))
                .thenReturn(hierarchy(firstProgrammeId, rootUnitId, leafUnitId));
        when(academicSetupClient.getProgrammeHierarchy(secondProgrammeId)).thenReturn(hierarchy);
        when(assignmentRepository.findBySelectionRoundIdAndProgrammeChoiceIdAndStatusInAndDeletedAtIsNull(
                eq(selectionRoundId), any(), anyList())).thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any(AcademicReviewAssignment.class))).thenAnswer(invocation -> {
            AcademicReviewAssignment saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(recommendationRepository.findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(any()))
                .thenReturn(List.of());

        var preview = service.previewReleaseBatches(selectionRoundId);
        assertThat(preview.totalApplicants()).isEqualTo(1);
        assertThat(preview.totalEligibleApplicants()).isEqualTo(1);
        assertThat(preview.programmes()).hasSize(2);
        assertThat(preview.programmes()).filteredOn(batch -> batch.programmeId().equals(secondProgrammeId))
                .singleElement().satisfies(batch -> {
            assertThat(batch.programmeId()).isEqualTo(secondProgrammeId);
            assertThat(batch.applicantCount()).isEqualTo(1);
            assertThat(batch.eligibleApplicantCount()).isEqualTo(1);
        });
        assertThat(preview.academicUnits()).extracting(batch -> batch.academicUnitId())
                .containsExactlyInAnyOrder(rootUnitId, leafUnitId);

        var released = service.releaseEligibleChoices(
                selectionRoundId, null, rootUnitId, null, null, UUID.randomUUID());

        assertThat(released).singleElement().satisfies(summary -> {
            assertThat(summary.choiceRank()).isEqualTo(2);
            assertThat(summary.owningAcademicUnitId()).isEqualTo(leafUnitId);
            assertThat(summary.recommendationAcademicUnitId()).isEqualTo(rootUnitId);
            assertThat(summary.hierarchyPathJson()).contains(rootUnitId.toString(), leafUnitId.toString());
        });

        // Hierarchy is copied into the assignment; subsequent structure changes cannot move this active review.
        var assignmentCaptor = org.mockito.ArgumentCaptor.forClass(AcademicReviewAssignment.class);
        verify(outboxService).enqueueAcademicReviewReleased(assignmentCaptor.capture());
        AcademicReviewAssignment storedAssignment = assignmentCaptor.getValue();
        assertThat(storedAssignment.getRecommendationAcademicUnitId()).isEqualTo(rootUnitId);
    }

    @Test
    void releasePreviewKeepsApplicantProgrammeAndAcademicUnitsVisibleBeforeConfirmation() {
        UUID selectionRoundId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID rootUnitId = UUID.randomUUID();
        UUID leafUnitId = UUID.randomUUID();
        SelectionRound round = openRound(selectionRoundId, admissionCycleId);
        Application application = application(applicationId, ApplicationStatus.SUBMITTED);
        ApplicationProgrammeChoice pendingChoice = choice(application, 1, ProgrammeChoiceStatus.PENDING, programmeId);

        when(roundRepository.findById(selectionRoundId)).thenReturn(Optional.of(round));
        when(applicationRepository.findByAdmissionCycleId(admissionCycleId)).thenReturn(List.of(application));
        when(clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED)).thenReturn(Optional.empty());
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(pendingChoice));
        when(academicSetupClient.getProgrammeHierarchy(programmeId))
                .thenReturn(hierarchy(programmeId, rootUnitId, leafUnitId));

        var preview = service.previewReleaseBatches(selectionRoundId);

        assertThat(preview.totalApplicants()).isEqualTo(1);
        assertThat(preview.totalEligibleApplicants()).isZero();
        assertThat(preview.programmes()).singleElement().satisfies(batch -> {
            assertThat(batch.programmeId()).isEqualTo(programmeId);
            assertThat(batch.applicantCount()).isEqualTo(1);
            assertThat(batch.eligibleApplicantCount()).isZero();
        });
        assertThat(preview.academicUnits()).hasSize(2).allSatisfy(batch -> {
            assertThat(batch.applicantCount()).isEqualTo(1);
            assertThat(batch.eligibleApplicantCount()).isZero();
        });
    }

    @Test
    void waitlistedHigherChoiceBlocksTheNextEligibleChoice() {
        UUID selectionRoundId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        SelectionRound round = openRound(selectionRoundId, admissionCycleId);
        Application application = application(applicationId);
        ApplicationProgrammeChoice waitlistedFirstChoice =
                choice(application, 1, ProgrammeChoiceStatus.WAITLISTED, UUID.randomUUID());
        ApplicationProgrammeChoice eligibleSecondChoice =
                choice(application, 2, ProgrammeChoiceStatus.ELIGIBLE, UUID.randomUUID());

        when(roundRepository.findById(selectionRoundId)).thenReturn(Optional.of(round));
        when(applicationRepository.findByAdmissionCycleId(admissionCycleId)).thenReturn(List.of(application));
        when(clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED)).thenReturn(Optional.of(mock(ApplicationClearance.class)));
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(waitlistedFirstChoice, eligibleSecondChoice));

        assertThatThrownBy(() -> service.releaseEligibleChoices(
                selectionRoundId, null, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unblocked programme choice");
        verifyNoInteractions(academicSetupClient, outboxService);
    }

    @Test
    void academicRecommendationIsAdvisoryUntilAdmissionsReviewsIt() {
        UUID assignmentId = UUID.randomUUID();
        UUID rootUnitId = UUID.randomUUID();
        AcademicReviewAssignment assignment = mock(AcademicReviewAssignment.class);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignment.getId()).thenReturn(assignmentId);
        when(assignment.getRecommendationAcademicUnitId()).thenReturn(rootUnitId);
        SelectionRound selectionRound = mock(SelectionRound.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationProgrammeChoice programmeChoice = mock(ApplicationProgrammeChoice.class);
        when(selectionRound.getId()).thenReturn(UUID.randomUUID());
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getApplicationNumber()).thenReturn("APP-2028-0001");
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getApplicantNumber()).thenReturn("APPLICANT-0001");
        when(applicant.getDisplayName()).thenReturn("Jemima Megan Lindsey Stevens");
        when(programmeChoice.getId()).thenReturn(UUID.randomUUID());
        when(programmeChoice.getProgrammeCode()).thenReturn("BSC-CS");
        when(programmeChoice.getProgrammeName()).thenReturn("Computer Science");
        when(assignment.getSelectionRound()).thenReturn(selectionRound);
        when(assignment.getApplication()).thenReturn(application);
        when(assignment.getProgrammeChoice()).thenReturn(programmeChoice);
        when(assignment.getStatus()).thenReturn(AcademicReviewAssignmentStatus.RECOMMENDED);
        when(recommendationRepository.saveAndFlush(any(AcademicUnitRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(recommendationRepository.findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(assignmentId))
                .thenReturn(List.of());
        CoreCurrentUserProfile reviewer = profile(rootUnitId);

        service.recommend(assignmentId, "SELECT", 1, "MERIT",
                "Meets the academic-unit selection criteria.", 0, reviewer);

        verify(selectionOfferService, never()).recordSelectionDecision(any(), any(), any(), any(), any(), any(), any());
        verify(outboxService).enqueueAcademicRecommendationRecorded(eq(assignment), any(AcademicUnitRecommendation.class));
    }

    private SelectionRound openRound(UUID selectionRoundId, UUID admissionCycleId) {
        AdmissionCycle cycle = mock(AdmissionCycle.class);
        when(cycle.getId()).thenReturn(admissionCycleId);
        SelectionRound round = mock(SelectionRound.class, withSettings().lenient());
        when(round.getId()).thenReturn(selectionRoundId);
        when(round.getAdmissionCycle()).thenReturn(cycle);
        when(round.getStatus()).thenReturn(SelectionRoundStatus.OPEN);
        return round;
    }

    private Application application(UUID applicationId) {
        return application(applicationId, ApplicationStatus.ELIGIBLE);
    }

    private Application application(UUID applicationId, ApplicationStatus status) {
        Application application = mock(Application.class, withSettings().lenient());
        Applicant applicant = mock(Applicant.class, withSettings().lenient());
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicationNumber()).thenReturn("APP-2028-0001");
        when(application.getStatus()).thenReturn(status);
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getApplicantNumber()).thenReturn("APPLICANT-0001");
        when(applicant.getDisplayName()).thenReturn("Jemima Megan Lindsey Stevens");
        return application;
    }

    private ApplicationProgrammeChoice choice(
            Application application, int rank, ProgrammeChoiceStatus status, UUID programmeId) {
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class, withSettings().lenient());
        when(choice.getId()).thenReturn(UUID.randomUUID());
        when(choice.getApplication()).thenReturn(application);
        when(choice.getChoiceRank()).thenReturn(rank);
        when(choice.getChoiceStatus()).thenReturn(status);
        when(choice.getProgrammeId()).thenReturn(programmeId);
        when(choice.getProgrammeCode()).thenReturn("PRG-" + rank);
        when(choice.getProgrammeName()).thenReturn("Programme " + rank);
        return choice;
    }

    private ProgrammeHierarchyResolution hierarchy(UUID programmeId, UUID rootUnitId, UUID leafUnitId) {
        AcademicUnitHierarchyNode root = new AcademicUnitHierarchyNode(
                rootUnitId, UUID.randomUUID(), "COLLEGE", null,
                "ROOT", "Highest Academic Unit", "ACTIVE", null, null, 0);
        AcademicUnitHierarchyNode leaf = new AcademicUnitHierarchyNode(
                leafUnitId, UUID.randomUUID(), "SCHOOL", rootUnitId,
                "LEAF", "Programme Owning Unit", "ACTIVE", null, null, 0);
        return new ProgrammeHierarchyResolution(
                programmeId, "PRG", "Programme", leaf, root, List.of(root, leaf));
    }

    private CoreCurrentUserProfile profile(UUID academicUnitId) {
        UUID userId = UUID.randomUUID();
        return new CoreCurrentUserProfile(
                new CoreUserSummary(userId, UUID.randomUUID(), "reviewer", "reviewer@example.test", "Reviewer", "ACTIVE"),
                List.of(new CoreRoleAssignmentSummary(
                        UUID.randomUUID(), UUID.randomUUID(), "ACADEMIC_UNIT_STAFF", "Academic Unit Staff", academicUnitId)));
    }
}

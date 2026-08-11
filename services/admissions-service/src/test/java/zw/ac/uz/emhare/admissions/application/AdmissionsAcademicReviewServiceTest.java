package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.*;

/**
 * Covers what remains of {@link AdmissionsAcademicReviewService} after the release/claim/
 * recommend/review/waitlist-release write workflow was retired per ADR-0014 (2026-08-11
 * admissions backend rolling-workflow plan, Task 4): reviewer-scoped work-queue and
 * application-workspace access.
 *
 * @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class AdmissionsAcademicReviewServiceTest {
    @Mock AcademicReviewAssignmentRepository assignmentRepository;
    @Mock AcademicUnitRecommendationRepository recommendationRepository;
    private AdmissionsAcademicReviewService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsAcademicReviewService(assignmentRepository, recommendationRepository);
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

    private CoreCurrentUserProfile profile(UUID academicUnitId) {
        UUID userId = UUID.randomUUID();
        return new CoreCurrentUserProfile(
                new CoreUserSummary(userId, UUID.randomUUID(), "reviewer", "reviewer@example.test", "Reviewer", "ACTIVE"),
                List.of(new CoreRoleAssignmentSummary(
                        UUID.randomUUID(), UUID.randomUUID(), "ACADEMIC_UNIT_STAFF", "Academic Unit Staff", academicUnitId)));
    }
}

package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;

/**
 * Read-only academic-review case history: assignment listing and reviewer-scoped access.
 *
 * <p>The release, claim, recommendation, review, and waitlist-release write workflows that used
 * to live here were retired per ADR-0014's hard cutover (see the 2026-08-11 admissions backend
 * rolling-workflow plan, Task 4) — programme choices now enter and leave academic review directly
 * via {@link ApplicationProgrammeChoice#enterAcademicReview()} /
 * {@link ApplicationProgrammeChoice#recordDecision(DecisionOutcome, String)} rather than through
 * this service's old release/claim/recommend/review/waitlist-release endpoints.
 *
 * @author Tinashe K
 */
@Service
public class AdmissionsAcademicReviewService {
    private static final List<AcademicReviewAssignmentStatus> ACTIVE_STATUSES = List.of(
            AcademicReviewAssignmentStatus.OPEN, AcademicReviewAssignmentStatus.CLAIMED,
            AcademicReviewAssignmentStatus.RECOMMENDED, AcademicReviewAssignmentStatus.RETURNED);

    private final AcademicReviewAssignmentRepository assignmentRepository;
    private final AcademicUnitRecommendationRepository recommendationRepository;

    public AdmissionsAcademicReviewService(
            AcademicReviewAssignmentRepository assignmentRepository,
            AcademicUnitRecommendationRepository recommendationRepository) {
        this.assignmentRepository = assignmentRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional
    public List<AcademicReviewSummary> listAssignments() {
        return assignmentRepository.findAllByDeletedAtIsNullOrderByReleasedAtDesc().stream().map(this::summary).toList();
    }

    @Transactional
    public List<AcademicReviewSummary> listMyAssignments(CoreCurrentUserProfile profile) {
        List<UUID> rootUnitIds = qualifyingRootUnitIds(profile);
        return rootUnitIds.stream()
                .flatMap(root -> assignmentRepository
                        .findAllByRecommendationAcademicUnitIdAndStatusInAndDeletedAtIsNullOrderByDueAtAscReleasedAtAsc(
                                root, ACTIVE_STATUSES).stream())
                .distinct().map(this::summary).toList();
    }

    @Transactional
    public UUID applicationIdForScopedAcademicReviewer(
            UUID assignmentId, CoreCurrentUserProfile profile) {
        AcademicReviewAssignment assignment = assignment(assignmentId);
        requireExactRootAssignment(profile, assignment.getRecommendationAcademicUnitId());
        return assignment.getApplication().getId();
    }

    private List<UUID> qualifyingRootUnitIds(CoreCurrentUserProfile profile) {
        if (profile == null || profile.user() == null || !"ACTIVE".equals(profile.user().status())
                || profile.roleAssignments() == null) {
            return List.of();
        }
        return profile.roleAssignments().stream()
                .filter(role -> "ACADEMIC_UNIT_STAFF".equals(role.roleCode()))
                .map(role -> role.academicUnitId()).filter(java.util.Objects::nonNull).distinct().toList();
    }
    private void requireExactRootAssignment(CoreCurrentUserProfile profile, UUID rootUnitId) {
        if (!qualifyingRootUnitIds(profile).contains(rootUnitId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "An active Academic Unit Staff assignment at the exact highest academic unit is required.");
        }
    }
    private AcademicReviewAssignment assignment(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Academic review assignment not found."));
    }
    private AcademicReviewSummary summary(AcademicReviewAssignment assignment) {
        AcademicUnitRecommendation recommendation = recommendationRepository
                .findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(assignment.getId())
                .stream().findFirst().orElse(null);
        return AcademicReviewSummary.from(assignment, recommendation);
    }
}

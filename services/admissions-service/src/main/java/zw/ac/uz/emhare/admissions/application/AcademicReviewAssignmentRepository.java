package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicReviewAssignmentRepository extends JpaRepository<AcademicReviewAssignment, UUID> {
    List<AcademicReviewAssignment> findAllByDeletedAtIsNullOrderByReleasedAtDesc();
    List<AcademicReviewAssignment> findAllByApplicationIdAndDeletedAtIsNullOrderByReleasedAtDesc(UUID applicationId);
    List<AcademicReviewAssignment> findAllByRecommendationAcademicUnitIdAndStatusInAndDeletedAtIsNullOrderByDueAtAscReleasedAtAsc(
            UUID academicUnitId, List<AcademicReviewAssignmentStatus> statuses);
    Optional<AcademicReviewAssignment> findBySelectionRoundIdAndProgrammeChoiceIdAndStatusInAndDeletedAtIsNull(
            UUID roundId, UUID choiceId, List<AcademicReviewAssignmentStatus> statuses);
    long countBySelectionRoundIdAndStatusInAndDeletedAtIsNull(UUID roundId, List<AcademicReviewAssignmentStatus> statuses);
    long countBySelectionRoundIdAndProgrammeChoiceId(UUID roundId, UUID choiceId);
}

package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicUnitRecommendationRepository extends JpaRepository<AcademicUnitRecommendation, UUID> {
    List<AcademicUnitRecommendation> findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(UUID assignmentId);
    Optional<AcademicUnitRecommendation> findByAssignmentIdAndReviewStatusAndDeletedAtIsNull(
            UUID assignmentId, AcademicRecommendationReviewStatus status);
    long countByAssignmentIdAndDeletedAtIsNull(UUID assignmentId);
}

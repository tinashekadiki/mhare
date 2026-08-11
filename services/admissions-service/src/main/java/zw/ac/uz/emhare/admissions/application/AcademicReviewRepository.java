package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicReviewRepository extends JpaRepository<AcademicReview, UUID> {
    Optional<AcademicReview> findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(UUID applicationId, UUID programmeChoiceId);
    java.util.List<AcademicReview> findAllByRecommendationAcademicUnitIdAndStatusAndDeletedAtIsNull(UUID recommendationAcademicUnitId, AcademicReviewStatus status);
}

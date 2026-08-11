package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicRecommendationRepository extends JpaRepository<AcademicRecommendation, UUID> {
    Optional<AcademicRecommendation> findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(UUID academicReviewId, AcademicRecommendationReviewStatus reviewStatus);
    int countByAcademicReviewIdAndDeletedAtIsNull(UUID academicReviewId);
}

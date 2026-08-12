package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendation;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicRecommendationRepository extends JpaRepository<AcademicRecommendation, UUID> {
    Optional<AcademicRecommendation> findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(UUID academicReviewId, AcademicRecommendationReviewStatus reviewStatus);
    int countByAcademicReviewIdAndDeletedAtIsNull(UUID academicReviewId);
    java.util.List<AcademicRecommendation> findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(UUID academicReviewId);
}

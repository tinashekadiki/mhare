package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.AcademicUnitRecommendation;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

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

package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionProjection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.*;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgressionDecisionProjectionRepository
        extends JpaRepository<ProgressionDecisionProjection, UUID> {
    Optional<ProgressionDecisionProjection> findBySourceProgressionDecisionIdAndDeletedAtIsNull(UUID sourceDecisionId);
    Optional<ProgressionDecisionProjection> findByStudentIdAndAcademicPeriodIdAndCurrentVersionTrueAndDeletedAtIsNull(
            UUID studentId, UUID academicPeriodId);
}

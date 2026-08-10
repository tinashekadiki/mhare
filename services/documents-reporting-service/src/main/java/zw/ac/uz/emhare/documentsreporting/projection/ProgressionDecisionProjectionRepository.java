package zw.ac.uz.emhare.documentsreporting.projection;

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

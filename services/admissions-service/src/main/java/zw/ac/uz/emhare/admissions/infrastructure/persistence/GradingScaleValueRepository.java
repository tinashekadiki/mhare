package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.GradingScaleValue;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface GradingScaleValueRepository extends JpaRepository<GradingScaleValue, UUID> {
    List<GradingScaleValue> findAllByGradingScaleIdAndDeletedAtIsNull(UUID gradingScaleId);
    List<GradingScaleValue> findAllByGradingScaleIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID gradingScaleId);
    Optional<GradingScaleValue> findByIdAndDeletedAtIsNull(UUID id);
    Optional<GradingScaleValue> findByGradingScaleIdAndGradeIgnoreCaseAndDeletedAtIsNull(UUID gradingScaleId, String grade);
}

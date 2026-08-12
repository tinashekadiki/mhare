package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedResultProjection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.*;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface PublishedResultProjectionRepository extends JpaRepository<PublishedResultProjection, UUID> {
    Optional<PublishedResultProjection> findBySourcePublishedResultIdAndDeletedAtIsNull(UUID sourcePublishedResultId);
    Optional<PublishedResultProjection> findByStudentIdAndAcademicPeriodIdAndModuleIdAndCurrentVersionTrueAndDeletedAtIsNull(
            UUID studentId, UUID academicPeriodId, UUID moduleId);
    List<PublishedResultProjection> findAllByStudentIdAndAcademicPeriodIdAndCurrentVersionTrueAndDeletedAtIsNullOrderByModuleCodeAsc(
            UUID studentId, UUID academicPeriodId);
}

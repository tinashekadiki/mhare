package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;

import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import zw.ac.uz.emhare.admissions.application.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicantQualificationSittingRepository extends JpaRepository<ApplicantQualificationSitting, UUID> {
    @EntityGraph(attributePaths = {"examBody"})
    List<ApplicantQualificationSitting> findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(UUID applicationId);

    @EntityGraph(attributePaths = {"application", "examBody"})
    Optional<ApplicantQualificationSitting> findByIdAndApplicationIdAndDeletedAtIsNull(UUID id, UUID applicationId);

    long countByApplicationIdAndDeletedAtIsNull(UUID applicationId);
}

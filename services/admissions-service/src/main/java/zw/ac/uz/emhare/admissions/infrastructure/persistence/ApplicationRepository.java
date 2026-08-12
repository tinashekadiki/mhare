package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.Application;

import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import zw.ac.uz.emhare.admissions.application.*;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {
    List<Application> findByApplicantUserId(UUID userId);
    List<Application> findByAdmissionCycleId(UUID admissionCycleId);

    @EntityGraph(attributePaths = {"admissionCycle", "applicationType"})
    List<Application> findAllByApplicantIdInAndDeletedAtIsNullOrderByCreatedAtDesc(List<UUID> applicantIds);

    @EntityGraph(attributePaths = {"admissionCycle", "applicationType"})
    List<Application> findAllByApplicantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID applicantId);

    @Query("""
            SELECT a FROM Application a
            WHERE a.admissionCycle.id = :admissionCycleId
              AND a.applicant.nationalIdNumber = :nationalIdNumber
              AND a.deletedAt IS NULL
            """)
    List<Application> findByAdmissionCycleIdAndApplicantNationalIdNumber(
            @Param("admissionCycleId") UUID admissionCycleId,
            @Param("nationalIdNumber") String nationalIdNumber);
}

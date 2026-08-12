package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.UUID;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface AdmissionRequirementSetRepository extends JpaRepository<AdmissionRequirementSet, UUID> {
    List<AdmissionRequirementSet> findAllByDeletedAtIsNullOrderByEffectiveFromDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select requirementSet
            from AdmissionRequirementSet requirementSet
            where requirementSet.programmeId = :programmeId
              and requirementSet.applicationType.id = :applicationTypeId
              and ((:admissionCycleId is null and requirementSet.admissionCycle is null)
                   or requirementSet.admissionCycle.id = :admissionCycleId)
              and requirementSet.status = zw.ac.uz.emhare.admissions.domain.model.RequirementSetStatus.APPROVED
              and requirementSet.deletedAt is null
            """)
    List<AdmissionRequirementSet> findApprovedForRouteForUpdate(
            @Param("programmeId") UUID programmeId,
            @Param("applicationTypeId") UUID applicationTypeId,
            @Param("admissionCycleId") UUID admissionCycleId);
}

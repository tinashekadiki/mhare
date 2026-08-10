package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgrammeVersionRepository extends JpaRepository<ProgrammeVersion, UUID> {
    List<ProgrammeVersion> findAllByProgrammeIdOrderByEffectiveFromDesc(UUID programmeId);
    boolean existsByProgrammeIdAndVersionCodeIgnoreCase(UUID programmeId, String versionCode);

    @Query("""
            select programmeVersion
            from ProgrammeVersion programmeVersion
            join fetch programmeVersion.programme programme
            join fetch programme.owningAcademicUnit owningAcademicUnit
            where programme.status = AcademicOfferingStatus.ACTIVE
              and programmeVersion.status = ProgrammeVersionStatus.APPROVED
              and programmeVersion.effectiveFrom <= :effectiveDate
              and (programmeVersion.effectiveTo is null or programmeVersion.effectiveTo >= :effectiveDate)
            order by programme.code
            """)
    List<ProgrammeVersion> findAdmissionsCatalogueVersions(LocalDate effectiveDate);
}

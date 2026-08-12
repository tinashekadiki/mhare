package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeProgrammeMapping;

/** @author Tinashe K */
public interface ApplicationTypeProgrammeMappingRepository extends JpaRepository<ApplicationTypeProgrammeMapping, UUID> {
    List<ApplicationTypeProgrammeMapping> findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
            UUID applicationTypeId);
    Optional<ApplicationTypeProgrammeMapping> findByApplicationTypeIdAndProgrammeIdAndActiveTrueAndDeletedAtIsNull(
            UUID applicationTypeId, UUID programmeId);
    List<ApplicationTypeProgrammeMapping> findAllByApplicationTypeIdAndDeletedAtIsNull(UUID applicationTypeId);
}

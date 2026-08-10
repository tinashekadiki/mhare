package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationTypeDocumentRequirementRepository
        extends JpaRepository<ApplicationTypeDocumentRequirement, UUID> {
    List<ApplicationTypeDocumentRequirement>
            findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                    UUID applicationTypeId);
    Optional<ApplicationTypeDocumentRequirement>
            findByApplicationTypeIdAndRequirementCodeAndActiveTrueAndDeletedAtIsNull(
                    UUID applicationTypeId, String requirementCode);
}

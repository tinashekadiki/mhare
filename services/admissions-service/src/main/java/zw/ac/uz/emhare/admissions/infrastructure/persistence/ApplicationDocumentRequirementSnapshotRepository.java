package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocumentRequirementSnapshot;

/** @author Tinashe K */
public interface ApplicationDocumentRequirementSnapshotRepository
        extends JpaRepository<ApplicationDocumentRequirementSnapshot, UUID> {
    List<ApplicationDocumentRequirementSnapshot>
            findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(UUID applicationId);
    Optional<ApplicationDocumentRequirementSnapshot>
            findByApplicationIdAndRequirementCodeAndDeletedAtIsNull(UUID applicationId, String requirementCode);
}

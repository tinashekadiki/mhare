package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirementCategory;

/**
 * @author Tinashe K
 */
public interface ApplicationTypeDocumentRequirementCategoryRepository
    extends JpaRepository<ApplicationTypeDocumentRequirementCategory, UUID> {

  List<ApplicationTypeDocumentRequirementCategory>
      findAllByDocumentRequirementIdInAndDeletedAtIsNull(List<UUID> documentRequirementIds);

  List<ApplicationTypeDocumentRequirementCategory> findAllByDocumentRequirementIdAndDeletedAtIsNull(
      UUID documentRequirementId);
}

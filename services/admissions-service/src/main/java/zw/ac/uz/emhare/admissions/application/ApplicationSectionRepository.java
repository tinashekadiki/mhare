package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationSectionRepository extends JpaRepository<ApplicationSection, UUID> {
    List<ApplicationSection> findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID applicationId);
    Optional<ApplicationSection> findByApplicationIdAndSectionCodeAndDeletedAtIsNull(UUID applicationId, String sectionCode);
}

package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationSection;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationSectionRepository extends JpaRepository<ApplicationSection, UUID> {
    List<ApplicationSection> findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID applicationId);
    Optional<ApplicationSection> findByApplicationIdAndSectionCodeAndDeletedAtIsNull(UUID applicationId, String sectionCode);
}

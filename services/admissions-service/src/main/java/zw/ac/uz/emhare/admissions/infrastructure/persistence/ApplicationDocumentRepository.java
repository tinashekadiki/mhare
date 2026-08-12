package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocument;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {
    List<ApplicationDocument> findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByRequirementCodeAsc(UUID applicationId);
    Optional<ApplicationDocument> findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(UUID documentId);
    Optional<ApplicationDocument> findByApplicationIdAndRequirementCodeAndCurrentTrueAndDeletedAtIsNull(
            UUID applicationId, String requirementCode);
}

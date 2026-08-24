package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantIdentityNameCorrection;

/**
 * @author Tinashe K
 */
public interface ApplicantIdentityNameCorrectionRepository
    extends JpaRepository<ApplicantIdentityNameCorrection, UUID> {
  Optional<ApplicantIdentityNameCorrection> findByApplicationIdAndDocumentIdAndDeletedAtIsNull(
      UUID applicationId, UUID documentId);

  Optional<ApplicantIdentityNameCorrection>
      findFirstByApplicationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID applicationId);

  Optional<ApplicantIdentityNameCorrection>
      findFirstByApplicationIdAndStatusNotAndDeletedAtIsNullOrderByUpdatedAtDesc(
          UUID applicationId,
          zw.ac.uz.emhare.admissions.domain.model.IdentityNameCorrectionStatus excludedStatus);
}

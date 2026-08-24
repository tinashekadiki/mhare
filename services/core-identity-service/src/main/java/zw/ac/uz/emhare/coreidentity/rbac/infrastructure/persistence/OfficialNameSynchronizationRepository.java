package zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.OfficialNameSynchronization;

/**
 * @author Tinashe K
 */
public interface OfficialNameSynchronizationRepository
    extends JpaRepository<OfficialNameSynchronization, UUID> {
  Optional<OfficialNameSynchronization> findBySourceRequestIdAndDeletedAtIsNull(
      UUID sourceRequestId);
}

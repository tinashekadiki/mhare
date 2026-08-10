package zw.ac.uz.emhare.coreidentity.provisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentPortalAccessProvisioningRepository extends JpaRepository<StudentPortalAccessProvisioning, UUID> {
    Optional<StudentPortalAccessProvisioning> findByConversionRequestIdAndDeletedAtIsNull(UUID conversionRequestId);
}

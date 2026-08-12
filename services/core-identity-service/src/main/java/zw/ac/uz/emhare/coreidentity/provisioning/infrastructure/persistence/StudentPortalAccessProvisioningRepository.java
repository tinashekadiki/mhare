package zw.ac.uz.emhare.coreidentity.provisioning.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.StudentPortalAccessProvisioning;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentPortalAccessProvisioningRepository extends JpaRepository<StudentPortalAccessProvisioning, UUID> {
    Optional<StudentPortalAccessProvisioning> findByConversionRequestIdAndDeletedAtIsNull(UUID conversionRequestId);
}

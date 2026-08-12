package zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
    @Query(value = """
            SELECT hashtextextended(CAST(:keycloakUserId AS text), 0)
            FROM pg_advisory_xact_lock(hashtextextended(CAST(:keycloakUserId AS text), 0))
            """, nativeQuery = true)
    long acquireIdentitySynchronizationLock(@Param("keycloakUserId") UUID keycloakUserId);

    Optional<PlatformUser> findByKeycloakUserId(UUID keycloakUserId);

    Optional<PlatformUser> findByEmail(String email);
}

package zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.InstitutionProfile;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstitutionProfileRepository extends JpaRepository<InstitutionProfile, UUID> {
    Optional<InstitutionProfile> findFirstByDeletedAtIsNullOrderByCreatedAtAsc();

    @Query(value = """
            SELECT
                (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL) AS "userCount",
                (SELECT COUNT(*) FROM roles WHERE deleted_at IS NULL) AS "roleCount",
                (SELECT COUNT(*) FROM permissions WHERE deleted_at IS NULL) AS "permissionCount",
                (SELECT COUNT(*) FROM lookup_sets WHERE deleted_at IS NULL) AS "lookupSetCount"
            """, nativeQuery = true)
    CoreStatisticsProjection loadCoreStatistics();

    interface CoreStatisticsProjection {
        long getUserCount();

        long getRoleCount();

        long getPermissionCount();

        long getLookupSetCount();
    }
}

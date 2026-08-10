package zw.ac.uz.emhare.coreidentity.rbac;

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

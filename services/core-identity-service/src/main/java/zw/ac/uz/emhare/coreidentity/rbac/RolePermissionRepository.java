package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    boolean existsByRoleAndPermission(Role role, Permission permission);

    Optional<RolePermission> findByRoleAndPermission(Role role, Permission permission);

    List<RolePermission> findByRoleIdAndDeletedAtIsNull(UUID roleId);
}

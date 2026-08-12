package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Permission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RolePermission;

import java.util.UUID;

public record RolePermissionSummary(
        UUID id,
        UUID roleId,
        UUID permissionId,
        String permissionCode,
        String permissionName,
        PermissionCategory category) {
    static RolePermissionSummary from(RolePermission rolePermission) {
        Permission permission = rolePermission.getPermission();
        return new RolePermissionSummary(
                rolePermission.getId(),
                rolePermission.getRole().getId(),
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getCategory());
    }
}

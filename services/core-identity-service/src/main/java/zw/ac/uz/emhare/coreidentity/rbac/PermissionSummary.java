package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.UUID;

public record PermissionSummary(UUID id, String code, String name, PermissionCategory category, String description) {
    static PermissionSummary from(Permission permission) {
        return new PermissionSummary(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getCategory(),
                permission.getDescription());
    }
}

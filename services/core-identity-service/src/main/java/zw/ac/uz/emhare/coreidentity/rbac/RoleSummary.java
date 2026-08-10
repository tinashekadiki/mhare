package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.UUID;

public record RoleSummary(UUID id, String code, String name, RoleScope scope, boolean systemManaged) {
    static RoleSummary from(Role role) {
        return new RoleSummary(role.getId(), role.getCode(), role.getName(), role.getScope(), role.isSystemManaged());
    }
}

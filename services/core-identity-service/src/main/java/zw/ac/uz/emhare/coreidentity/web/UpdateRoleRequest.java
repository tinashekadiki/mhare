package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.ac.uz.emhare.coreidentity.rbac.RoleScope;

public record UpdateRoleRequest(@NotBlank String name, @NotNull RoleScope scope, boolean systemManaged) {
}

package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.ac.uz.emhare.coreidentity.rbac.RoleScope;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull RoleScope scope,
        boolean systemManaged) {
}

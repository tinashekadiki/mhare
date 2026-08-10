package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleCommand(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull RoleScope scope,
        boolean systemManaged) {
}

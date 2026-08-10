package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePermissionCommand(@NotBlank String name, @NotNull PermissionCategory category, String description) {
}

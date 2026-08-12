package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RoleScope;

public record UpdateRoleRequest(@NotBlank String name, @NotNull RoleScope scope, boolean systemManaged) {
}

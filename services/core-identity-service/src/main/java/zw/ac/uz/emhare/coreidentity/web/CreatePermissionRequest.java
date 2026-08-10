package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.ac.uz.emhare.coreidentity.rbac.PermissionCategory;

public record CreatePermissionRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull PermissionCategory category,
        String description) {
}

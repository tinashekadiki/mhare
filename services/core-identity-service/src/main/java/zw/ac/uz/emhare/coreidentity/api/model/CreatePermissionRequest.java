package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;

public record CreatePermissionRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull PermissionCategory category,
        String description) {
}

package zw.ac.uz.emhare.coreidentity.rbac.application.command;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePermissionCommand(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull PermissionCategory category,
        String description) {
}

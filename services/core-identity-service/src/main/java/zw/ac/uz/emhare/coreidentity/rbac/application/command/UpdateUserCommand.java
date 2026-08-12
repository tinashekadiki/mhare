package zw.ac.uz.emhare.coreidentity.rbac.application.command;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserStatus;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserCommand(
        @NotBlank String displayName,
        String phoneNumber,
        @NotNull UserStatus status) {
}

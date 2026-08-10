package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.ac.uz.emhare.coreidentity.rbac.UserStatus;

public record UpdateUserRequest(@NotBlank String displayName, String phoneNumber, @NotNull UserStatus status) {
}

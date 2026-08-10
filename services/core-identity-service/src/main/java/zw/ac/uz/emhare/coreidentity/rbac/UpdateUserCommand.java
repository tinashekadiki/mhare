package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserCommand(
        @NotBlank String displayName,
        String phoneNumber,
        @NotNull UserStatus status) {
}

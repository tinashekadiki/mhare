package zw.ac.uz.emhare.coreidentity.rbac.application.command;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RegisterUserCommand(
        UUID keycloakUserId,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String displayName) {
}

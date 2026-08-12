package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RegisterUserRequest(
        UUID keycloakUserId,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String displayName) {
}

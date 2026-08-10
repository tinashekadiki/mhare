package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record ProvisionUserAccessCommand(
        UUID keycloakUserId,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String displayName,
        String phoneNumber,
        @NotEmpty List<@Valid ProvisionedRoleAssignmentCommand> roleAssignments) {
}

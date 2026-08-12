package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record ProvisionUserAccessRequest(
        UUID keycloakUserId,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String displayName,
        String phoneNumber,
        @NotEmpty List<@Valid ProvisionedRoleAssignmentRequest> roleAssignments) {
}

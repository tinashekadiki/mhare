package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;

import java.util.UUID;

public record CoreUserSummary(
        UUID id,
        UUID keycloakUserId,
        String username,
        String email,
        String phoneNumber,
        String displayName,
        String status,
        java.time.Instant lastLoginAt) {

    static CoreUserSummary from(PlatformUser user) {
        return new CoreUserSummary(
                user.getId(),
                user.getKeycloakUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getLastLoginAt());
    }
}

package zw.ac.uz.emhare.common.security;

import java.util.Set;
import java.util.UUID;

public record EmhareCurrentUser(
        UUID keycloakUserId,
        UUID localUserId,
        String email,
        String username,
        String displayName,
        Set<String> realmRoles) {

    public boolean hasRealmRole(String role) {
        return realmRoles.contains(role);
    }

    public UUID auditUserId() {
        return localUserId == null ? keycloakUserId : localUserId;
    }
}

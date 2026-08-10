package zw.ac.uz.emhare.coreidentity.rbac;

import java.time.Instant;
import java.util.UUID;

public record LoginEventSummary(
        UUID id,
        UUID userId,
        UUID keycloakUserId,
        String username,
        String email,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        LoginOutcome outcome) {
    static LoginEventSummary from(LoginEvent loginEvent) {
        return new LoginEventSummary(
                loginEvent.getId(),
                loginEvent.getUser() == null ? null : loginEvent.getUser().getId(),
                loginEvent.getKeycloakUserId(),
                loginEvent.getUsername(),
                loginEvent.getEmail(),
                loginEvent.getOccurredAt(),
                loginEvent.getIpAddress(),
                loginEvent.getUserAgent(),
                loginEvent.getOutcome());
    }
}

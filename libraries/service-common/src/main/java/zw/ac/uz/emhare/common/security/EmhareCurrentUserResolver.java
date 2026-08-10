package zw.ac.uz.emhare.common.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class EmhareCurrentUserResolver {

    public Optional<EmhareCurrentUser> currentUser() {
        return fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    }

    public Optional<EmhareCurrentUser> fromAuthentication(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return Optional.of(fromJwt(jwtAuthenticationToken.getToken()));
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(fromJwt(jwt));
        }
        return Optional.empty();
    }

    public EmhareCurrentUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
    }

    public EmhareCurrentUser fromJwt(Jwt jwt) {
        UUID keycloakUserId = parseUuid(jwt.getSubject()).orElse(null);
        UUID localUserId = parseUuid(jwt.getClaimAsString("emhare_user_id")).orElse(null);
        String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"));
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), email);
        String displayName = firstNonBlank(jwt.getClaimAsString("name"), username, email);
        return new EmhareCurrentUser(keycloakUserId, localUserId, email, username, displayName, realmRoles(jwt));
    }

    private Set<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return Set.of();
        }
        Object roles = realmAccessMap.get("roles");
        if (!(roles instanceof Collection<?> roleValues)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object role : roleValues) {
            if (role instanceof String roleName && !roleName.isBlank()) {
                result.add(roleName);
            }
        }
        return result;
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

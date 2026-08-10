package zw.ac.uz.emhare.admissions.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CoreIdentityClient {

    private final RestClient restClient;

    public CoreIdentityClient(RestClient.Builder restClientBuilder, @Value("${emhare.core-identity.url:http://localhost:8081}") String coreIdentityUrl) {
        this.restClient = restClientBuilder.baseUrl(coreIdentityUrl).build();
    }

    public CoreCurrentUserProfile syncCurrentUser(Authentication authentication) {
        return restClient.get()
                .uri("/api/core/me")
                .headers(headers -> headers.setBearerAuth(token(authentication)))
                .retrieve()
                .body(CoreCurrentUserProfile.class);
    }

    private String token(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("JWT authentication is required.");
    }

    public record CoreCurrentUserProfile(
            CoreUserSummary user,
            List<CoreRoleAssignmentSummary> roleAssignments,
            List<String> realmRoles,
            List<String> effectivePermissionCodes,
            boolean operationalAccess) {
        public CoreCurrentUserProfile(CoreUserSummary user, List<CoreRoleAssignmentSummary> roleAssignments) {
            this(user, roleAssignments, List.of(), List.of(), false);
        }
    }

    public record CoreUserSummary(UUID id, UUID keycloakUserId, String username, String email, String displayName, String status) {
    }

    public record CoreRoleAssignmentSummary(UUID id, UUID roleId, String roleCode, String roleName, UUID academicUnitId) {
    }
}

package zw.ac.uz.emhare.admissions.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.admissions.integration.http.CoreIdentityHttpService;

@Component
public class CoreIdentityClient {

    private final CoreIdentityHttpService coreIdentityHttpService;

    public CoreIdentityClient(CoreIdentityHttpService coreIdentityHttpService) {
        this.coreIdentityHttpService = coreIdentityHttpService;
    }

    public CoreCurrentUserProfile syncCurrentUser(Authentication authentication) {
        try {
            return coreIdentityHttpService.syncCurrentUser("Bearer " + token(authentication));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new AccessDeniedException("Core Identity rejected the current user context.", exception);
            }
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    public CoreInstitutionProfile institutionProfile(String authorization) {
        try {
            CoreInstitutionProfile profile = coreIdentityHttpService.institutionProfile(authorization);
            if (profile == null) {
                throw new ServiceDependencyUnavailableException("Core Identity returned an empty institution profile.", null);
            }
            return profile;
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private ServiceDependencyUnavailableException unavailable(Throwable cause) {
        return new ServiceDependencyUnavailableException(
                "Core Identity is unavailable, so the current user cannot be synchronized.", cause);
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

    public record CoreInstitutionProfile(
            UUID id, String code, String name, String legalName, String defaultCurrencyCode,
            String countryCode, String timezone, String contactDetailsJson, String brandingJson, String legacyCode) { }
}

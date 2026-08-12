package zw.ac.uz.emhare.documentsreporting.integration;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.documentsreporting.integration.http.CoreIdentityHttpService;

/** Resolves the authoritative local user id used by document ownership projections. @author Tinashe K */
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

    private String token(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("JWT authentication is required.");
    }

    private ServiceDependencyUnavailableException unavailable(Throwable cause) {
        return new ServiceDependencyUnavailableException(
                "Core Identity is unavailable, so document ownership cannot be verified.", cause);
    }

    public record CoreCurrentUserProfile(CoreUserSummary user) {
    }

    public record CoreUserSummary(
            UUID id,
            UUID keycloakUserId,
            String username,
            String email,
            String displayName,
            String status) {
    }
}

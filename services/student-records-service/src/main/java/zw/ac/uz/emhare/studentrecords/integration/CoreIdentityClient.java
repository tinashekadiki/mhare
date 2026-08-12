package zw.ac.uz.emhare.studentrecords.integration;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.studentrecords.integration.http.CoreIdentityHttpService;

/** @author Tinashe K */
@Component
public class CoreIdentityClient {

    private final CoreIdentityHttpService coreIdentityHttpService;

    public CoreIdentityClient(CoreIdentityHttpService coreIdentityHttpService) {
        this.coreIdentityHttpService = coreIdentityHttpService;
    }

    public UUID syncCurrentUserId(Authentication authentication) {
        CoreCurrentUserProfile profile;
        try {
            profile = coreIdentityHttpService.syncCurrentUser("Bearer " + token(authentication));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new AccessDeniedException("Core Identity rejected the current user context.", exception);
            }
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        if (profile == null || profile.user() == null || profile.user().id() == null) {
            throw new ServiceDependencyUnavailableException(
                    "Core Identity returned an incomplete current-user profile.", null);
        }
        return profile.user().id();
    }

    private ServiceDependencyUnavailableException unavailable(Throwable cause) {
        return new ServiceDependencyUnavailableException(
                "Core Identity is unavailable, so student ownership cannot be verified.", cause);
    }

    private String token(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("JWT authentication is required.");
    }

    public record CoreCurrentUserProfile(CoreUserSummary user) {
    }

    public record CoreUserSummary(UUID id) {
    }
}

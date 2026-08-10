package zw.ac.uz.emhare.studentrecords.integration;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** @author Tinashe K */
@Component
public class CoreIdentityClient {

    private final RestClient restClient;

    public CoreIdentityClient(
            RestClient.Builder restClientBuilder,
            @Value("${emhare.core-identity.url:http://localhost:8081}") String coreIdentityUrl) {
        this.restClient = restClientBuilder.baseUrl(coreIdentityUrl).build();
    }

    public UUID syncCurrentUserId(Authentication authentication) {
        CoreCurrentUserProfile profile;
        try {
            profile = restClient.get()
                    .uri("/api/core/me")
                    .headers(headers -> headers.setBearerAuth(token(authentication)))
                    .retrieve()
                    .body(CoreCurrentUserProfile.class);
        } catch (RuntimeException exception) {
            throw new ServiceDependencyUnavailableException(
                    "Core Identity is unavailable, so student ownership cannot be verified.", exception);
        }
        if (profile == null || profile.user() == null || profile.user().id() == null) {
            throw new ServiceDependencyUnavailableException(
                    "Core Identity returned an incomplete current-user profile.", null);
        }
        return profile.user().id();
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

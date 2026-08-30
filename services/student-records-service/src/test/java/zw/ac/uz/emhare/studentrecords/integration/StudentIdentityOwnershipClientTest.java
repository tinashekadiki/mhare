package zw.ac.uz.emhare.studentrecords.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.studentrecords.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.studentrecords.integration.CoreIdentityClient.CoreUserSummary;
import zw.ac.uz.emhare.studentrecords.integration.http.CoreIdentityHttpService;

/**
 * @author Tinashe K
 */
class StudentIdentityOwnershipClientTest {
  private final CoreIdentityHttpService http = mock(CoreIdentityHttpService.class);
  private final CoreIdentityClient client = new CoreIdentityClient(http);
  private final JwtAuthenticationToken authentication =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("owned-token")
              .header("alg", "none")
              .subject(UUID.randomUUID().toString())
              .build());

  @Test
  void resolvesTheLocalIdentityUsingBearerAuthentication() {
    UUID userId = UUID.randomUUID();
    when(http.syncCurrentUser("Bearer owned-token"))
        .thenReturn(new CoreCurrentUserProfile(new CoreUserSummary(userId)));
    assertEquals(userId, client.syncCurrentUserId(authentication));
    verify(http).syncCurrentUser("Bearer owned-token");
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404})
  void identityRejectionsRemainAccessDenied(int status) {
    when(http.syncCurrentUser(anyString()))
        .thenThrow(
            new RestClientResponseException(
                "Rejected", status, "Rejected", HttpHeaders.EMPTY, new byte[0], null));
    assertThrows(AccessDeniedException.class, () -> client.syncCurrentUserId(authentication));
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 503})
  void serverFailuresCannotBeTreatedAsOwnershipApproval(int status) {
    when(http.syncCurrentUser(anyString()))
        .thenThrow(
            new RestClientResponseException(
                "Unavailable", status, "Unavailable", HttpHeaders.EMPTY, new byte[0], null));
    ServiceDependencyUnavailableException error =
        assertThrows(
            ServiceDependencyUnavailableException.class,
            () -> client.syncCurrentUserId(authentication));
    assertTrue(error.getMessage().contains("ownership cannot be verified"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"profile", "user", "id"})
  void incompleteProfilesFailClosed(String missing) {
    when(http.syncCurrentUser(anyString()))
        .thenReturn(
            missing.equals("profile")
                ? null
                : new CoreCurrentUserProfile(
                    missing.equals("user") ? null : new CoreUserSummary(null)));
    assertThrows(
        ServiceDependencyUnavailableException.class,
        () -> client.syncCurrentUserId(authentication));
  }

  @Test
  void networkFailureAndMissingJwtCannotResolveAnIdentity() {
    when(http.syncCurrentUser(anyString()))
        .thenThrow(new IllegalStateException("Connection failed"));
    assertThrows(
        ServiceDependencyUnavailableException.class,
        () -> client.syncCurrentUserId(authentication));
    reset(http);
    assertThrows(ServiceDependencyUnavailableException.class, () -> client.syncCurrentUserId(null));
    verifyNoInteractions(http);
  }
}

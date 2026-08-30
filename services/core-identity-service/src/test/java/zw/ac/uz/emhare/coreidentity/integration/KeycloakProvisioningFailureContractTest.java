package zw.ac.uz.emhare.coreidentity.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** HTTP-adapter contract tests without a live identity provider. @author Tinashe K */
class KeycloakProvisioningFailureContractTest {
  private static final String BASE = "https://identity.example.test";
  private static final String USERS = BASE + "/admin/realms/emhare/users";
  private MockRestServiceServer server;
  private KeycloakIdentityProvisioningClient client;

  @BeforeEach
  void setup() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new KeycloakIdentityProvisioningClient(
            builder, BASE + "/", "emhare", "provisioner", "test-secret");
  }

  @ParameterizedTest
  @ValueSource(strings = {"SERVER", "REALM", "CLIENT", "SECRET"})
  void configuration_shouldRejectMissingProvisioningSettings(String field) {
    assertThrows(
        IllegalStateException.class,
        () ->
            new KeycloakIdentityProvisioningClient(
                field.equals("SERVER") ? null : BASE, field.equals("REALM") ? " " : "emhare",
                field.equals("CLIENT") ? null : "provisioner",
                    field.equals("SECRET") ? "" : "secret"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void provision_shouldRejectMissingIdentityFieldsBeforeHttp(String absent) {
    assertThrows(
        IllegalArgumentException.class,
        () -> client.provisionUser(absent, "user@example.test", "User"));
    assertThrows(
        IllegalArgumentException.class, () -> client.provisionUser("user", absent, "User"));
    assertThrows(
        IllegalArgumentException.class,
        () -> client.provisionUser("user", "user@example.test", absent));
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "{\"access_token\":\" \"}"})
  void token_shouldRequireNonBlankAccessToken(String response) {
    server
        .expect(requestTo(BASE + "/realms/emhare/protocol/openid-connect/token"))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    assertEquals(
        "Keycloak returned no access token for user provisioning.",
        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.provisionUser("user", "user@example.test", "User"))
            .getMessage());
    server.verify();
  }

  @Test
  void authenticationFailure_shouldSurfaceDependencyFailureWithoutLookingUpUsers() {
    server
        .expect(requestTo(BASE + "/realms/emhare/protocol/openid-connect/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertEquals(
        "Keycloak provisioning authentication failed.",
        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.provisionUser("user", "user@example.test", "User"))
            .getMessage());
    server.verify();
  }

  @Test
  void lookupFailure_shouldNotProceedToCreation() {
    token();
    server
        .expect(requestTo(USERS + "?username=user&exact=true"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertEquals(
        "Keycloak user lookup failed.",
        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.provisionUser("user", "user@example.test", "User"))
            .getMessage());
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIFFERENT_ACCOUNTS", "USERNAME_MISMATCH", "EMAIL_MISMATCH", "MATCH"})
  void reuse_shouldRequireUsernameAndEmailToIdentifyTheSameAccount(String scenario) {
    UUID first = UUID.randomUUID();
    token();
    String username = scenario.equals("USERNAME_MISMATCH") ? "another" : "user";
    String email = scenario.equals("EMAIL_MISMATCH") ? "another@example.test" : "USER@example.test";
    lookup("username", representation(first, username, email));
    lookup(
        "email",
        representation(
            scenario.equals("DIFFERENT_ACCOUNTS") ? UUID.randomUUID() : first, username, email));
    if (scenario.equals("MATCH")) {
      var result = client.provisionUser("user", "user@example.test", "User");
      assertEquals(first, result.keycloakUserId());
      assertFalse(result.created());
      assertNull(result.temporaryPassword());
    } else {
      assertThrows(
          IllegalStateException.class,
          () -> client.provisionUser("user", "user@example.test", "User"));
    }
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MISSING", "OPAQUE", "NO_SLASH", "TRAILING_SLASH", "INVALID_UUID"})
  void create_shouldResolveAccountWhenLocationDoesNotContainAnIdentity(String locationCase) {
    UUID createdId = UUID.randomUUID();
    token();
    lookup("username", "");
    lookup("email", "[]");
    var creation =
        server
            .expect(requestTo(USERS))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.firstName").value("User"))
            .andExpect(jsonPath("$.lastName").value(""))
            .andExpect(jsonPath("$.email").value("user@example.test"));
    var response = withStatus(HttpStatus.CREATED);
    if (!locationCase.equals("MISSING"))
      response.location(
          URI.create(
              switch (locationCase) {
                case "OPAQUE" -> "mailto:identity@example.test";
                case "NO_SLASH" -> "identity";
                case "TRAILING_SLASH" -> USERS + "/";
                default -> USERS + "/not-a-uuid";
              }));
    creation.andRespond(response);
    lookup("username", "[]");
    lookup("email", representation(createdId, "user@example.test", "user@example.test"));
    var result = client.provisionUser(" user ", " USER@example.test ", " User ");
    assertEquals(createdId, result.keycloakUserId());
    assertTrue(result.created());
    assertTrue(
        result
            .temporaryPassword()
            .matches("(?s)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%]).{20}"));
    server.verify();
  }

  @Test
  void create_shouldReportUnresolvableSuccessfulCreation() {
    token();
    lookup("username", "[]");
    lookup("email", "[]");
    server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CREATED));
    lookup("username", "[]");
    lookup("email", "[]");
    assertEquals(
        "Keycloak created the user but did not return a resolvable identity.",
        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.provisionUser("user", "user@example.test", "User"))
            .getMessage());
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void createConflict_shouldReuseOnlyAResolvableConcurrentIdentity(boolean resolves) {
    UUID existing = UUID.randomUUID();
    token();
    lookup("username", "[]");
    lookup("email", "[]");
    server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CONFLICT));
    lookup("username", "[]");
    lookup(
        "email",
        resolves ? representation(existing, "user@example.test", "user@example.test") : "[]");
    if (resolves) {
      var result = client.provisionUser("user", "user@example.test", "User");
      assertEquals(existing, result.keycloakUserId());
      assertFalse(result.created());
      assertNull(result.temporaryPassword());
    } else
      assertThrows(
          IllegalStateException.class,
          () -> client.provisionUser("user", "user@example.test", "User"));
    server.verify();
  }

  @Test
  void createRejection_shouldSurfaceDependencyFailure() {
    token();
    lookup("username", "[]");
    lookup("email", "[]");
    server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.FORBIDDEN));
    assertEquals(
        "Keycloak rejected user provisioning.",
        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.provisionUser("user", "user@example.test", "User"))
            .getMessage());
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {404, 503})
  void deleteCompensation_shouldIgnoreAbsentAccountButReportOtherFailures(int status) {
    UUID identity = UUID.randomUUID();
    token();
    server
        .expect(requestTo(USERS + "/" + identity))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.valueOf(status)));
    if (status == 404) assertDoesNotThrow(() -> client.deleteUser(identity));
    else
      assertEquals(
          "Keycloak could not roll back the newly created user.",
          assertThrows(
                  ServiceDependencyUnavailableException.class, () -> client.deleteUser(identity))
              .getMessage());
    server.verify();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void officialName_shouldOmitAbsentMiddleNameAndTrimApprovedNames(String middle) {
    UUID identity = UUID.randomUUID();
    token();
    server
        .expect(requestTo(USERS + "/" + identity))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(jsonPath("$.firstName").value("Tariro"))
        .andExpect(jsonPath("$.lastName").value("Moyo"))
        .andRespond(withNoContent());
    client.updateOfficialName(identity, " Tariro ", middle, " Moyo ");
    server.verify();
  }

  @Test
  void officialName_shouldRejectUnlinkedAccountAndExposeProviderFailure() {
    assertThrows(
        IllegalStateException.class, () -> client.updateOfficialName(null, "Tariro", null, "Moyo"));
    UUID identity = UUID.randomUUID();
    token();
    server
        .expect(requestTo(USERS + "/" + identity))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertEquals(
        "Keycloak could not synchronize the approved official name.",
        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.updateOfficialName(identity, "Tariro", null, "Moyo"))
            .getMessage());
    server.verify();
  }

  private void token() {
    server
        .expect(requestTo(BASE + "/realms/emhare/protocol/openid-connect/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"access_token\":\"test-token\"}", MediaType.APPLICATION_JSON));
  }

  private void lookup(String field, String response) {
    server
        .expect(
            requestTo(
                USERS
                    + "?"
                    + field
                    + "="
                    + (field.equals("username") ? "user" : "user@example.test")
                    + "&exact=true"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
  }

  private String representation(UUID id, String username, String email) {
    return "[{\"id\":\""
        + id
        + "\",\"username\":\""
        + username
        + "\",\"email\":\""
        + email
        + "\"}]";
  }
}

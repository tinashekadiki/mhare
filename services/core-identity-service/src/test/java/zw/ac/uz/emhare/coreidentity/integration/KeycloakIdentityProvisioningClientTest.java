package zw.ac.uz.emhare.coreidentity.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import zw.ac.uz.emhare.coreidentity.rbac.IdentityProvisioningPort;

/** @author Tinashe K */
class KeycloakIdentityProvisioningClientTest {

    private MockRestServiceServer mockKeycloak;
    private KeycloakIdentityProvisioningClient provisioningClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockKeycloak = MockRestServiceServer.bindTo(restClientBuilder).build();
        provisioningClient = new KeycloakIdentityProvisioningClient(
                restClientBuilder,
                "https://keycloak.example.test",
                "emhare",
                "emhare-core-identity-provisioner",
                "test-client-secret");
    }

    @Test
    void provisionUser_shouldCreateKeycloakUserWithTemporaryPassword() {
        UUID keycloakUserId = UUID.randomUUID();
        expectAccessToken();
        expectEmptyUserLookup("username", "finance.operator");
        expectEmptyUserLookup("email", "finance.operator@example.test");
        mockKeycloak.expect(requestTo("https://keycloak.example.test/admin/realms/emhare/users"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.username").value("finance.operator@example.test"))
                .andExpect(jsonPath("$.email").value("finance.operator@example.test"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.requiredActions[0]").value("UPDATE_PASSWORD"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andRespond(request -> withStatus(HttpStatus.CREATED)
                        .location(URI.create("https://keycloak.example.test/admin/realms/emhare/users/" + keycloakUserId))
                        .createResponse(request));

        IdentityProvisioningPort.ProvisionedIdentity result = provisioningClient.provisionUser(
                "finance.operator",
                "finance.operator@example.test",
                "Finance Operator");

        assertEquals(keycloakUserId, result.keycloakUserId());
        assertTrue(result.created());
        assertNotNull(result.temporaryPassword());
        assertTrue(result.temporaryPassword().length() >= 20);
        mockKeycloak.verify();
    }

    @Test
    void provisionUser_shouldReuseMatchingKeycloakUser() {
        UUID keycloakUserId = UUID.randomUUID();
        String keycloakUser = """
                [{"id":"%s","username":"finance.operator@example.test","email":"finance.operator@example.test"}]
                """.formatted(keycloakUserId);
        expectAccessToken();
        expectEmptyUserLookup("username", "finance.operator");
        expectUserLookup("email", "finance.operator@example.test", keycloakUser);

        IdentityProvisioningPort.ProvisionedIdentity result = provisioningClient.provisionUser(
                "finance.operator",
                "finance.operator@example.test",
                "Finance Operator");

        assertEquals(keycloakUserId, result.keycloakUserId());
        assertFalse(result.created());
        assertEquals(null, result.temporaryPassword());
        mockKeycloak.verify();
    }

    @Test
    void deleteUser_shouldRemoveKeycloakIdentity() {
        UUID keycloakUserId = UUID.randomUUID();
        expectAccessToken();
        mockKeycloak.expect(requestTo(
                        "https://keycloak.example.test/admin/realms/emhare/users/" + keycloakUserId))
                .andExpect(method(DELETE))
                .andRespond(withNoContent());

        provisioningClient.deleteUser(keycloakUserId);

        mockKeycloak.verify();
    }

    private void expectAccessToken() {
        mockKeycloak.expect(requestTo(
                        "https://keycloak.example.test/realms/emhare/protocol/openid-connect/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"provisioning-token\"}", MediaType.APPLICATION_JSON));
    }

    private void expectEmptyUserLookup(String field, String encodedValue) {
        expectUserLookup(field, encodedValue, "[]");
    }

    private void expectUserLookup(String field, String encodedValue, String responseBody) {
        mockKeycloak.expect(requestTo(
                        "https://keycloak.example.test/admin/realms/emhare/users?"
                                + field + "=" + encodedValue + "&exact=true"))
                .andExpect(method(GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }
}

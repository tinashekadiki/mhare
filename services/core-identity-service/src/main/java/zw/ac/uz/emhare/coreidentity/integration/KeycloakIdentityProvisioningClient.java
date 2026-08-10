package zw.ac.uz.emhare.coreidentity.integration;

import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.coreidentity.rbac.IdentityProvisioningPort;

/** @author Tinashe K */
@Component
public class KeycloakIdentityProvisioningClient implements IdentityProvisioningPort {

    private static final ParameterizedTypeReference<List<KeycloakUserRepresentation>> KEYCLOAK_USERS_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final String PASSWORD_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PASSWORD_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String PASSWORD_DIGITS = "23456789";
    private static final String PASSWORD_SYMBOLS = "!@#$%";
    private static final String PASSWORD_ALPHABET =
            PASSWORD_UPPERCASE + PASSWORD_LOWERCASE + PASSWORD_DIGITS + PASSWORD_SYMBOLS;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public KeycloakIdentityProvisioningClient(
            @Value("${emhare.identity-provisioning.keycloak.server-url}") String serverUrl,
            @Value("${emhare.identity-provisioning.keycloak.realm}") String realm,
            @Value("${emhare.identity-provisioning.keycloak.client-id}") String clientId,
            @Value("${emhare.identity-provisioning.keycloak.client-secret}") String clientSecret) {
        this(RestClient.builder(), serverUrl, realm, clientId, clientSecret);
    }

    KeycloakIdentityProvisioningClient(
            RestClient.Builder restClientBuilder,
            String serverUrl,
            String realm,
            String clientId,
            String clientSecret) {
        this.restClient = restClientBuilder.baseUrl(removeTrailingSlash(serverUrl)).build();
        this.realm = requireConfigured(realm, "Keycloak provisioning realm");
        this.clientId = requireConfigured(clientId, "Keycloak provisioning client id");
        this.clientSecret = requireConfigured(clientSecret, "Keycloak provisioning client secret");
    }

    @Override
    public ProvisionedIdentity provisionUser(String username, String email, String displayName) {
        String normalizedUsername = requireText(username, "Username is required.");
        String normalizedEmail = requireText(email, "Email is required.").toLowerCase(Locale.ROOT);
        String normalizedDisplayName = requireText(displayName, "Display name is required.");
        String accessToken = accessToken();

        KeycloakUserRepresentation existingUser = resolveExistingUser(
                accessToken, normalizedUsername, normalizedEmail);
        if (existingUser != null) {
            return new ProvisionedIdentity(existingUser.id(), false, null);
        }

        String temporaryPassword = temporaryPassword();
        String[] nameParts = normalizedDisplayName.split("\\s+", 2);
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("username", normalizedEmail);
        userPayload.put("email", normalizedEmail);
        userPayload.put("firstName", nameParts[0]);
        userPayload.put("lastName", nameParts.length == 1 ? "" : nameParts[1]);
        userPayload.put("enabled", true);
        userPayload.put("emailVerified", true);
        userPayload.put("requiredActions", List.of("UPDATE_PASSWORD"));
        userPayload.put("credentials", List.of(Map.of(
                "type", "password",
                "value", temporaryPassword,
                "temporary", true)));
        try {
            ResponseEntity<Void> createResponse = restClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userPayload)
                    .retrieve()
                    .toBodilessEntity();
            UUID userId = userIdFromLocation(createResponse.getHeaders().getLocation());
            if (userId == null) {
                KeycloakUserRepresentation createdUser = resolveExistingUser(
                        accessToken, normalizedUsername, normalizedEmail);
                if (createdUser == null) {
                    throw new ServiceDependencyUnavailableException(
                            "Keycloak created the user but did not return a resolvable identity.", null);
                }
                userId = createdUser.id();
            }
            return new ProvisionedIdentity(userId, true, temporaryPassword);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                KeycloakUserRepresentation concurrentUser = resolveExistingUser(
                        accessToken, normalizedUsername, normalizedEmail);
                if (concurrentUser != null) {
                    return new ProvisionedIdentity(concurrentUser.id(), false, null);
                }
                throw new IllegalStateException(
                        "A different Keycloak account already uses this username or email.", exception);
            }
            throw unavailable("Keycloak rejected user provisioning.", exception);
        } catch (ServiceDependencyUnavailableException | IllegalStateException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("Keycloak is unavailable, so the user was not created.", exception);
        }
    }

    @Override
    public void deleteUser(UUID keycloakUserId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{userId}", realm, keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw unavailable("Keycloak could not roll back the newly created user.", exception);
            }
        } catch (RestClientException exception) {
            throw unavailable("Keycloak could not roll back the newly created user.", exception);
        }
    }

    private String accessToken() {
        MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
        tokenRequest.add("grant_type", "client_credentials");
        tokenRequest.add("client_id", clientId);
        tokenRequest.add("client_secret", clientSecret);
        try {
            KeycloakTokenResponse tokenResponse = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenRequest)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new ServiceDependencyUnavailableException(
                        "Keycloak returned no access token for user provisioning.", null);
            }
            return tokenResponse.accessToken();
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("Keycloak provisioning authentication failed.", exception);
        }
    }

    private KeycloakUserRepresentation resolveExistingUser(String accessToken, String username, String email) {
        Map<UUID, KeycloakUserRepresentation> matchingUsers = new LinkedHashMap<>();
        exactUsers(accessToken, "username", username).forEach(user -> matchingUsers.put(user.id(), user));
        exactUsers(accessToken, "email", email).forEach(user -> matchingUsers.put(user.id(), user));
        if (matchingUsers.isEmpty()) {
            return null;
        }
        if (matchingUsers.size() > 1) {
            throw new IllegalStateException(
                    "The username and email belong to different Keycloak accounts.");
        }
        KeycloakUserRepresentation existingUser = matchingUsers.values().iterator().next();
        boolean usernameMatchesRealmPolicy = username.equalsIgnoreCase(existingUser.username())
                || email.equalsIgnoreCase(existingUser.username());
        if (!usernameMatchesRealmPolicy
                || !email.equalsIgnoreCase(existingUser.email())) {
            throw new IllegalStateException(
                    "A different Keycloak account already uses this username or email.");
        }
        return existingUser;
    }

    private List<KeycloakUserRepresentation> exactUsers(String accessToken, String field, String value) {
        try {
            List<KeycloakUserRepresentation> users = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/realms/{realm}/users")
                            .queryParam(field, value)
                            .queryParam("exact", true)
                            .build(realm))
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(KEYCLOAK_USERS_TYPE);
            return users == null ? List.of() : users;
        } catch (RestClientException exception) {
            throw unavailable("Keycloak user lookup failed.", exception);
        }
    }

    private String temporaryPassword() {
        List<Character> passwordCharacters = new ArrayList<>();
        passwordCharacters.add(randomCharacter(PASSWORD_UPPERCASE));
        passwordCharacters.add(randomCharacter(PASSWORD_LOWERCASE));
        passwordCharacters.add(randomCharacter(PASSWORD_DIGITS));
        passwordCharacters.add(randomCharacter(PASSWORD_SYMBOLS));
        while (passwordCharacters.size() < 20) {
            passwordCharacters.add(randomCharacter(PASSWORD_ALPHABET));
        }
        for (int index = passwordCharacters.size() - 1; index > 0; index--) {
            int swapIndex = SECURE_RANDOM.nextInt(index + 1);
            Character currentCharacter = passwordCharacters.get(index);
            passwordCharacters.set(index, passwordCharacters.get(swapIndex));
            passwordCharacters.set(swapIndex, currentCharacter);
        }
        StringBuilder password = new StringBuilder(passwordCharacters.size());
        passwordCharacters.forEach(password::append);
        return password.toString();
    }

    private Character randomCharacter(String alphabet) {
        return alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length()));
    }

    private UUID userIdFromLocation(URI location) {
        if (location == null || location.getPath() == null) {
            return null;
        }
        String path = location.getPath();
        int finalSeparator = path.lastIndexOf('/');
        if (finalSeparator < 0 || finalSeparator == path.length() - 1) {
            return null;
        }
        try {
            return UUID.fromString(path.substring(finalSeparator + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ServiceDependencyUnavailableException unavailable(String message, RestClientException exception) {
        return new ServiceDependencyUnavailableException(message, exception);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String removeTrailingSlash(String value) {
        String configuredValue = requireConfigured(value, "Keycloak provisioning server URL");
        return configuredValue.endsWith("/")
                ? configuredValue.substring(0, configuredValue.length() - 1)
                : configuredValue;
    }

    private static String requireConfigured(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " is not configured.");
        }
        return value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private record KeycloakTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken) {
    }

    private record KeycloakUserRepresentation(UUID id, String username, String email) {
    }
}

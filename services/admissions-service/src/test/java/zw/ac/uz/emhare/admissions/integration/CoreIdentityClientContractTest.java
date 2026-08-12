package zw.ac.uz.emhare.admissions.integration;

import zw.ac.uz.emhare.admissions.domain.model.Applicant;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;

class CoreIdentityClientContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCoreCurrentUserProfileWithBooleanOperationalAccess() throws Exception {
        String coreIdentityResponse = """
                {
                  "user": {
                    "id": "f8dca685-42c9-4f46-b7f8-eb9ca76b001b",
                    "keycloakUserId": "86212aac-c4a3-46a9-86cb-15b09f519147",
                    "username": "applicant@example.test",
                    "email": "applicant@example.test",
                    "displayName": "Portal Applicant",
                    "status": "ACTIVE"
                  },
                  "roleAssignments": [],
                  "realmRoles": ["applicant"],
                  "effectivePermissionCodes": [],
                  "operationalAccess": false
                }
                """;

        CoreCurrentUserProfile profile = objectMapper.readValue(
                coreIdentityResponse,
                CoreCurrentUserProfile.class);

        assertFalse(profile.operationalAccess());
    }
}

package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.UUID;

/**
 * @author Tinashe K
 */
public interface IdentityProvisioningPort {

  ProvisionedIdentity provisionUser(String username, String email, String displayName);

  void deleteUser(UUID keycloakUserId);

  void updateOfficialName(
      UUID keycloakUserId, String firstName, String middleNames, String lastName);

  record ProvisionedIdentity(UUID keycloakUserId, boolean created, String temporaryPassword) {}
}

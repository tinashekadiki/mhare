package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;

/** @author Tinashe K */
public record ProvisionedUserAccessSummary(
        CoreUserSummary user,
        List<UserRoleAssignmentSummary> roleAssignments,
        boolean keycloakIdentityCreated,
        String temporaryPassword) {
}

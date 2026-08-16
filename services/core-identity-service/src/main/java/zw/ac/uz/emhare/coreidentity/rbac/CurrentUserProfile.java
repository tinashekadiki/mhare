package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;
import java.util.Set;

public record CurrentUserProfile(
        CoreUserSummary user,
        List<UserRoleAssignmentSummary> roleAssignments,
        Set<String> realmRoles,
        Set<String> effectivePermissionCodes,
        boolean operationalAccess,
        String institutionBrandingJson) {
}

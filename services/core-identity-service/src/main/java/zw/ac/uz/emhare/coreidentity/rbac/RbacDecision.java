package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.UUID;

public record RbacDecision(UUID userId, UUID academicUnitId, String permissionCode, boolean allowed) {
}

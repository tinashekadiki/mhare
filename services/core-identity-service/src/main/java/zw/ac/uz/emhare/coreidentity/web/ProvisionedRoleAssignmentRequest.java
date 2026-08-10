package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record ProvisionedRoleAssignmentRequest(
        @NotNull UUID roleId,
        UUID academicUnitId,
        Instant startsAt) {
}

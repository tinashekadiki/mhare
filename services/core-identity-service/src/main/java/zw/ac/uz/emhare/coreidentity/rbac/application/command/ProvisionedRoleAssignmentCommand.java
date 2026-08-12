package zw.ac.uz.emhare.coreidentity.rbac.application.command;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record ProvisionedRoleAssignmentCommand(
        @NotNull UUID roleId,
        UUID academicUnitId,
        Instant startsAt) {
}

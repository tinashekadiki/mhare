package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AssignRoleRequest(
        @NotNull UUID roleId,
        UUID academicUnitId,
        Instant startsAt) {
}

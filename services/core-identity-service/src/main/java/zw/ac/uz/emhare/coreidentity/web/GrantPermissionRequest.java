package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GrantPermissionRequest(@NotNull UUID permissionId) {
}

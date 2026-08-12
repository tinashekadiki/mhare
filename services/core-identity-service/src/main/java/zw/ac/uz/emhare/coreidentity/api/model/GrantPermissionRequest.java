package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GrantPermissionRequest(@NotNull UUID permissionId) {
}

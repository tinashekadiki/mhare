package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.validation.constraints.NotBlank;

public record UpsertLookupValueCommand(
        @NotBlank String code,
        @NotBlank String name,
        int sortOrder,
        boolean active) {
}

package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.validation.constraints.NotBlank;

public record UpsertLookupSetCommand(@NotBlank String code, @NotBlank String name, String description) {
}

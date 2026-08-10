package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;

public record UpsertLookupSetRequest(@NotBlank String code, @NotBlank String name, String description) {
}

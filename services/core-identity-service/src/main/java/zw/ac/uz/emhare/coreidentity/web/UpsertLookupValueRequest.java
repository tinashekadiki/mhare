package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;

public record UpsertLookupValueRequest(@NotBlank String code, @NotBlank String name, int sortOrder, boolean active) {
}

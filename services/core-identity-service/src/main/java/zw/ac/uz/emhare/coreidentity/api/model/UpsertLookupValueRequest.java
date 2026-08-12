package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.NotBlank;

public record UpsertLookupValueRequest(@NotBlank String code, @NotBlank String name, int sortOrder, boolean active) {
}

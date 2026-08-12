package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.NotBlank;

public record UpsertLookupSetRequest(@NotBlank String code, @NotBlank String name, String description) {
}

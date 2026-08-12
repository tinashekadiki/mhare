package zw.ac.uz.emhare.coreidentity.rbac.application.command;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpsertCountryCommand(
        @Pattern(regexp = "^[A-Z]{2}$") String iso2Code,
        @Pattern(regexp = "^[A-Z]{3}$") String iso3Code,
        @NotBlank String name,
        @NotBlank String nationalityName) {
}

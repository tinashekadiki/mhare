package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpsertCountryRequest(
        @Pattern(regexp = "^[A-Z]{2}$") String iso2Code,
        @Pattern(regexp = "^[A-Z]{3}$") String iso3Code,
        @NotBlank String name,
        @NotBlank String nationalityName) {
}

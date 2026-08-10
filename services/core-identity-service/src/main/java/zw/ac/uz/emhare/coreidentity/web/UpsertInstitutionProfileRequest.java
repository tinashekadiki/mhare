package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpsertInstitutionProfileRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String legalName,
        @Pattern(regexp = "^[A-Z]{3}$") String defaultCurrencyCode,
        @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @NotBlank String timezone,
        String contactDetailsJson,
        String brandingJson,
        String legacyCode) {
}

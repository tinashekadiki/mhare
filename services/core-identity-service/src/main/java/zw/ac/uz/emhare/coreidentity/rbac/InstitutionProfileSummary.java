package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.InstitutionProfile;

import java.util.UUID;

public record InstitutionProfileSummary(
        UUID id,
        String code,
        String name,
        String legalName,
        String defaultCurrencyCode,
        String countryCode,
        String timezone,
        String contactDetailsJson,
        String brandingJson,
        String legacyCode) {
    static InstitutionProfileSummary from(InstitutionProfile profile) {
        return new InstitutionProfileSummary(
                profile.getId(),
                profile.getCode(),
                profile.getName(),
                profile.getLegalName(),
                profile.getDefaultCurrencyCode(),
                profile.getCountryCode(),
                profile.getTimezone(),
                profile.getContactDetailsJson(),
                profile.getBrandingJson(),
                profile.getLegacyCode());
    }
}

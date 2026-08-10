package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.UUID;

public record CountrySummary(UUID id, String iso2Code, String iso3Code, String name, String nationalityName) {
    static CountrySummary from(Country country) {
        return new CountrySummary(
                country.getId(),
                country.getIso2Code(),
                country.getIso3Code(),
                country.getName(),
                country.getNationalityName());
    }
}

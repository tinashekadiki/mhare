package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupValue;

import java.util.UUID;

public record LookupValueSummary(
        UUID id,
        UUID lookupSetId,
        String lookupSetCode,
        String code,
        String name,
        int sortOrder,
        boolean active) {
    static LookupValueSummary from(LookupValue lookupValue) {
        return new LookupValueSummary(
                lookupValue.getId(),
                lookupValue.getLookupSet().getId(),
                lookupValue.getLookupSet().getCode(),
                lookupValue.getCode(),
                lookupValue.getName(),
                lookupValue.getSortOrder(),
                lookupValue.isActive());
    }
}

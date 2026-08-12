package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupSet;

import java.util.UUID;

public record LookupSetSummary(UUID id, String code, String name, String description) {
    static LookupSetSummary from(LookupSet lookupSet) {
        return new LookupSetSummary(lookupSet.getId(), lookupSet.getCode(), lookupSet.getName(), lookupSet.getDescription());
    }
}

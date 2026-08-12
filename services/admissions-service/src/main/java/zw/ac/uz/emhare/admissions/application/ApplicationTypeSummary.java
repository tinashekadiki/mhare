package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;

import java.util.UUID;

/** @author Tinashe K */
public record ApplicationTypeSummary(
        UUID id,
        String code,
        String name,
        boolean requiresEmploymentHistory,
        boolean requiresReferees,
        UUID financeFeeStructureId,
        String financeFeeStructureCode,
        String financeFeeStructureName,
        boolean active,
        long version) {

    static ApplicationTypeSummary from(ApplicationType applicationType) {
        return new ApplicationTypeSummary(
                applicationType.getId(),
                applicationType.getCode(),
                applicationType.getName(),
                applicationType.requiresEmploymentHistory(),
                applicationType.requiresReferees(),
                applicationType.getFinanceFeeStructureId(),
                applicationType.getFinanceFeeStructureCode(),
                applicationType.getFinanceFeeStructureName(),
                applicationType.isActive(),
                applicationType.getVersion());
    }
}

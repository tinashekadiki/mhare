package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

/** @author Tinashe K */
public record AdmissionRequirementSetSummary(
        UUID id,
        UUID programmeId,
        UUID applicationTypeId,
        UUID intakeId,
        String versionCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        BigDecimal minimumTotalPoints,
        boolean requiresEnglish,
        boolean requiresMathematicsOrScience,
        String advancedRulesVersion,
        Instant approvedAt,
        List<QualificationRequirementGroupSummary> qualificationGroups) {

    static AdmissionRequirementSetSummary from(AdmissionRequirementSet requirementSet) {
        return new AdmissionRequirementSetSummary(
                requirementSet.getId(), requirementSet.getProgrammeId(), requirementSet.getApplicationType().getId(),
                requirementSet.getAdmissionCycle() == null ? null : requirementSet.getAdmissionCycle().getIntakeId(),
                requirementSet.getVersionCode(), requirementSet.getEffectiveFrom(), requirementSet.getEffectiveTo(),
                requirementSet.getStatus().name(), requirementSet.getMinimumTotalPoints(),
                requirementSet.isRequiresEnglish(), requirementSet.isRequiresMathematicsOrScience(),
                requirementSet.getAdvancedRulesVersion(), requirementSet.getApprovedAt(), List.of());
    }

    AdmissionRequirementSetSummary withQualificationGroups(List<QualificationRequirementGroupSummary> groups) {
        return new AdmissionRequirementSetSummary(id, programmeId, applicationTypeId, intakeId, versionCode,
                effectiveFrom, effectiveTo, status, minimumTotalPoints, requiresEnglish,
                requiresMathematicsOrScience, advancedRulesVersion, approvedAt, List.copyOf(groups));
    }

    public record QualificationRequirementGroupSummary(
            UUID id, String code, String name, int minimumSatisfiedItems, int sortOrder,
            List<QualificationRequirementItemSummary> items) { }

    public record QualificationRequirementItemSummary(
            UUID id, String qualificationLevel, int minimumCount,
            BigDecimal minimumTotalPoints, Integer minimumDurationMonths, int sortOrder) { }
}

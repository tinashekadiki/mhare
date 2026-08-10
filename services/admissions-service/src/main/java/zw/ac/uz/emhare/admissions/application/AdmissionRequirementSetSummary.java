package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
        Instant approvedAt) {

    static AdmissionRequirementSetSummary from(AdmissionRequirementSet requirementSet) {
        return new AdmissionRequirementSetSummary(
                requirementSet.getId(), requirementSet.getProgrammeId(), requirementSet.getApplicationType().getId(),
                requirementSet.getAdmissionCycle() == null ? null : requirementSet.getAdmissionCycle().getIntakeId(),
                requirementSet.getVersionCode(), requirementSet.getEffectiveFrom(), requirementSet.getEffectiveTo(),
                requirementSet.getStatus().name(), requirementSet.getMinimumTotalPoints(),
                requirementSet.isRequiresEnglish(), requirementSet.isRequiresMathematicsOrScience(),
                requirementSet.getAdvancedRulesVersion(), requirementSet.getApprovedAt());
    }
}

package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** @author Tinashe K */
public record CreateAdmissionRequirementSetRequest(
        @NotNull UUID programmeId,
        @NotNull UUID applicationTypeId,
        UUID intakeId,
        @NotBlank @Size(max = 50) String versionCode,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @PositiveOrZero BigDecimal minimumTotalPoints,
        @PositiveOrZero BigDecimal maleCutoffPoints,
        @PositiveOrZero BigDecimal femaleCutoffPoints,
        boolean requiresEnglish,
        boolean requiresMathematicsOrScience,
        Map<String, Object> advancedRules,
        @Size(max = 30) String advancedRulesVersion) {
}

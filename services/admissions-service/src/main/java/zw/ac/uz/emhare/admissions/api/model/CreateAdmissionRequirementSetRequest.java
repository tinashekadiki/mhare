package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
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
        @Size(max = 30) String advancedRulesVersion,
        @Size(max = 20) List<@jakarta.validation.Valid QualificationRequirementGroupInput> qualificationGroups) {

    public record QualificationRequirementGroupInput(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 160) String name,
            @jakarta.validation.constraints.Positive int minimumSatisfiedItems,
            int sortOrder,
            @NotNull @Size(min = 1, max = 20) List<@jakarta.validation.Valid QualificationRequirementItemInput> items) { }

    public record QualificationRequirementItemInput(
            @NotBlank @Size(max = 30) String qualificationLevel,
            @jakarta.validation.constraints.Positive int minimumCount,
            @PositiveOrZero BigDecimal minimumTotalPoints,
            @PositiveOrZero Integer minimumDurationMonths,
            int sortOrder) { }
}

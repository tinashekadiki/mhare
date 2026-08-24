package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.*;

/**
 * @author Tinashe K
 */
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
    boolean requiresMathematics,
    boolean requiresScience,
    boolean requiresMathematicsOrScience,
    Map<String, Object> advancedRules,
    @Size(max = 30) String advancedRulesVersion,
    @Size(max = 50) List<@jakarta.validation.Valid SubjectRequirementInput> subjectRequirements,
    @Size(max = 20) List<@jakarta.validation.Valid QualificationRequirementGroupInput> qualificationGroups) {

  public record SubjectRequirementInput(
      @NotBlank @Size(max = 30) String level,
      UUID subjectId,
      @Size(max = 50) String subjectGroupCode,
      @NotBlank @Size(max = 30) String requirementType,
      @Size(max = 20) String minimumGrade,
      @PositiveOrZero BigDecimal minimumPoints,
      @PositiveOrZero Integer minimumCount,
      @PositiveOrZero BigDecimal weight,
      int sortOrder) {}

  public record QualificationRequirementGroupInput(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 160) String name,
      @jakarta.validation.constraints.Positive int minimumSatisfiedItems,
      int sortOrder,
      @NotNull @Size(min = 1, max = 20) List<@jakarta.validation.Valid QualificationRequirementItemInput> items) {}

  public record QualificationRequirementItemInput(
      @NotBlank @Size(max = 30) String qualificationLevel,
      @jakarta.validation.constraints.Positive int minimumCount,
      @PositiveOrZero BigDecimal minimumTotalPoints,
      @PositiveOrZero Integer minimumDurationMonths,
      int sortOrder) {}
}

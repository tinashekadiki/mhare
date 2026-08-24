package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;

/**
 * @author Tinashe K
 */
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
    BigDecimal maleCutoffPoints,
    BigDecimal femaleCutoffPoints,
    boolean requiresEnglish,
    boolean requiresMathematics,
    boolean requiresScience,
    boolean requiresMathematicsOrScience,
    String advancedRulesVersion,
    Instant approvedAt,
    List<SubjectRequirementSummary> subjectRequirements,
    List<QualificationRequirementGroupSummary> qualificationGroups) {

  static AdmissionRequirementSetSummary from(AdmissionRequirementSet requirementSet) {
    return new AdmissionRequirementSetSummary(
        requirementSet.getId(),
        requirementSet.getProgrammeId(),
        requirementSet.getApplicationType().getId(),
        requirementSet.getIntakeId(),
        requirementSet.getVersionCode(),
        requirementSet.getEffectiveFrom(),
        requirementSet.getEffectiveTo(),
        requirementSet.getStatus().name(),
        requirementSet.getMinimumTotalPoints(),
        requirementSet.getMaleCutoffPoints(),
        requirementSet.getFemaleCutoffPoints(),
        requirementSet.isRequiresEnglish(),
        requirementSet.isRequiresMathematics(),
        requirementSet.isRequiresScience(),
        requirementSet.isRequiresMathematicsOrScience(),
        requirementSet.getAdvancedRulesVersion(),
        requirementSet.getApprovedAt(),
        List.of(),
        List.of());
  }

  AdmissionRequirementSetSummary withRequirements(
      List<SubjectRequirementSummary> subjects, List<QualificationRequirementGroupSummary> groups) {
    return new AdmissionRequirementSetSummary(
        id,
        programmeId,
        applicationTypeId,
        intakeId,
        versionCode,
        effectiveFrom,
        effectiveTo,
        status,
        minimumTotalPoints,
        maleCutoffPoints,
        femaleCutoffPoints,
        requiresEnglish,
        requiresMathematics,
        requiresScience,
        requiresMathematicsOrScience,
        advancedRulesVersion,
        approvedAt,
        List.copyOf(subjects),
        List.copyOf(groups));
  }

  public record SubjectRequirementSummary(
      UUID id,
      String level,
      UUID subjectId,
      String subjectGroupCode,
      String requirementType,
      String minimumGrade,
      BigDecimal minimumPoints,
      Integer minimumCount,
      BigDecimal weight,
      int sortOrder) {}

  public record QualificationRequirementGroupSummary(
      UUID id,
      String code,
      String name,
      int minimumSatisfiedItems,
      int sortOrder,
      List<QualificationRequirementItemSummary> items) {}

  public record QualificationRequirementItemSummary(
      UUID id,
      String qualificationLevel,
      int minimumCount,
      BigDecimal minimumTotalPoints,
      Integer minimumDurationMonths,
      int sortOrder) {}
}

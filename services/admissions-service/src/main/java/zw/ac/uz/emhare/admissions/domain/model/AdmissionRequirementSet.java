package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "admission_requirement_sets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_requirement_set_version",
            columnNames = {"programme_id", "application_type_id", "intake_id", "version_code"}))
public class AdmissionRequirementSet extends AuditableEntity {

  @Column(name = "programme_id", nullable = false)
  private UUID programmeId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_type_id", nullable = false)
  private ApplicationType applicationType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "admission_cycle_id")
  private AdmissionCycle admissionCycle;

  @Column(name = "intake_id")
  private UUID intakeId;

  @Column(name = "version_code", nullable = false, length = 50)
  private String versionCode;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RequirementSetStatus status;

  @Column(name = "minimum_total_points", precision = 8, scale = 2)
  private BigDecimal minimumTotalPoints;

  @Column(name = "male_cutoff_points", precision = 8, scale = 2)
  private BigDecimal maleCutoffPoints;

  @Column(name = "female_cutoff_points", precision = 8, scale = 2)
  private BigDecimal femaleCutoffPoints;

  @Column(name = "requires_english", nullable = false)
  private boolean requiresEnglish;

  @Column(name = "requires_mathematics_or_science", nullable = false)
  private boolean requiresMathematicsOrScience;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "advanced_rules_json", columnDefinition = "jsonb")
  private String advancedRulesJson;

  @Column(name = "advanced_rules_version", length = 30)
  private String advancedRulesVersion;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  protected AdmissionRequirementSet() {}

  public AdmissionRequirementSet(
      UUID programmeId,
      ApplicationType applicationType,
      AdmissionCycle admissionCycle,
      String versionCode,
      LocalDate effectiveFrom) {
    this.programmeId = programmeId;
    this.applicationType = applicationType;
    this.admissionCycle = admissionCycle;
    this.intakeId = admissionCycle == null ? null : admissionCycle.getIntakeId();
    this.versionCode = versionCode;
    this.effectiveFrom = effectiveFrom;
    this.status = RequirementSetStatus.DRAFT;
  }

  public AdmissionRequirementSet(
      UUID programmeId,
      ApplicationType applicationType,
      UUID intakeId,
      String versionCode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      BigDecimal minimumTotalPoints,
      BigDecimal maleCutoffPoints,
      BigDecimal femaleCutoffPoints,
      boolean requiresEnglish,
      boolean requiresMathematicsOrScience,
      String advancedRulesJson,
      String advancedRulesVersion) {
    this.programmeId = programmeId;
    this.applicationType = applicationType;
    this.intakeId = intakeId;
    this.versionCode = versionCode;
    this.effectiveFrom = effectiveFrom;
    this.status = RequirementSetStatus.DRAFT;
    if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException(
          "Requirement-set effective end date cannot precede its start date.");
    }
    if ((advancedRulesJson == null) != (advancedRulesVersion == null)) {
      throw new IllegalArgumentException(
          "Advanced rules and their schema version must be supplied together.");
    }
    this.effectiveTo = effectiveTo;
    this.minimumTotalPoints = minimumTotalPoints;
    this.maleCutoffPoints = maleCutoffPoints;
    this.femaleCutoffPoints = femaleCutoffPoints;
    this.requiresEnglish = requiresEnglish;
    this.requiresMathematicsOrScience = requiresMathematicsOrScience;
    this.advancedRulesJson = advancedRulesJson;
    this.advancedRulesVersion = advancedRulesVersion;
  }

  public AdmissionRequirementSet(
      UUID programmeId,
      ApplicationType applicationType,
      AdmissionCycle admissionCycle,
      String versionCode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      BigDecimal minimumTotalPoints,
      BigDecimal maleCutoffPoints,
      BigDecimal femaleCutoffPoints,
      boolean requiresEnglish,
      boolean requiresMathematicsOrScience,
      String advancedRulesJson,
      String advancedRulesVersion) {
    this(programmeId, applicationType, admissionCycle, versionCode, effectiveFrom);
    if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException(
          "Requirement-set effective end date cannot precede its start date.");
    }
    if ((advancedRulesJson == null) != (advancedRulesVersion == null)) {
      throw new IllegalArgumentException(
          "Advanced rules and their schema version must be supplied together.");
    }
    this.effectiveTo = effectiveTo;
    this.minimumTotalPoints = minimumTotalPoints;
    this.maleCutoffPoints = maleCutoffPoints;
    this.femaleCutoffPoints = femaleCutoffPoints;
    this.requiresEnglish = requiresEnglish;
    this.requiresMathematicsOrScience = requiresMathematicsOrScience;
    this.advancedRulesJson = advancedRulesJson;
    this.advancedRulesVersion = advancedRulesVersion;
  }

  public void approve(UUID actorUserId, Instant now) {
    if (status != RequirementSetStatus.DRAFT) {
      throw new IllegalStateException("Only a draft admission requirement set can be approved.");
    }
    status = RequirementSetStatus.APPROVED;
    approvedByUserId = actorUserId;
    approvedAt = now;
  }

  public void retire() {
    if (status != RequirementSetStatus.APPROVED) {
      throw new IllegalStateException("Only an approved admission requirement set can be retired.");
    }
    status = RequirementSetStatus.RETIRED;
  }

  public boolean overlapsEffectivePeriod(AdmissionRequirementSet other) {
    LocalDate thisEnd = effectiveTo == null ? LocalDate.MAX : effectiveTo;
    LocalDate otherEnd = other.effectiveTo == null ? LocalDate.MAX : other.effectiveTo;
    return !effectiveFrom.isAfter(otherEnd) && !other.effectiveFrom.isAfter(thisEnd);
  }

  public boolean isApprovedAndEffectiveFor(
      UUID selectedProgrammeId, UUID applicationTypeId, UUID intakeId, LocalDate effectiveDate) {
    return status == RequirementSetStatus.APPROVED
        && programmeId.equals(selectedProgrammeId)
        && applicationType.getId().equals(applicationTypeId)
        && (this.intakeId == null || this.intakeId.equals(intakeId))
        && !effectiveFrom.isAfter(effectiveDate)
        && (effectiveTo == null || !effectiveTo.isBefore(effectiveDate));
  }

  public String getVersionCode() {
    return versionCode;
  }

  public UUID getProgrammeId() {
    return programmeId;
  }

  public ApplicationType getApplicationType() {
    return applicationType;
  }

  public AdmissionCycle getAdmissionCycle() {
    return admissionCycle;
  }

  public UUID getIntakeId() {
    return intakeId;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public RequirementSetStatus getStatus() {
    return status;
  }

  public BigDecimal getMinimumTotalPoints() {
    return minimumTotalPoints;
  }

  public BigDecimal getMaleCutoffPoints() {
    return maleCutoffPoints;
  }

  public BigDecimal getFemaleCutoffPoints() {
    return femaleCutoffPoints;
  }

  public boolean isRequiresEnglish() {
    return requiresEnglish;
  }

  public boolean isRequiresMathematicsOrScience() {
    return requiresMathematicsOrScience;
  }

  public String getAdvancedRulesVersion() {
    return advancedRulesVersion;
  }

  public String getAdvancedRulesJson() {
    return advancedRulesJson;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }
}

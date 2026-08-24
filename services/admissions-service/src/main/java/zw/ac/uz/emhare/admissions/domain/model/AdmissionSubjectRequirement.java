package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "admission_subject_requirements")
public class AdmissionSubjectRequirement extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requirement_set_id", nullable = false)
  private AdmissionRequirementSet requirementSet;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SubjectLevel level;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subject_id")
  private AdmissionSubject subject;

  @Column(name = "subject_group_code", length = 50)
  private String subjectGroupCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_type", nullable = false, length = 30)
  private SubjectRequirementType requirementType;

  @Column(name = "minimum_grade", length = 20)
  private String minimumGrade;

  @Column(name = "minimum_points", precision = 8, scale = 2)
  private BigDecimal minimumPoints;

  @Column(name = "minimum_count")
  private Integer minimumCount;

  @Column(precision = 8, scale = 2)
  private BigDecimal weight;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected AdmissionSubjectRequirement() {}

  public AdmissionSubjectRequirement(
      AdmissionRequirementSet requirementSet,
      SubjectLevel level,
      SubjectRequirementType requirementType,
      int sortOrder) {
    this.requirementSet = requirementSet;
    this.level = level;
    this.requirementType = requirementType;
    this.sortOrder = sortOrder;
  }

  public AdmissionSubjectRequirement(
      AdmissionRequirementSet requirementSet,
      SubjectLevel level,
      AdmissionSubject subject,
      String subjectGroupCode,
      SubjectRequirementType requirementType,
      String minimumGrade,
      BigDecimal minimumPoints,
      Integer minimumCount,
      BigDecimal weight,
      int sortOrder) {
    if (subject == null && (subjectGroupCode == null || subjectGroupCode.isBlank())) {
      throw new IllegalArgumentException("A subject or subject group is required.");
    }
    if (subject != null && subject.getLevel() != level) {
      throw new IllegalArgumentException("The subject level must match the requirement level.");
    }
    this.requirementSet = requirementSet;
    this.level = level;
    this.subject = subject;
    this.subjectGroupCode = optional(subjectGroupCode);
    this.requirementType = requirementType;
    this.minimumGrade = optional(minimumGrade);
    this.minimumPoints = minimumPoints;
    this.minimumCount = minimumCount;
    this.weight = weight;
    this.sortOrder = sortOrder;
  }

  public AdmissionRequirementSet getRequirementSet() {
    return requirementSet;
  }

  public SubjectLevel getLevel() {
    return level;
  }

  public AdmissionSubject getSubject() {
    return subject;
  }

  public String getSubjectGroupCode() {
    return subjectGroupCode;
  }

  public SubjectRequirementType getRequirementType() {
    return requirementType;
  }

  public String getMinimumGrade() {
    return minimumGrade;
  }

  public BigDecimal getMinimumPoints() {
    return minimumPoints;
  }

  public Integer getMinimumCount() {
    return minimumCount;
  }

  public BigDecimal getWeight() {
    return weight;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  private static String optional(String value) {
    return value == null || value.isBlank()
        ? null
        : value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}

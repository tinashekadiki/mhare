package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "admission_subjects",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_admission_subjects_level_code",
            columnNames = {"level", "code"}))
public class AdmissionSubject extends AuditableEntity {

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SubjectLevel level;

  @Column(name = "subject_group_code", length = 50)
  private String subjectGroupCode;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "is_science_subject", nullable = false)
  private boolean scienceSubject;

  @Column(name = "legacy_olevel_subject_code", length = 50)
  private String legacyOlevelSubjectCode;

  @Column(name = "legacy_subject_code", length = 50)
  private String legacySubjectCode;

  protected AdmissionSubject() {}

  public AdmissionSubject(
      String code,
      String name,
      SubjectLevel level,
      String subjectGroupCode,
      boolean scienceSubject) {
    this.code = code;
    this.name = name;
    this.level = level;
    this.subjectGroupCode = subjectGroupCode;
    this.scienceSubject = scienceSubject;
    this.active = true;
  }

  public AdmissionSubject(String code, String name, SubjectLevel level, String subjectGroupCode) {
    this(code, name, level, subjectGroupCode, false);
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public SubjectLevel getLevel() {
    return level;
  }

  public String getSubjectGroupCode() {
    return subjectGroupCode;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isScienceSubject() {
    return scienceSubject;
  }

  public boolean isMathematicsSubject() {
    return "MATHEMATICS".equalsIgnoreCase(subjectGroupCode);
  }

  public boolean isEnglishSubject() {
    return "ENGLISH".equalsIgnoreCase(subjectGroupCode);
  }

  public void updateReference(
      String code, String name, String subjectGroupCode, boolean scienceSubject, boolean active) {
    this.code = code;
    this.name = name;
    this.subjectGroupCode = subjectGroupCode;
    this.scienceSubject = scienceSubject;
    this.active = active;
  }
}

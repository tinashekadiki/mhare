package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "applicant_employment_histories")
public class ApplicantEmploymentHistory extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_id", nullable = false)
  private Applicant applicant;

  @Column(name = "employer_name", nullable = false, length = 200)
  private String employerName;

  @Column(name = "position_title", nullable = false, length = 150)
  private String positionTitle;

  @Column(name = "started_on", nullable = false)
  private LocalDate startedOn;

  @Column(name = "ended_on")
  private LocalDate endedOn;

  @Column(name = "is_current", nullable = false)
  private boolean current;

  @Column(length = 2000)
  private String responsibilities;

  protected ApplicantEmploymentHistory() {}

  public ApplicantEmploymentHistory(
      Applicant applicant,
      String employerName,
      String positionTitle,
      LocalDate startedOn,
      LocalDate endedOn,
      boolean current,
      String responsibilities) {
    this.applicant = applicant;
    update(employerName, positionTitle, startedOn, endedOn, current, responsibilities);
  }

  public void update(
      String employerName,
      String positionTitle,
      LocalDate startedOn,
      LocalDate endedOn,
      boolean current,
      String responsibilities) {
    if (startedOn == null) throw new IllegalArgumentException("Employment start date is required.");
    if (endedOn != null && endedOn.isBefore(startedOn)) {
      throw new IllegalArgumentException("Employment end date cannot be before the start date.");
    }
    if (current && endedOn != null) {
      throw new IllegalArgumentException("Current employment cannot have an end date.");
    }
    this.employerName = required(employerName, "Employer name");
    this.positionTitle = required(positionTitle, "Position title");
    this.startedOn = startedOn;
    this.endedOn = current ? null : endedOn;
    this.current = current;
    this.responsibilities = optional(responsibilities);
  }

  public Applicant getApplicant() {
    return applicant;
  }

  public String getEmployerName() {
    return employerName;
  }

  public String getPositionTitle() {
    return positionTitle;
  }

  public LocalDate getStartedOn() {
    return startedOn;
  }

  public LocalDate getEndedOn() {
    return endedOn;
  }

  public boolean isCurrent() {
    return current;
  }

  public String getResponsibilities() {
    return responsibilities;
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required.");
    return value.trim();
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

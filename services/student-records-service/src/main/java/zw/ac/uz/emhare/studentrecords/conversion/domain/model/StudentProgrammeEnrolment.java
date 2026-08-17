package zw.ac.uz.emhare.studentrecords.conversion.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.studentrecords.conversion.*;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "student_programme_enrolments")
public class StudentProgrammeEnrolment extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  private StudentProfile student;

  @Column(name = "source_offer_id", nullable = false)
  private UUID sourceOfferId;

  @Column(name = "source_programme_choice_id", nullable = false)
  private UUID sourceProgrammeChoiceId;

  @Column(name = "programme_id", nullable = false)
  private UUID programmeId;

  @Column(name = "programme_version_id", nullable = false)
  private UUID programmeVersionId;

  @Column(name = "programme_code", nullable = false, length = 50)
  private String programmeCode;

  @Column(name = "programme_name", nullable = false, length = 200)
  private String programmeName;

  @Column(name = "intake_id", nullable = false)
  private UUID intakeId;

  @Column(name = "commencement_date", nullable = false)
  private LocalDate commencementDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ProgrammeEnrolmentStatus status;

  @Column(name = "status_reason", nullable = false, length = 1000)
  private String statusReason;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  protected StudentProgrammeEnrolment() {}

  public StudentProgrammeEnrolment(
      StudentProfile student, AcceptedOfferReadyForConversionEvent event) {
    this.student = student;
    this.sourceOfferId = event.offerId();
    this.sourceProgrammeChoiceId = event.programmeChoiceId();
    this.programmeId = event.programmeId();
    this.programmeVersionId = event.programmeVersionId();
    this.programmeCode = event.programmeCode();
    this.programmeName = event.programmeName();
    this.intakeId = event.intakeId();
    this.commencementDate = event.commencementDate();
    this.status = ProgrammeEnrolmentStatus.PROVISIONING;
    this.statusReason = "Created from accepted offer " + event.offerNumber() + ".";
  }

  public void activate(Instant now) {
    if (status != ProgrammeEnrolmentStatus.PROVISIONING) {
      throw new IllegalStateException("Only a provisioning programme enrolment can be activated.");
    }
    status = ProgrammeEnrolmentStatus.ACTIVE;
    statusReason = "Finance account and student portal access provisioned.";
    approvedAt = now;
  }

  public StudentProfile getStudent() {
    return student;
  }

  public UUID getProgrammeId() {
    return programmeId;
  }

  public UUID getProgrammeVersionId() {
    return programmeVersionId;
  }

  public String getProgrammeCode() {
    return programmeCode;
  }

  public String getProgrammeName() {
    return programmeName;
  }

  public UUID getIntakeId() {
    return intakeId;
  }

  public LocalDate getCommencementDate() {
    return commencementDate;
  }

  public ProgrammeEnrolmentStatus getStatus() {
    return status;
  }

  public String getStatusReason() {
    return statusReason;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public boolean isActive() {
    return status == ProgrammeEnrolmentStatus.ACTIVE;
  }
}

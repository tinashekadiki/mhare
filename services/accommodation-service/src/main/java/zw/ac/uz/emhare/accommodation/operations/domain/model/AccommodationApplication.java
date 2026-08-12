package zw.ac.uz.emhare.accommodation.operations.domain.model;

import zw.ac.uz.emhare.accommodation.operations.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoomType;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_applications")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationApplication extends AuditableEntity {
    public enum PaymentState { PAID, WAIVED, PART_PAID, UNPAID, UNKNOWN }
    public enum Status { SUBMITTED, ELIGIBLE, WAITLISTED, ALLOCATED, REJECTED, WITHDRAWN }

    @Column(name = "application_number", nullable = false, length = 60) private String applicationNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_period_id")
    private AccommodationApplicationPeriod applicationPeriod;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40) private String studentNumber;
    @Column(name = "student_name", nullable = false, length = 200) private String studentName;
    @Column(name = "primary_email", nullable = false, length = 254) private String primaryEmail;
    @Column(name = "gender_code", nullable = false, length = 20) private String genderCode;
    @Column(name = "disability_code", length = 80) private String disabilityCode;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 3, columnDefinition = "char(3)") private String countryCode;
    @Column(name = "location_code", length = 80) private String locationCode;
    @Column(name = "programme_id", nullable = false) private UUID programmeId;
    @Column(name = "programme_code", nullable = false, length = 50) private String programmeCode;
    @Column(name = "programme_name", nullable = false, length = 200) private String programmeName;
    @Column(name = "programme_level", nullable = false) private int programmeLevel;
    @Column(name = "sponsor_code", length = 80) private String sponsorCode;
    @Enumerated(EnumType.STRING) @Column(name = "payment_state", nullable = false, length = 30) private PaymentState paymentState;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_room_type_id")
    private AccommodationRoomType preferredRoomType;
    @Column(name = "special_requirements", length = 1000) private String specialRequirements;
    @Column(name = "priority_score", nullable = false) private int priorityScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(name = "submitted_at", nullable = false) private Instant submittedAt;
    @Column(name = "evaluated_by_user_id") private UUID evaluatedByUserId;
    @Column(name = "evaluated_at") private Instant evaluatedAt;
    @Column(name = "evaluation_reason", length = 1000) private String evaluationReason;
    @Column(name = "selected_group_id") private UUID selectedGroupId;
    @Column(name = "withdrawn_by_user_id") private UUID withdrawnByUserId;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "withdrawal_reason", length = 1000) private String withdrawalReason;

    protected AccommodationApplication() {}

    public AccommodationApplication(String applicationNumber, AccommodationApplicationPeriod applicationPeriod,
            UUID studentId, String studentNumber, String studentName, String primaryEmail, String genderCode,
            String disabilityCode, String countryCode, String locationCode, UUID programmeId,
            String programmeCode, String programmeName, int programmeLevel, String sponsorCode,
            PaymentState paymentState, AccommodationRoomType preferredRoomType, String specialRequirements,
            Instant submittedAt) {
        if (applicationPeriod == null || studentId == null || programmeId == null || submittedAt == null) {
            throw new IllegalArgumentException("Period, student, programme, and submission time are required.");
        }
        if (applicationPeriod.getStatus() != AccommodationApplicationPeriod.Status.APPLICATION_OPEN
                || submittedAt.isBefore(applicationPeriod.getApplicationsOpenAt())
                || submittedAt.isAfter(applicationPeriod.getApplicationsCloseAt())) {
            throw new IllegalStateException("The accommodation application period is not open.");
        }
        if (programmeLevel < 1) throw new IllegalArgumentException("Programme level must be positive.");
        this.applicationNumber = AccommodationValueRules.code(applicationNumber, "Application number");
        this.applicationPeriod = applicationPeriod;
        this.studentId = studentId;
        this.studentNumber = AccommodationValueRules.code(studentNumber, "Student number");
        this.studentName = AccommodationValueRules.required(studentName, "Student name");
        this.primaryEmail = AccommodationValueRules.required(primaryEmail, "Primary email").toLowerCase();
        this.genderCode = AccommodationValueRules.code(genderCode, "Gender code");
        this.disabilityCode = AccommodationValueRules.optional(disabilityCode);
        this.countryCode = AccommodationValueRules.code(countryCode, "Country code");
        this.locationCode = AccommodationValueRules.optional(locationCode);
        this.programmeId = programmeId;
        this.programmeCode = AccommodationValueRules.code(programmeCode, "Programme code");
        this.programmeName = AccommodationValueRules.required(programmeName, "Programme name");
        this.programmeLevel = programmeLevel;
        this.sponsorCode = AccommodationValueRules.optional(sponsorCode);
        this.paymentState = paymentState == null ? PaymentState.UNKNOWN : paymentState;
        this.preferredRoomType = preferredRoomType;
        this.specialRequirements = AccommodationValueRules.optional(specialRequirements);
        this.priorityScore = 0;
        this.status = Status.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    public void evaluate(Status outcome, int score, UUID selectedGroupId, String reason,
            UUID actorUserId, Instant occurredAt, long expectedVersion) {
        AccommodationValueRules.requireVersion(getVersion(), expectedVersion, "Accommodation application");
        if (status != Status.SUBMITTED) throw new IllegalStateException("Only submitted applications can be evaluated.");
        if (outcome != Status.ELIGIBLE && outcome != Status.WAITLISTED && outcome != Status.REJECTED) {
            throw new IllegalArgumentException("Evaluation outcome must be eligible, waitlisted, or rejected.");
        }
        if (actorUserId == null || occurredAt == null) throw new IllegalArgumentException("Evaluating operator and time are required.");
        priorityScore = score;
        this.selectedGroupId = selectedGroupId;
        evaluatedByUserId = actorUserId;
        evaluatedAt = occurredAt;
        evaluationReason = AccommodationValueRules.required(reason, "Evaluation reason");
        status = outcome;
    }

    public void markAllocated() {
        if (status != Status.ELIGIBLE && status != Status.WAITLISTED) {
            throw new IllegalStateException("Only eligible or waitlisted applications can be allocated.");
        }
        status = Status.ALLOCATED;
    }

    public void withdraw(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        AccommodationValueRules.requireVersion(getVersion(), expectedVersion, "Accommodation application");
        if (status == Status.ALLOCATED || status == Status.WITHDRAWN || status == Status.REJECTED) {
            throw new IllegalStateException("This accommodation application cannot be withdrawn in its current state.");
        }
        withdrawnByUserId = actorUserId;
        withdrawnAt = occurredAt;
        withdrawalReason = AccommodationValueRules.required(reason, "Withdrawal reason");
        status = Status.WITHDRAWN;
    }

    public String getApplicationNumber() { return applicationNumber; }
    public AccommodationApplicationPeriod getApplicationPeriod() { return applicationPeriod; }
    public UUID getStudentId() { return studentId; }
    public String getStudentNumber() { return studentNumber; }
    public String getStudentName() { return studentName; }
    public String getPrimaryEmail() { return primaryEmail; }
    public String getGenderCode() { return genderCode; }
    public String getDisabilityCode() { return disabilityCode; }
    public String getCountryCode() { return countryCode; }
    public String getLocationCode() { return locationCode; }
    public UUID getProgrammeId() { return programmeId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public int getProgrammeLevel() { return programmeLevel; }
    public String getSponsorCode() { return sponsorCode; }
    public PaymentState getPaymentState() { return paymentState; }
    public AccommodationRoomType getPreferredRoomType() { return preferredRoomType; }
    public String getSpecialRequirements() { return specialRequirements; }
    public int getPriorityScore() { return priorityScore; }
    public Status getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getEvaluatedByUserId() { return evaluatedByUserId; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public String getEvaluationReason() { return evaluationReason; }
    public UUID getSelectedGroupId() { return selectedGroupId; }
    public UUID getWithdrawnByUserId() { return withdrawnByUserId; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public String getWithdrawalReason() { return withdrawalReason; }
}

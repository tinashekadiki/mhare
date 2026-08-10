package zw.ac.uz.emhare.assessmentresults.progression;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.roster.RegistrationRosterImport;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_overall_decisions")
@SQLRestriction("deleted_at IS NULL")
public class StudentOverallDecision extends AuditableEntity {

    public enum Status { CALCULATED, REVIEWED, APPROVED, PUBLISHED, REJECTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "progression_rule_set_id", nullable = false)
    private ProgressionRuleSet ruleSet;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_roster_import_id", nullable = false)
    private RegistrationRosterImport rosterImport;
    @Column(name = "decision_number", nullable = false, length = 80)
    private String decisionNumber;
    @Column(name = "decision_version", nullable = false)
    private int decisionVersion;
    @Column(name = "supersedes_decision_id")
    private UUID supersedesDecisionId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40)
    private String studentNumber;
    @Column(name = "programme_enrolment_id", nullable = false)
    private UUID programmeEnrolmentId;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "academic_period_id", nullable = false)
    private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 50)
    private String academicPeriodCode;
    @Column(name = "programme_period_number", nullable = false)
    private int programmePeriodNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matched_outcome_id", nullable = false)
    private ProgressionRuleOutcome matchedOutcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_code", nullable = false, length = 30)
    private ProgressionRuleOutcome.DecisionCode decisionCode;
    @Column(name = "decision_label", nullable = false, length = 150)
    private String decisionLabel;
    @Column(name = "next_programme_period_number")
    private Integer nextProgrammePeriodNumber;
    @Column(name = "attempted_credits", nullable = false, precision = 8, scale = 2)
    private BigDecimal attemptedCredits;
    @Column(name = "passed_credits", nullable = false, precision = 8, scale = 2)
    private BigDecimal passedCredits;
    @Column(name = "failed_credits", nullable = false, precision = 8, scale = 2)
    private BigDecimal failedCredits;
    @Column(name = "failed_modules", nullable = false)
    private int failedModules;
    @Column(name = "failed_compulsory_modules", nullable = false)
    private int failedCompulsoryModules;
    @Column(name = "weighted_average", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightedAverage;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "status_reason", nullable = false, length = 1000)
    private String statusReason;
    @Column(name = "calculated_by_user_id", nullable = false)
    private UUID calculatedByUserId;
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_reason", length = 1000)
    private String reviewReason;
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;
    @Column(name = "published_by_user_id")
    private UUID publishedByUserId;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "publication_reason", length = 1000)
    private String publicationReason;
    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;
    @Column(name = "rejected_at")
    private Instant rejectedAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    protected StudentOverallDecision() {
    }

    public StudentOverallDecision(
            ProgressionRuleSet ruleSet,
            RegistrationRosterImport rosterImport,
            ProgressionRuleOutcome matchedOutcome,
            ProgressionMetrics metrics,
            String decisionNumber,
            int decisionVersion,
            UUID supersedesDecisionId,
            UUID actorUserId,
            Instant calculatedAt) {
        this.ruleSet = ruleSet;
        this.rosterImport = rosterImport;
        this.decisionNumber = decisionNumber;
        this.decisionVersion = decisionVersion;
        this.supersedesDecisionId = supersedesDecisionId;
        this.studentId = rosterImport.getStudentId();
        this.studentNumber = rosterImport.getStudentNumber();
        this.programmeEnrolmentId = rosterImport.getProgrammeEnrolmentId();
        this.programmeId = rosterImport.getProgrammeId();
        this.programmeVersionId = rosterImport.getProgrammeVersionId();
        this.academicPeriodId = rosterImport.getAcademicPeriodId();
        this.academicPeriodCode = rosterImport.getAcademicPeriodCode();
        this.programmePeriodNumber = rosterImport.getProgrammePeriodNumber();
        this.matchedOutcome = matchedOutcome;
        this.decisionCode = matchedOutcome.getDecisionCode();
        this.decisionLabel = matchedOutcome.getDecisionLabel();
        this.nextProgrammePeriodNumber = matchedOutcome.getNextProgrammePeriodNumber();
        this.attemptedCredits = metrics.attemptedCredits();
        this.passedCredits = metrics.passedCredits();
        this.failedCredits = metrics.failedCredits();
        this.failedModules = metrics.failedModules();
        this.failedCompulsoryModules = metrics.failedCompulsoryModules();
        this.weightedAverage = metrics.weightedAverage();
        this.status = Status.CALCULATED;
        this.statusReason = "Calculated from complete current published Module results.";
        this.calculatedByUserId = actorUserId;
        this.calculatedAt = calculatedAt;
    }

    public Status review(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        require(Status.CALCULATED, expectedVersion);
        if (actorUserId.equals(calculatedByUserId)) {
            throw new IllegalStateException("The progression calculator cannot review the same decision.");
        }
        Status previous = status;
        status = Status.REVIEWED;
        statusReason = ProgressionRuleSet.required(reason);
        reviewedByUserId = actorUserId;
        reviewedAt = occurredAt;
        reviewReason = statusReason;
        return previous;
    }

    public Status approve(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        require(Status.REVIEWED, expectedVersion);
        if (actorUserId.equals(calculatedByUserId) || actorUserId.equals(reviewedByUserId)) {
            throw new IllegalStateException("Progression approval requires an independent approver.");
        }
        Status previous = status;
        status = Status.APPROVED;
        statusReason = ProgressionRuleSet.required(reason);
        approvedByUserId = actorUserId;
        approvedAt = occurredAt;
        approvalReason = statusReason;
        return previous;
    }

    public Status publish(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        require(Status.APPROVED, expectedVersion);
        if (actorUserId.equals(calculatedByUserId)
                || actorUserId.equals(reviewedByUserId)
                || actorUserId.equals(approvedByUserId)) {
            throw new IllegalStateException("Progression publication requires an independent publisher.");
        }
        Status previous = status;
        status = Status.PUBLISHED;
        statusReason = ProgressionRuleSet.required(reason);
        publishedByUserId = actorUserId;
        publishedAt = occurredAt;
        publicationReason = statusReason;
        return previous;
    }

    public Status reject(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        if (status != Status.CALCULATED && status != Status.REVIEWED) {
            throw new IllegalStateException("Only a calculated or reviewed progression decision can be rejected.");
        }
        requireVersion(expectedVersion);
        if (actorUserId.equals(calculatedByUserId)) {
            throw new IllegalStateException("The progression calculator cannot reject the same decision.");
        }
        Status previous = status;
        status = Status.REJECTED;
        statusReason = ProgressionRuleSet.required(reason);
        rejectedByUserId = actorUserId;
        rejectedAt = occurredAt;
        rejectionReason = statusReason;
        return previous;
    }

    private void require(Status requiredStatus, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != requiredStatus) {
            throw new IllegalStateException("Progression decision is not at the required workflow stage.");
        }
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Progression decision changed. Refresh before retrying.");
        }
    }

    public ProgressionRuleSet getRuleSet() { return ruleSet; }
    public RegistrationRosterImport getRosterImport() { return rosterImport; }
    public String getDecisionNumber() { return decisionNumber; }
    public int getDecisionVersion() { return decisionVersion; }
    public UUID getSupersedesDecisionId() { return supersedesDecisionId; }
    public UUID getStudentId() { return studentId; }
    public String getStudentNumber() { return studentNumber; }
    public UUID getProgrammeEnrolmentId() { return programmeEnrolmentId; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public int getProgrammePeriodNumber() { return programmePeriodNumber; }
    public ProgressionRuleOutcome getMatchedOutcome() { return matchedOutcome; }
    public ProgressionRuleOutcome.DecisionCode getDecisionCode() { return decisionCode; }
    public String getDecisionLabel() { return decisionLabel; }
    public Integer getNextProgrammePeriodNumber() { return nextProgrammePeriodNumber; }
    public BigDecimal getAttemptedCredits() { return attemptedCredits; }
    public BigDecimal getPassedCredits() { return passedCredits; }
    public BigDecimal getFailedCredits() { return failedCredits; }
    public int getFailedModules() { return failedModules; }
    public int getFailedCompulsoryModules() { return failedCompulsoryModules; }
    public BigDecimal getWeightedAverage() { return weightedAverage; }
    public Status getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public UUID getCalculatedByUserId() { return calculatedByUserId; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public UUID getPublishedByUserId() { return publishedByUserId; }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getRejectedByUserId() { return rejectedByUserId; }
    public Instant getRejectedAt() { return rejectedAt; }
}

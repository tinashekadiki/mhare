package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.ProgressionDecisionPublishedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "progression_decision_projections")
@SQLRestriction("deleted_at IS NULL")
public class ProgressionDecisionProjection extends AuditableEntity {

    @Column(name = "source_event_id", nullable = false) private UUID sourceEventId;
    @Column(name = "source_progression_decision_id", nullable = false) private UUID sourceProgressionDecisionId;
    @Column(name = "decision_number", nullable = false, length = 80) private String decisionNumber;
    @Column(name = "decision_version", nullable = false) private int decisionVersion;
    @Column(name = "supersedes_decision_id") private UUID supersedesDecisionId;
    @Column(name = "source_progression_rule_set_id", nullable = false) private UUID sourceProgressionRuleSetId;
    @Column(name = "progression_rule_code", nullable = false, length = 40) private String progressionRuleCode;
    @Column(name = "progression_rule_version", nullable = false) private int progressionRuleVersion;
    @Column(name = "source_registration_roster_import_id", nullable = false) private UUID sourceRegistrationRosterImportId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40) private String studentNumber;
    @Column(name = "programme_enrolment_id", nullable = false) private UUID programmeEnrolmentId;
    @Column(name = "programme_id", nullable = false) private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false) private UUID programmeVersionId;
    @Column(name = "academic_period_id", nullable = false) private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 50) private String academicPeriodCode;
    @Column(name = "programme_period_number", nullable = false) private int programmePeriodNumber;
    @Column(name = "decision_code", nullable = false, length = 30) private String decisionCode;
    @Column(name = "decision_label", nullable = false, length = 150) private String decisionLabel;
    @Column(name = "next_programme_period_number") private Integer nextProgrammePeriodNumber;
    @Column(name = "attempted_credits", nullable = false, precision = 8, scale = 2) private BigDecimal attemptedCredits;
    @Column(name = "passed_credits", nullable = false, precision = 8, scale = 2) private BigDecimal passedCredits;
    @Column(name = "failed_credits", nullable = false, precision = 8, scale = 2) private BigDecimal failedCredits;
    @Column(name = "failed_modules", nullable = false) private int failedModules;
    @Column(name = "failed_compulsory_modules", nullable = false) private int failedCompulsoryModules;
    @Column(name = "weighted_average", nullable = false, precision = 6, scale = 2) private BigDecimal weightedAverage;
    @Column(name = "published_by_user_id", nullable = false) private UUID publishedByUserId;
    @Column(name = "published_at", nullable = false) private Instant publishedAt;
    @Column(name = "current_version", nullable = false) private boolean currentVersion;

    protected ProgressionDecisionProjection() {
    }

    public ProgressionDecisionProjection(ProgressionDecisionPublishedEvent event) {
        sourceEventId = event.eventId();
        sourceProgressionDecisionId = event.progressionDecisionId();
        decisionNumber = required(event.decisionNumber());
        decisionVersion = event.decisionVersion();
        supersedesDecisionId = event.supersedesDecisionId();
        sourceProgressionRuleSetId = event.progressionRuleSetId();
        progressionRuleCode = required(event.progressionRuleCode());
        progressionRuleVersion = event.progressionRuleVersion();
        sourceRegistrationRosterImportId = event.registrationRosterImportId();
        studentId = event.studentId();
        studentNumber = required(event.studentNumber());
        programmeEnrolmentId = event.programmeEnrolmentId();
        programmeId = event.programmeId();
        programmeVersionId = event.programmeVersionId();
        academicPeriodId = event.academicPeriodId();
        academicPeriodCode = required(event.academicPeriodCode());
        programmePeriodNumber = event.programmePeriodNumber();
        decisionCode = required(event.decisionCode());
        decisionLabel = required(event.decisionLabel());
        nextProgrammePeriodNumber = event.nextProgrammePeriodNumber();
        attemptedCredits = event.attemptedCredits();
        passedCredits = event.passedCredits();
        failedCredits = event.failedCredits();
        failedModules = event.failedModules();
        failedCompulsoryModules = event.failedCompulsoryModules();
        weightedAverage = event.weightedAverage();
        publishedByUserId = event.publishedByUserId();
        publishedAt = event.publishedAt();
        currentVersion = true;
    }

    public void markSuperseded() { currentVersion = false; }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Progression projection text is required.");
        }
        return value.trim();
    }

    public UUID getSourceProgressionDecisionId() { return sourceProgressionDecisionId; }
    public String getDecisionNumber() { return decisionNumber; }
    public int getDecisionVersion() { return decisionVersion; }
    public String getStudentNumber() { return studentNumber; }
    public UUID getStudentId() { return studentId; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public int getProgrammePeriodNumber() { return programmePeriodNumber; }
    public String getDecisionCode() { return decisionCode; }
    public String getDecisionLabel() { return decisionLabel; }
    public Integer getNextProgrammePeriodNumber() { return nextProgrammePeriodNumber; }
    public BigDecimal getAttemptedCredits() { return attemptedCredits; }
    public BigDecimal getPassedCredits() { return passedCredits; }
    public BigDecimal getFailedCredits() { return failedCredits; }
    public int getFailedModules() { return failedModules; }
    public int getFailedCompulsoryModules() { return failedCompulsoryModules; }
    public BigDecimal getWeightedAverage() { return weightedAverage; }
    public UUID getPublishedByUserId() { return publishedByUserId; }
    public Instant getPublishedAt() { return publishedAt; }
}

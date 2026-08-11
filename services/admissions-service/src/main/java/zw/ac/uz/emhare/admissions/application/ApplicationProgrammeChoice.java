package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "application_programme_choices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_application_choice_rank", columnNames = {"application_id", "choice_rank"}),
                @UniqueConstraint(name = "uk_application_choice_programme", columnNames = {"application_id", "programme_id"})
        })
public class ApplicationProgrammeChoice extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;

    @Column(name = "programme_code", nullable = false, length = 50)
    private String programmeCode;

    @Column(name = "programme_name", nullable = false, length = 200)
    private String programmeName;

    @Column(name = "award_name", nullable = false, length = 200)
    private String awardName;

    @Column(name = "owning_academic_unit_id", nullable = false)
    private UUID owningAcademicUnitId;

    @Column(name = "owning_academic_unit_name", nullable = false, length = 180)
    private String owningAcademicUnitName;

    @Column(name = "programme_version_code", nullable = false, length = 40)
    private String programmeVersionCode;

    @Column(name = "catalogue_snapshot_status", nullable = false, length = 30)
    private String catalogueSnapshotStatus;

    @Column(name = "choice_rank", nullable = false)
    private int choiceRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "choice_status", nullable = false, length = 30)
    private ProgrammeChoiceStatus choiceStatus;

    @Column(name = "evaluation_summary", length = 1000)
    private String evaluationSummary;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    protected ApplicationProgrammeChoice() {
    }

    public ApplicationProgrammeChoice(
            Application application,
            ProgrammeSelectionSnapshot programmeSelection,
            int choiceRank) {
        this.application = application;
        this.programmeId = programmeSelection.programmeId();
        this.programmeVersionId = programmeSelection.programmeVersionId();
        this.programmeCode = programmeSelection.programmeCode();
        this.programmeName = programmeSelection.programmeName();
        this.awardName = programmeSelection.awardName();
        this.owningAcademicUnitId = programmeSelection.owningAcademicUnitId();
        this.owningAcademicUnitName = programmeSelection.owningAcademicUnitName();
        this.programmeVersionCode = programmeSelection.programmeVersionCode();
        this.catalogueSnapshotStatus = "VALIDATED";
        this.choiceRank = choiceRank;
        this.choiceStatus = ProgrammeChoiceStatus.PENDING;
    }

    public Application getApplication() {
        return application;
    }

    public UUID getProgrammeId() {
        return programmeId;
    }

    public int getChoiceRank() {
        return choiceRank;
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

    public String getAwardName() {
        return awardName;
    }

    public String getOwningAcademicUnitName() {
        return owningAcademicUnitName;
    }

    public String getProgrammeVersionCode() {
        return programmeVersionCode;
    }

    public ProgrammeChoiceStatus getChoiceStatus() {
        return choiceStatus;
    }

    public void recordEvaluation(EvaluationStatus evaluationStatus, String summary) {
        choiceStatus = switch (evaluationStatus) {
            case ELIGIBLE -> ProgrammeChoiceStatus.ELIGIBLE;
            case CONDITIONALLY_ELIGIBLE -> ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE;
            case NOT_ELIGIBLE -> ProgrammeChoiceStatus.INELIGIBLE;
            case REQUIRES_REVIEW -> ProgrammeChoiceStatus.REQUIRES_REVIEW;
        };
        evaluationSummary = summary;
    }

    public void enterAcademicReview() {
        if (choiceStatus != ProgrammeChoiceStatus.ELIGIBLE && choiceStatus != ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE) {
            throw new IllegalStateException("Only an eligible programme choice can enter academic review.");
        }
        choiceStatus = ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW;
    }

    public void recordDecision(DecisionOutcome decision, String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW) {
            throw new IllegalStateException("Only a programme choice under academic review can receive an admission decision.");
        }
        choiceStatus = decision == DecisionOutcome.ADMIT ? ProgrammeChoiceStatus.ADMITTED : ProgrammeChoiceStatus.REJECTED;
        decisionReason = reason;
    }

    public void closeAfterHigherRankAdmission(String reason) {
        if (choiceStatus == ProgrammeChoiceStatus.ELIGIBLE
                || choiceStatus == ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE
                || choiceStatus == ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW
                || choiceStatus == ProgrammeChoiceStatus.REQUIRES_REVIEW
                || choiceStatus == ProgrammeChoiceStatus.PENDING) {
            choiceStatus = ProgrammeChoiceStatus.REJECTED;
            decisionReason = reason;
        }
    }

    public void markOffered(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.ADMITTED) {
            throw new IllegalStateException("Only an admitted programme choice can receive an offer.");
        }
        choiceStatus = ProgrammeChoiceStatus.OFFERED;
        decisionReason = reason;
    }

    public void markConverted(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.OFFERED) {
            throw new IllegalStateException("Only an offered programme choice can be converted.");
        }
        choiceStatus = ProgrammeChoiceStatus.CONVERTED;
        decisionReason = reason;
    }

    public void recordOfferResponse(OfferResponseType response, String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.OFFERED) {
            throw new IllegalStateException("Only an offered programme choice can record an offer response.");
        }
        if (response == OfferResponseType.DECLINED) {
            choiceStatus = ProgrammeChoiceStatus.REJECTED;
        }
        decisionReason = reason;
    }

    public void reopenAfterOfferClosed(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.OFFERED) {
            throw new IllegalStateException("Only an offered programme choice can return to admitted status.");
        }
        choiceStatus = ProgrammeChoiceStatus.ADMITTED;
        decisionReason = reason;
    }

    public UUID getOwningAcademicUnitId() {
        return owningAcademicUnitId;
    }

    public String getEvaluationSummary() {
        return evaluationSummary;
    }

    public String getDecisionReason() {
        return decisionReason;
    }
}

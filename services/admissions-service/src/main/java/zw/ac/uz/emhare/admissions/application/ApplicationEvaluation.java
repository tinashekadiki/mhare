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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "application_evaluations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_application_evaluations_choice_requirement",
                columnNames = {"programme_choice_id", "requirement_set_id"}))
public class ApplicationEvaluation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_choice_id", nullable = false)
    private ApplicationProgrammeChoice programmeChoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_set_id", nullable = false)
    private AdmissionRequirementSet requirementSet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EvaluationStatus status;

    @Column(name = "total_points", precision = 8, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "rank_score", precision = 10, scale = 4)
    private BigDecimal rankScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_requirements_json", columnDefinition = "jsonb")
    private String missingRequirementsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results_json", columnDefinition = "jsonb")
    private String ruleResultsJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "evaluated_by_user_id")
    private UUID evaluatedByUserId;

    protected ApplicationEvaluation() {
    }

    public ApplicationEvaluation(
            Application application,
            ApplicationProgrammeChoice programmeChoice,
            AdmissionRequirementSet requirementSet,
            EvaluationStatus status,
            BigDecimal totalPoints,
            BigDecimal rankScore,
            String missingRequirementsJson,
            String ruleResultsJson,
            Instant evaluatedAt,
            UUID evaluatedByUserId) {
        this.application = application;
        this.programmeChoice = programmeChoice;
        this.requirementSet = requirementSet;
        this.status = status;
        this.totalPoints = totalPoints;
        this.rankScore = rankScore;
        this.missingRequirementsJson = missingRequirementsJson;
        this.ruleResultsJson = ruleResultsJson;
        this.evaluatedAt = evaluatedAt;
        this.evaluatedByUserId = evaluatedByUserId;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public BigDecimal getTotalPoints() {
        return totalPoints;
    }

    public BigDecimal getRankScore() {
        return rankScore;
    }

    public AdmissionRequirementSet getRequirementSet() {
        return requirementSet;
    }

    public ApplicationProgrammeChoice getProgrammeChoice() {
        return programmeChoice;
    }
}

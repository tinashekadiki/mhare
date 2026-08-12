package zw.ac.uz.emhare.assessmentresults.progression.domain.model;

import zw.ac.uz.emhare.assessmentresults.progression.*;

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
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "progression_rule_outcomes")
@SQLRestriction("deleted_at IS NULL")
public class ProgressionRuleOutcome extends AuditableEntity {

    public enum DecisionCode { PROCEED, PROCEED_WITH_CARRY, REPEAT, EXCLUDE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "progression_rule_set_id", nullable = false)
    private ProgressionRuleSet ruleSet;
    @Column(nullable = false)
    private int priority;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_code", nullable = false, length = 30)
    private DecisionCode decisionCode;
    @Column(name = "decision_label", nullable = false, length = 150)
    private String decisionLabel;
    @Column(name = "minimum_weighted_average", precision = 6, scale = 2)
    private BigDecimal minimumWeightedAverage;
    @Column(name = "minimum_passed_credits", precision = 8, scale = 2)
    private BigDecimal minimumPassedCredits;
    @Column(name = "maximum_failed_credits", precision = 8, scale = 2)
    private BigDecimal maximumFailedCredits;
    @Column(name = "maximum_failed_modules")
    private Integer maximumFailedModules;
    @Column(name = "require_all_compulsory_passed", nullable = false)
    private boolean requireAllCompulsoryPassed;
    @Column(name = "next_programme_period_number")
    private Integer nextProgrammePeriodNumber;
    @Column(name = "fallback_outcome", nullable = false)
    private boolean fallbackOutcome;

    protected ProgressionRuleOutcome() {
    }

    public ProgressionRuleOutcome(
            ProgressionRuleSet ruleSet,
            int priority,
            DecisionCode decisionCode,
            String decisionLabel,
            BigDecimal minimumWeightedAverage,
            BigDecimal minimumPassedCredits,
            BigDecimal maximumFailedCredits,
            Integer maximumFailedModules,
            boolean requireAllCompulsoryPassed,
            Integer nextProgrammePeriodNumber,
            boolean fallbackOutcome) {
        this.ruleSet = ruleSet;
        this.priority = priority;
        this.decisionCode = decisionCode;
        this.decisionLabel = ProgressionRuleSet.required(decisionLabel);
        this.minimumWeightedAverage = minimumWeightedAverage;
        this.minimumPassedCredits = minimumPassedCredits;
        this.maximumFailedCredits = maximumFailedCredits;
        this.maximumFailedModules = maximumFailedModules;
        this.requireAllCompulsoryPassed = requireAllCompulsoryPassed;
        this.nextProgrammePeriodNumber = nextProgrammePeriodNumber;
        this.fallbackOutcome = fallbackOutcome;
    }

    public boolean matches(ProgressionMetrics metrics) {
        return (minimumWeightedAverage == null || metrics.weightedAverage().compareTo(minimumWeightedAverage) >= 0)
                && (minimumPassedCredits == null || metrics.passedCredits().compareTo(minimumPassedCredits) >= 0)
                && (maximumFailedCredits == null || metrics.failedCredits().compareTo(maximumFailedCredits) <= 0)
                && (maximumFailedModules == null || metrics.failedModules() <= maximumFailedModules)
                && (!requireAllCompulsoryPassed || metrics.failedCompulsoryModules() == 0);
    }

    public ProgressionRuleSet getRuleSet() { return ruleSet; }
    public int getPriority() { return priority; }
    public DecisionCode getDecisionCode() { return decisionCode; }
    public String getDecisionLabel() { return decisionLabel; }
    public BigDecimal getMinimumWeightedAverage() { return minimumWeightedAverage; }
    public BigDecimal getMinimumPassedCredits() { return minimumPassedCredits; }
    public BigDecimal getMaximumFailedCredits() { return maximumFailedCredits; }
    public Integer getMaximumFailedModules() { return maximumFailedModules; }
    public boolean isRequireAllCompulsoryPassed() { return requireAllCompulsoryPassed; }
    public Integer getNextProgrammePeriodNumber() { return nextProgrammePeriodNumber; }
    public boolean isFallbackOutcome() { return fallbackOutcome; }
}

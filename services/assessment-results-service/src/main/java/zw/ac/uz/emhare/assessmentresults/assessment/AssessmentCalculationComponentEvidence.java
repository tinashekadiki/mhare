package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.ComponentType;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "assessment_calculation_component_evidence")
@SQLRestriction("deleted_at IS NULL")
public class AssessmentCalculationComponentEvidence extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "calculation_run_id")
    private AssessmentCalculationRun calculationRun;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "calculation_outcome_id")
    private AssessmentCalculationOutcome calculationOutcome;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assessment_component_id")
    private AssessmentComponent assessmentComponent;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submitted_mark_id")
    private StudentAssessmentMark submittedMark;
    @Column(name = "component_code", nullable = false, length = 30) private String componentCode;
    @Enumerated(EnumType.STRING) @Column(name = "component_type", nullable = false, length = 30) private ComponentType componentType;
    @Column(nullable = false, precision = 8, scale = 2) private BigDecimal score;
    @Column(name = "maximum_mark", nullable = false, precision = 8, scale = 2) private BigDecimal maximumMark;
    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2) private BigDecimal weightPercent;
    @Column(name = "weighted_contribution", nullable = false, precision = 6, scale = 2) private BigDecimal weightedContribution;

    protected AssessmentCalculationComponentEvidence() {}
    public AssessmentCalculationComponentEvidence(AssessmentCalculationRun run, AssessmentCalculationOutcome outcome, AssessmentComponent component, StudentAssessmentMark mark, BigDecimal contribution) {
        calculationRun = run; calculationOutcome = outcome; assessmentComponent = component; submittedMark = mark;
        componentCode = component.getCode(); componentType = component.getComponentType(); score = mark.getScore();
        maximumMark = component.getMaximumMark(); weightPercent = component.getWeightPercent(); weightedContribution = contribution;
    }
    public ComponentType getComponentType(){return componentType;}
    public BigDecimal getWeightedContribution(){return weightedContribution;}
}

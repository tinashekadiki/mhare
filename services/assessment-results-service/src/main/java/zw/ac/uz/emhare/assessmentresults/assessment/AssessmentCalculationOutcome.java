package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterEntry;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="assessment_calculation_outcomes") @SQLRestriction("deleted_at IS NULL")
public class AssessmentCalculationOutcome extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="calculation_run_id") private AssessmentCalculationRun calculationRun;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assessment_roster_entry_id") private AssessmentRosterEntry rosterEntry;
    @Column(name="weighted_total",precision=6,scale=2) private BigDecimal weightedTotal;
    @Column(name="is_complete",nullable=false) private boolean complete;
    @Column(name="missing_component_codes",length=1000) private String missingComponentCodes;
    protected AssessmentCalculationOutcome() {}
    public AssessmentCalculationOutcome(AssessmentCalculationRun run,AssessmentRosterEntry entry,BigDecimal total,String missing){calculationRun=run;rosterEntry=entry;complete=missing==null;weightedTotal=complete?total:null;missingComponentCodes=missing;}
    public AssessmentCalculationRun getCalculationRun(){return calculationRun;} public AssessmentRosterEntry getRosterEntry(){return rosterEntry;} public BigDecimal getWeightedTotal(){return weightedTotal;} public boolean isComplete(){return complete;} public String getMissingComponentCodes(){return missingComponentCodes;}
}

package zw.ac.uz.emhare.assessmentresults.result.domain.model;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationOutcome;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.AssessmentRosterEntry;

import zw.ac.uz.emhare.assessmentresults.result.*;
import jakarta.persistence.*;import java.math.BigDecimal;import org.hibernate.annotations.SQLRestriction;import org.hibernate.envers.Audited;import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationOutcome;import zw.ac.uz.emhare.assessmentresults.roster.domain.model.AssessmentRosterEntry;import zw.ac.uz.emhare.common.persistence.AuditableEntity;
/** @author Tinashe K */
@Audited @Entity @Table(name="module_results") @SQLRestriction("deleted_at IS NULL")
public class ModuleResult extends AuditableEntity{
 public enum Status{PASS,FAIL}
 @ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="result_batch_id")private ResultBatch resultBatch;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="calculation_outcome_id")private AssessmentCalculationOutcome calculationOutcome;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="assessment_roster_entry_id")private AssessmentRosterEntry rosterEntry;@Column(name="coursework_mark",nullable=false,precision=6,scale=2)private BigDecimal courseworkMark;@Column(name="examination_mark",nullable=false,precision=6,scale=2)private BigDecimal examinationMark;@Column(name="final_mark",nullable=false,precision=6,scale=2)private BigDecimal finalMark;@Column(nullable=false,length=10)private String grade;@Column(nullable=false,length=100)private String remark;@Enumerated(EnumType.STRING)@Column(name="result_status",nullable=false,length=20)private Status resultStatus;
 protected ModuleResult(){}public ModuleResult(ResultBatch batch,AssessmentCalculationOutcome outcome,BigDecimal coursework,BigDecimal exam,GradingBand band){resultBatch=batch;calculationOutcome=outcome;rosterEntry=outcome.getRosterEntry();courseworkMark=coursework;examinationMark=exam;finalMark=outcome.getWeightedTotal();grade=band.getGrade();remark=band.getRemark();resultStatus=band.isPassing()?Status.PASS:Status.FAIL;}
 public ResultBatch getResultBatch(){return resultBatch;}public AssessmentCalculationOutcome getCalculationOutcome(){return calculationOutcome;}public AssessmentRosterEntry getRosterEntry(){return rosterEntry;}public BigDecimal getCourseworkMark(){return courseworkMark;}public BigDecimal getExaminationMark(){return examinationMark;}public BigDecimal getFinalMark(){return finalMark;}public String getGrade(){return grade;}public String getRemark(){return remark;}public Status getResultStatus(){return resultStatus;}
}

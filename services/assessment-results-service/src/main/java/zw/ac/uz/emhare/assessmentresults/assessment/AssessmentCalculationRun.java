package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.CalculationStatus;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="assessment_calculation_runs") @SQLRestriction("deleted_at IS NULL")
public class AssessmentCalculationRun extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="module_offering_id") private AssessmentModuleOffering moduleOffering;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assessment_scheme_id") private AssessmentScheme assessmentScheme;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="rule_snapshot",nullable=false,columnDefinition="jsonb") private Map<String,Object> ruleSnapshot;
    @Column(name="roster_count",nullable=false) private int rosterCount; @Column(name="complete_result_count",nullable=false) private int completeResultCount; @Column(name="incomplete_result_count",nullable=false) private int incompleteResultCount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private CalculationStatus status;
    @Column(name="initiated_by_user_id",nullable=false) private UUID initiatedByUserId; @Column(name="initiated_at",nullable=false) private Instant initiatedAt; @Column(name="completed_at") private Instant completedAt;
    protected AssessmentCalculationRun() {}
    public AssessmentCalculationRun(AssessmentModuleOffering offering,AssessmentScheme scheme,Map<String,Object> snapshot,int total,int complete,UUID actor,Instant now){moduleOffering=offering;assessmentScheme=scheme;ruleSnapshot=snapshot;rosterCount=total;completeResultCount=complete;incompleteResultCount=total-complete;status=CalculationStatus.COMPLETED;initiatedByUserId=actor;initiatedAt=now;completedAt=now;}
    public AssessmentModuleOffering getModuleOffering(){return moduleOffering;} public AssessmentScheme getAssessmentScheme(){return assessmentScheme;} public Map<String,Object> getRuleSnapshot(){return ruleSnapshot;} public int getRosterCount(){return rosterCount;} public int getCompleteResultCount(){return completeResultCount;} public int getIncompleteResultCount(){return incompleteResultCount;} public CalculationStatus getStatus(){return status;} public UUID getInitiatedByUserId(){return initiatedByUserId;} public Instant getInitiatedAt(){return initiatedAt;} public Instant getCompletedAt(){return completedAt;}
}

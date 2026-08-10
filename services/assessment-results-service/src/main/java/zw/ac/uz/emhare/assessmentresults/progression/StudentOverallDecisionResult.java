package zw.ac.uz.emhare.assessmentresults.progression;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.result.PublishedResult;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterEntry;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_overall_decision_results")
@SQLRestriction("deleted_at IS NULL")
public class StudentOverallDecisionResult extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_overall_decision_id", nullable = false)
    private StudentOverallDecision decision;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_result_id", nullable = false)
    private PublishedResult publishedResult;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_roster_entry_id", nullable = false)
    private AssessmentRosterEntry rosterEntry;
    @Column(name = "module_id", nullable = false)
    private UUID moduleId;
    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode;
    @Column(name = "module_name", nullable = false, length = 200)
    private String moduleName;
    @Column(name = "curriculum_module_type", nullable = false, length = 20)
    private String curriculumModuleType;
    @Column(name = "credit_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal creditValue;
    @Column(name = "final_mark", nullable = false, precision = 6, scale = 2)
    private BigDecimal finalMark;
    @Column(nullable = false, length = 10)
    private String grade;
    @Column(nullable = false, length = 100)
    private String remark;
    @Column(nullable = false)
    private boolean passing;
    @Column(name = "publication_version", nullable = false)
    private int publicationVersion;

    protected StudentOverallDecisionResult() {
    }

    public StudentOverallDecisionResult(StudentOverallDecision decision, PublishedResult publishedResult) {
        AssessmentRosterEntry sourceRosterEntry = publishedResult.getModuleResult().getRosterEntry();
        this.decision = decision;
        this.publishedResult = publishedResult;
        this.rosterEntry = sourceRosterEntry;
        this.moduleId = publishedResult.getModuleId();
        this.moduleCode = publishedResult.getModuleCode();
        this.moduleName = publishedResult.getModuleName();
        this.curriculumModuleType = sourceRosterEntry.getCurriculumModuleType();
        this.creditValue = sourceRosterEntry.getCreditValue();
        this.finalMark = publishedResult.getFinalMark();
        this.grade = publishedResult.getGrade();
        this.remark = publishedResult.getRemark();
        this.passing = publishedResult.getModuleResult().getResultStatus()
                == zw.ac.uz.emhare.assessmentresults.result.ModuleResult.Status.PASS;
        this.publicationVersion = publishedResult.getPublicationVersion();
    }

    public StudentOverallDecision getDecision() { return decision; }
    public PublishedResult getPublishedResult() { return publishedResult; }
    public AssessmentRosterEntry getRosterEntry() { return rosterEntry; }
    public String getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public String getCurriculumModuleType() { return curriculumModuleType; }
    public BigDecimal getCreditValue() { return creditValue; }
    public BigDecimal getFinalMark() { return finalMark; }
    public String getGrade() { return grade; }
    public String getRemark() { return remark; }
    public boolean isPassing() { return passing; }
    public int getPublicationVersion() { return publicationVersion; }
}

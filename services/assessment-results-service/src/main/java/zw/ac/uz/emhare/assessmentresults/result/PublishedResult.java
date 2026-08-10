package zw.ac.uz.emhare.assessmentresults.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentModuleOffering;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterEntry;
import zw.ac.uz.emhare.assessmentresults.roster.RegistrationRosterImport;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "published_results")
@SQLRestriction("deleted_at IS NULL")
public class PublishedResult extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_batch_id", nullable = false)
    private ResultBatch resultBatch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_result_id", nullable = false)
    private ModuleResult moduleResult;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40)
    private String studentNumber;
    @Column(name = "module_id", nullable = false)
    private UUID moduleId;
    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode;
    @Column(name = "module_name", nullable = false, length = 200)
    private String moduleName;
    @Column(name = "academic_period_id", nullable = false)
    private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 50)
    private String academicPeriodCode;
    @Column(name = "final_mark", nullable = false, precision = 6, scale = 2)
    private BigDecimal finalMark;
    @Column(nullable = false, length = 10)
    private String grade;
    @Column(nullable = false, length = 100)
    private String remark;
    @Column(name = "published_by_user_id", nullable = false)
    private UUID publishedByUserId;
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
    @Column(name = "publication_version", nullable = false)
    private int publicationVersion;
    @Column(name = "supersedes_published_result_id")
    private UUID supersedesPublishedResultId;
    @Column(name = "result_amendment_id")
    private UUID resultAmendmentId;

    protected PublishedResult() {
    }

    public PublishedResult(ResultBatch batch, ModuleResult result, UUID actorUserId, Instant publishedAt) {
        AssessmentRosterEntry rosterEntry = result.getRosterEntry();
        RegistrationRosterImport rosterImport = rosterEntry.getRosterImport();
        AssessmentModuleOffering offering = batch.getModuleOffering();
        this.resultBatch = batch;
        this.moduleResult = result;
        this.studentId = rosterImport.getStudentId();
        this.studentNumber = rosterImport.getStudentNumber();
        this.moduleId = offering.getModuleId();
        this.moduleCode = offering.getModuleCode();
        this.moduleName = offering.getModuleName();
        this.academicPeriodId = offering.getAcademicPeriodId();
        this.academicPeriodCode = offering.getAcademicPeriodCode();
        this.finalMark = result.getFinalMark();
        this.grade = result.getGrade();
        this.remark = result.getRemark();
        this.publishedByUserId = actorUserId;
        this.publishedAt = publishedAt;
        this.publicationVersion = 1;
    }

    public PublishedResult(
            PublishedResult original,
            PublishedResultAmendment amendment,
            UUID actorUserId,
            Instant publishedAt) {
        this.resultBatch = amendment.getReplacementResultBatch();
        this.moduleResult = amendment.getReplacementModuleResult();
        this.studentId = original.getStudentId();
        this.studentNumber = original.getStudentNumber();
        this.moduleId = original.getModuleId();
        this.moduleCode = original.getModuleCode();
        this.moduleName = original.getModuleName();
        this.academicPeriodId = original.getAcademicPeriodId();
        this.academicPeriodCode = original.getAcademicPeriodCode();
        this.finalMark = amendment.getProposedFinalMark();
        this.grade = amendment.getProposedGrade();
        this.remark = amendment.getProposedRemark();
        this.publishedByUserId = actorUserId;
        this.publishedAt = publishedAt;
        this.publicationVersion = original.getPublicationVersion() + 1;
        this.supersedesPublishedResultId = original.getId();
        this.resultAmendmentId = amendment.getId();
    }

    public ResultBatch getResultBatch() { return resultBatch; }
    public ModuleResult getModuleResult() { return moduleResult; }
    public UUID getStudentId() { return studentId; }
    public String getStudentNumber() { return studentNumber; }
    public UUID getModuleId() { return moduleId; }
    public String getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public BigDecimal getFinalMark() { return finalMark; }
    public String getGrade() { return grade; }
    public String getRemark() { return remark; }
    public UUID getPublishedByUserId() { return publishedByUserId; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getPublicationVersion() { return publicationVersion; }
    public UUID getSupersedesPublishedResultId() { return supersedesPublishedResultId; }
    public UUID getResultAmendmentId() { return resultAmendmentId; }
}

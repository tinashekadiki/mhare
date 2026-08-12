package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.PublishedResultVersionCreatedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "published_result_projections")
@SQLRestriction("deleted_at IS NULL")
public class PublishedResultProjection extends AuditableEntity {

    @Column(name = "source_event_id", nullable = false) private UUID sourceEventId;
    @Column(name = "source_published_result_id", nullable = false) private UUID sourcePublishedResultId;
    @Column(name = "source_result_batch_id", nullable = false) private UUID sourceResultBatchId;
    @Column(name = "source_module_result_id", nullable = false) private UUID sourceModuleResultId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40) private String studentNumber;
    @Column(name = "programme_enrolment_id", nullable = false) private UUID programmeEnrolmentId;
    @Column(name = "programme_id", nullable = false) private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false) private UUID programmeVersionId;
    @Column(name = "academic_period_id", nullable = false) private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 50) private String academicPeriodCode;
    @Column(name = "module_id", nullable = false) private UUID moduleId;
    @Column(name = "module_code", nullable = false, length = 50) private String moduleCode;
    @Column(name = "module_name", nullable = false, length = 200) private String moduleName;
    @Column(name = "curriculum_module_type", nullable = false, length = 20) private String curriculumModuleType;
    @Column(name = "credit_value", nullable = false, precision = 6, scale = 2) private BigDecimal creditValue;
    @Column(name = "final_mark", nullable = false, precision = 6, scale = 2) private BigDecimal finalMark;
    @Column(nullable = false, length = 10) private String grade;
    @Column(nullable = false, length = 100) private String remark;
    @Column(nullable = false) private boolean passing;
    @Column(name = "publication_version", nullable = false) private int publicationVersion;
    @Column(name = "supersedes_published_result_id") private UUID supersedesPublishedResultId;
    @Column(name = "result_amendment_id") private UUID resultAmendmentId;
    @Column(name = "published_by_user_id", nullable = false) private UUID publishedByUserId;
    @Column(name = "published_at", nullable = false) private Instant publishedAt;
    @Column(name = "current_version", nullable = false) private boolean currentVersion;

    protected PublishedResultProjection() {
    }

    public PublishedResultProjection(PublishedResultVersionCreatedEvent event) {
        sourceEventId = event.eventId();
        sourcePublishedResultId = event.publishedResultId();
        sourceResultBatchId = event.resultBatchId();
        sourceModuleResultId = event.moduleResultId();
        studentId = event.studentId();
        studentNumber = required(event.studentNumber());
        programmeEnrolmentId = event.programmeEnrolmentId();
        programmeId = event.programmeId();
        programmeVersionId = event.programmeVersionId();
        academicPeriodId = event.academicPeriodId();
        academicPeriodCode = required(event.academicPeriodCode());
        moduleId = event.moduleId();
        moduleCode = required(event.moduleCode());
        moduleName = required(event.moduleName());
        curriculumModuleType = required(event.curriculumModuleType());
        creditValue = event.creditValue();
        finalMark = event.finalMark();
        grade = required(event.grade());
        remark = required(event.remark());
        passing = event.passing();
        publicationVersion = event.publicationVersion();
        supersedesPublishedResultId = event.supersedesPublishedResultId();
        resultAmendmentId = event.resultAmendmentId();
        publishedByUserId = event.publishedByUserId();
        publishedAt = event.publishedAt();
        currentVersion = true;
    }

    public void markSuperseded() { currentVersion = false; }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Published result projection text is required.");
        }
        return value.trim();
    }

    public UUID getSourcePublishedResultId() { return sourcePublishedResultId; }
    public UUID getStudentId() { return studentId; }
    public String getStudentNumber() { return studentNumber; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public UUID getModuleId() { return moduleId; }
    public String getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public String getCurriculumModuleType() { return curriculumModuleType; }
    public BigDecimal getCreditValue() { return creditValue; }
    public BigDecimal getFinalMark() { return finalMark; }
    public String getGrade() { return grade; }
    public String getRemark() { return remark; }
    public boolean isPassing() { return passing; }
    public int getPublicationVersion() { return publicationVersion; }
    public Instant getPublishedAt() { return publishedAt; }
}

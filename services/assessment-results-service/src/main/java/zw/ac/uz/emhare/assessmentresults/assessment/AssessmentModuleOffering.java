package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.OfferingStatus;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterEntry;
import zw.ac.uz.emhare.assessmentresults.roster.RegistrationRosterImport;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name = "assessment_module_offerings") @SQLRestriction("deleted_at IS NULL")
public class AssessmentModuleOffering extends AuditableEntity {
    @Column(name="module_id", nullable=false) private UUID moduleId;
    @Column(name="module_code", nullable=false, length=50) private String moduleCode;
    @Column(name="module_name", nullable=false, length=200) private String moduleName;
    @Column(name="academic_period_id", nullable=false) private UUID academicPeriodId;
    @Column(name="academic_period_code", nullable=false, length=50) private String academicPeriodCode;
    @Column(name="academic_period_name", nullable=false, length=150) private String academicPeriodName;
    @Column(name="assigned_instructor_user_id", nullable=false) private UUID assignedInstructorUserId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OfferingStatus status;

    protected AssessmentModuleOffering() {}

    public AssessmentModuleOffering(AssessmentRosterEntry rosterEntry, UUID assignedInstructorUserId) {
        RegistrationRosterImport rosterImport = rosterEntry.getRosterImport();
        this.moduleId = rosterEntry.getModuleId();
        this.moduleCode = rosterEntry.getModuleCode();
        this.moduleName = rosterEntry.getModuleName();
        this.academicPeriodId = rosterImport.getAcademicPeriodId();
        this.academicPeriodCode = rosterImport.getAcademicPeriodCode();
        this.academicPeriodName = rosterImport.getAcademicPeriodName();
        this.assignedInstructorUserId = assignedInstructorUserId;
        this.status = OfferingStatus.DRAFT;
    }

    public void activate() { status = OfferingStatus.ACTIVE; }
    public UUID getModuleId() { return moduleId; }
    public String getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public String getAcademicPeriodName() { return academicPeriodName; }
    public UUID getAssignedInstructorUserId() { return assignedInstructorUserId; }
    public OfferingStatus getStatus() { return status; }
}

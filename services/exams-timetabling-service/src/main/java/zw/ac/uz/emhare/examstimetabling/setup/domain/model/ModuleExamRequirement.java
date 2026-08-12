package zw.ac.uz.emhare.examstimetabling.setup.domain.model;

import zw.ac.uz.emhare.examstimetabling.setup.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="module_exam_requirements") @SQLRestriction("deleted_at IS NULL")
public class ModuleExamRequirement extends AuditableEntity {
    public enum Status { DRAFT,APPROVED,SUPERSEDED }
    @Column(name="academic_period_id",nullable=false) private UUID academicPeriodId; @Column(name="module_id",nullable=false) private UUID moduleId;
    @Column(name="module_code",nullable=false,length=50) private String moduleCode; @Column(name="module_name",nullable=false,length=200) private String moduleName;
    @Column(name="requirement_version",nullable=false) private int requirementVersion; @Column(name="duration_minutes",nullable=false) private int durationMinutes;
    @Column(name="reading_time_minutes",nullable=false) private int readingTimeMinutes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="required_venue_type_id") private ExamVenueType requiredVenueType;
    @Column(name="special_requirements",length=1000) private String specialRequirements;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="approved_by_user_id") private UUID approvedByUserId; @Column(name="approved_at") private Instant approvedAt;
    @Column(name="approval_reason",length=1000) private String approvalReason;
    protected ModuleExamRequirement() {}
    public ModuleExamRequirement(UUID academicPeriodId,UUID moduleId,String moduleCode,String moduleName,int requirementVersion,
            int durationMinutes,int readingTimeMinutes,ExamVenueType requiredVenueType,String specialRequirements){
        if(academicPeriodId==null||moduleId==null||requirementVersion<1||durationMinutes<15||durationMinutes>480||readingTimeMinutes<0||readingTimeMinutes>120)
            throw new IllegalArgumentException("Module exam requirement scope, version, or duration is invalid.");
        this.academicPeriodId=academicPeriodId;this.moduleId=moduleId;this.moduleCode=ExamVenueType.text(moduleCode,"Module code");
        this.moduleName=ExamVenueType.text(moduleName,"Module name");this.requirementVersion=requirementVersion;
        this.durationMinutes=durationMinutes;this.readingTimeMinutes=readingTimeMinutes;this.requiredVenueType=requiredVenueType;
        this.specialRequirements=ExamVenueType.optional(specialRequirements);status=Status.DRAFT;
    }
    public void approve(UUID actor,String reason,Instant now,long expectedVersion){if(getVersion()!=expectedVersion)throw new IllegalStateException("Module exam requirement was changed by another user. Refresh before retrying.");if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft Module exam requirement can be approved.");approvedByUserId=actor;approvalReason=ExamVenueType.text(reason,"Approval reason");approvedAt=now;status=Status.APPROVED;}
    public void supersede(){if(status!=Status.APPROVED)throw new IllegalStateException("Only an approved requirement can be superseded.");status=Status.SUPERSEDED;}
    public UUID getAcademicPeriodId(){return academicPeriodId;} public UUID getModuleId(){return moduleId;} public String getModuleCode(){return moduleCode;}
    public String getModuleName(){return moduleName;} public int getRequirementVersion(){return requirementVersion;} public int getDurationMinutes(){return durationMinutes;}
    public int getReadingTimeMinutes(){return readingTimeMinutes;} public ExamVenueType getRequiredVenueType(){return requiredVenueType;}
    public String getSpecialRequirements(){return specialRequirements;} public Status getStatus(){return status;}
}

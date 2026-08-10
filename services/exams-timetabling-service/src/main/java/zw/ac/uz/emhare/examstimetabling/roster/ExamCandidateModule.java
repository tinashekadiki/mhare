package zw.ac.uz.emhare.examstimetabling.roster;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent.RegisteredModule;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_candidate_modules") @SQLRestriction("deleted_at IS NULL")
public class ExamCandidateModule extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="registration_import_id")
    private ExamRegistrationImport registrationImport;
    @Column(name="registration_module_id",nullable=false) private UUID registrationModuleId;
    @Column(name="curriculum_module_id",nullable=false) private UUID curriculumModuleId;
    @Column(name="module_id",nullable=false) private UUID moduleId;
    @Column(name="module_code",nullable=false,length=50) private String moduleCode;
    @Column(name="module_name",nullable=false,length=200) private String moduleName;
    @Enumerated(EnumType.STRING) @Column(name="eligibility_status",nullable=false,length=20)
    private EligibilityStatus eligibilityStatus;
    public enum EligibilityStatus { ELIGIBLE, WITHDRAWN }

    protected ExamCandidateModule() {}
    public ExamCandidateModule(ExamRegistrationImport registrationImport, RegisteredModule module) {
        this.registrationImport=registrationImport; registrationModuleId=module.registrationModuleId();
        curriculumModuleId=module.curriculumModuleId(); moduleId=module.moduleId(); moduleCode=module.moduleCode().trim();
        moduleName=module.moduleName().trim(); eligibilityStatus=EligibilityStatus.ELIGIBLE;
    }
    public ExamRegistrationImport getRegistrationImport(){return registrationImport;} public UUID getModuleId(){return moduleId;}
    public String getModuleCode(){return moduleCode;} public String getModuleName(){return moduleName;}
    public EligibilityStatus getEligibilityStatus(){return eligibilityStatus;}
}

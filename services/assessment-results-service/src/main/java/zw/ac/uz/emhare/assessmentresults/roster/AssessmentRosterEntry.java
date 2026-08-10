package zw.ac.uz.emhare.assessmentresults.roster;

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
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent.RegisteredModule;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "assessment_roster_entries")
@SQLRestriction("deleted_at IS NULL")
public class AssessmentRosterEntry extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roster_import_id", nullable = false)
    private RegistrationRosterImport rosterImport;
    @Column(name = "registration_module_id", nullable = false)
    private UUID registrationModuleId;
    @Column(name = "curriculum_module_id", nullable = false)
    private UUID curriculumModuleId;
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
    @Column(name = "minimum_mark_required", precision = 5, scale = 2)
    private BigDecimal minimumMarkRequired;
    @Column(name = "eligibility_status", nullable = false, length = 20)
    private String eligibilityStatus;

    protected AssessmentRosterEntry() {
    }

    public AssessmentRosterEntry(RegistrationRosterImport rosterImport, RegisteredModule module) {
        this.rosterImport = rosterImport;
        this.registrationModuleId = module.registrationModuleId();
        this.curriculumModuleId = module.curriculumModuleId();
        this.moduleId = module.moduleId();
        this.moduleCode = module.moduleCode().trim();
        this.moduleName = module.moduleName().trim();
        this.curriculumModuleType = module.curriculumModuleType().trim();
        this.creditValue = module.creditValue();
        this.minimumMarkRequired = module.minimumMarkRequired();
        this.eligibilityStatus = "ELIGIBLE";
    }

    public UUID getRegistrationModuleId() { return registrationModuleId; }
    public RegistrationRosterImport getRosterImport() { return rosterImport; }
    public UUID getCurriculumModuleId() { return curriculumModuleId; }
    public UUID getModuleId() { return moduleId; }
    public String getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public String getCurriculumModuleType() { return curriculumModuleType; }
    public BigDecimal getCreditValue() { return creditValue; }
    public BigDecimal getMinimumMarkRequired() { return minimumMarkRequired; }
    public String getEligibilityStatus() { return eligibilityStatus; }
}

package zw.ac.uz.emhare.studentrecords.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationModuleOption;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "registration_modules")
@SQLRestriction("deleted_at IS NULL")
public class RegistrationModule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_session_id", nullable = false)
    private RegistrationSession registrationSession;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_source", nullable = false, length = 30)
    private ModuleSelectionSource selectionSource;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RegistrationModule() {
    }

    public RegistrationModule(
            RegistrationSession registrationSession,
            RegistrationModuleOption option,
            ModuleSelectionSource selectionSource) {
        this.registrationSession = registrationSession;
        this.curriculumModuleId = option.curriculumModuleId();
        this.moduleId = option.moduleId();
        this.moduleCode = requireText(option.moduleCode(), "Module code");
        this.moduleName = requireText(option.moduleName(), "Module name");
        this.curriculumModuleType = requireText(option.moduleType(), "Curriculum Module type");
        this.creditValue = option.creditValue();
        this.minimumMarkRequired = option.minimumMarkRequired();
        this.selectionSource = selectionSource;
        this.sortOrder = option.sortOrder();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public UUID getCurriculumModuleId() { return curriculumModuleId; }
    public UUID getModuleId() { return moduleId; }
    public String getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public String getCurriculumModuleType() { return curriculumModuleType; }
    public BigDecimal getCreditValue() { return creditValue; }
    public BigDecimal getMinimumMarkRequired() { return minimumMarkRequired; }
    public ModuleSelectionSource getSelectionSource() { return selectionSource; }
    public int getSortOrder() { return sortOrder; }
}

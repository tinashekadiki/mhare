package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable document rule captured for one application draft. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_document_requirement_snapshots")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationDocumentRequirementSnapshot extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "requirement_code", nullable = false, length = 60) private String requirementCode;
    @Column(name = "requirement_name", nullable = false, length = 160) private String requirementName;
    @Column(name = "is_required", nullable = false) private boolean required;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected ApplicationDocumentRequirementSnapshot() { }

    public ApplicationDocumentRequirementSnapshot(
            Application application, ApplicationTypeDocumentRequirement requirement) {
        this.application = application;
        this.requirementCode = requirement.getRequirementCode();
        this.requirementName = requirement.getRequirementName();
        this.required = requirement.isRequired();
        this.sortOrder = requirement.getSortOrder();
    }

    public String getRequirementCode() { return requirementCode; }
    public String getRequirementName() { return requirementName; }
    public boolean isRequired() { return required; }
    public int getSortOrder() { return sortOrder; }
}

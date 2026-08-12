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

/** Relational group of alternative qualification requirements. @author Tinashe K */
@Audited
@Entity
@Table(name = "admission_qualification_requirement_groups")
@SQLRestriction("deleted_at IS NULL")
public class AdmissionQualificationRequirementGroup extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_set_id", nullable = false)
    private AdmissionRequirementSet requirementSet;
    @Column(name = "group_code", nullable = false, length = 50) private String groupCode;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "minimum_satisfied_items", nullable = false) private int minimumSatisfiedItems;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected AdmissionQualificationRequirementGroup() { }

    public AdmissionQualificationRequirementGroup(
            AdmissionRequirementSet requirementSet, String groupCode, String name,
            int minimumSatisfiedItems, int sortOrder) {
        if (minimumSatisfiedItems < 1) throw new IllegalArgumentException("Qualification group minimum must be positive.");
        this.requirementSet = java.util.Objects.requireNonNull(requirementSet, "Requirement set is required.");
        this.groupCode = required(groupCode, "Qualification group code").toUpperCase(java.util.Locale.ROOT);
        this.name = required(name, "Qualification group name");
        this.minimumSatisfiedItems = minimumSatisfiedItems;
        this.sortOrder = sortOrder;
    }

    public String getGroupCode() { return groupCode; }
    public String getName() { return name; }
    public int getMinimumSatisfiedItems() { return minimumSatisfiedItems; }
    public int getSortOrder() { return sortOrder; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}

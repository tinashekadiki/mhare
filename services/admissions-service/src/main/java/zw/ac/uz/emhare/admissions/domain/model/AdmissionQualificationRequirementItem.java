package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** One qualification alternative within a governed group. @author Tinashe K */
@Audited
@Entity
@Table(name = "admission_qualification_requirement_items")
@SQLRestriction("deleted_at IS NULL")
public class AdmissionQualificationRequirementItem extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_group_id", nullable = false)
    private AdmissionQualificationRequirementGroup requirementGroup;
    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_level", nullable = false, length = 30)
    private QualificationLevel qualificationLevel;
    @Column(name = "minimum_count", nullable = false) private int minimumCount;
    @Column(name = "minimum_total_points", precision = 8, scale = 2) private BigDecimal minimumTotalPoints;
    @Column(name = "minimum_duration_months") private Integer minimumDurationMonths;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected AdmissionQualificationRequirementItem() { }

    public AdmissionQualificationRequirementItem(
            AdmissionQualificationRequirementGroup requirementGroup, QualificationLevel qualificationLevel,
            int minimumCount, BigDecimal minimumTotalPoints, Integer minimumDurationMonths, int sortOrder) {
        if (minimumCount < 1) throw new IllegalArgumentException("Qualification minimum count must be positive.");
        if (minimumTotalPoints != null && minimumTotalPoints.signum() < 0) throw new IllegalArgumentException("Qualification minimum points cannot be negative.");
        if (minimumDurationMonths != null && minimumDurationMonths < 0) throw new IllegalArgumentException("Qualification duration cannot be negative.");
        this.requirementGroup = java.util.Objects.requireNonNull(requirementGroup, "Qualification group is required.");
        this.qualificationLevel = java.util.Objects.requireNonNull(qualificationLevel, "Qualification level is required.");
        this.minimumCount = minimumCount;
        this.minimumTotalPoints = minimumTotalPoints;
        this.minimumDurationMonths = minimumDurationMonths;
        this.sortOrder = sortOrder;
    }

    public QualificationLevel getQualificationLevel() { return qualificationLevel; }
    public int getMinimumCount() { return minimumCount; }
    public BigDecimal getMinimumTotalPoints() { return minimumTotalPoints; }
    public Integer getMinimumDurationMonths() { return minimumDurationMonths; }
    public int getSortOrder() { return sortOrder; }
}

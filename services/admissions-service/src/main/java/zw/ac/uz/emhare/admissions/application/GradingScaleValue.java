package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "grading_scale_values",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grading_scale_values_scale_grade", columnNames = {"grading_scale_id", "grade"}))
public class GradingScaleValue extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_scale_id", nullable = false)
    private GradingScale gradingScale;

    @Column(nullable = false, length = 20)
    private String grade;

    @Column(precision = 8, scale = 2)
    private BigDecimal points;

    @Column(name = "is_pass", nullable = false)
    private boolean pass;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GradingScaleValue() {
    }

    public GradingScaleValue(GradingScale gradingScale, String grade, BigDecimal points, boolean pass, int sortOrder) {
        this.gradingScale = gradingScale;
        this.grade = grade;
        this.points = points;
        this.pass = pass;
        this.sortOrder = sortOrder;
    }

    public GradingScale getGradingScale() {
        return gradingScale;
    }

    public String getGrade() {
        return grade;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public boolean isPass() {
        return pass;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void updateReference(String grade, BigDecimal points, boolean pass, int sortOrder) {
        this.grade = grade;
        this.points = points;
        this.pass = pass;
        this.sortOrder = sortOrder;
    }
}

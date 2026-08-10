package zw.ac.uz.emhare.academicsetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "academic_period_types")
@SQLRestriction("deleted_at IS NULL")
public class AcademicPeriodType extends AuditableEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceStatus status;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    protected AcademicPeriodType() {
    }

    public AcademicPeriodType(String code, String name, int sortOrder) {
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.sortOrder = sortOrder;
        this.status = ReferenceStatus.ACTIVE;
        this.changeReason = "Initial record creation.";
    }

    public void update(String code, String name, int sortOrder, String changeReason, long expectedVersion) {
        requireVersion(expectedVersion);
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.sortOrder = sortOrder;
        this.changeReason = requireChangeReason(changeReason);
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Academic period type was changed by another user. Refresh before retrying.");
        }
    }

    private String requireChangeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10) {
            throw new IllegalArgumentException("Academic calendar change reason must contain at least 10 characters.");
        }
        return normalized;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ReferenceStatus getStatus() {
        return status;
    }

    public String getChangeReason() { return changeReason; }
}

package zw.ac.uz.emhare.academicsetup.domain.model;


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
@Table(name = "academic_unit_types")
@SQLRestriction("deleted_at IS NULL")
public class AcademicUnitType extends AuditableEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "level_order", nullable = false)
    private int levelOrder;

    @Column(name = "is_leaf_allowed", nullable = false)
    private boolean leafAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceStatus status;

    protected AcademicUnitType() {
    }

    public AcademicUnitType(String code, String name, int levelOrder, boolean leafAllowed) {
        this.code = normalizeCode(code);
        this.name = name.trim();
        this.levelOrder = levelOrder;
        this.leafAllowed = leafAllowed;
        this.status = ReferenceStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getLevelOrder() {
        return levelOrder;
    }

    public boolean isLeafAllowed() {
        return leafAllowed;
    }

    public ReferenceStatus getStatus() {
        return status;
    }

    private static String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

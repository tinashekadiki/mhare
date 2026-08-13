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
@Table(name = "programme_levels")
@SQLRestriction("deleted_at IS NULL")
public class ProgrammeLevel extends AuditableEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceStatus status;

    protected ProgrammeLevel() {
    }

    public ProgrammeLevel(String code, String name, int sortOrder) {
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.sortOrder = sortOrder;
        this.status = ReferenceStatus.ACTIVE;
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

    public void update(String name, int sortOrder, long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Programme level was changed by another user. Refresh before retrying.");
        }
        this.name = name.trim();
        this.sortOrder = sortOrder;
    }
}

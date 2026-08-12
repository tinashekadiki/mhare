package zw.ac.uz.emhare.academicsetup.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** A governed specialization or entry preference offered by a programme version. @author Tinashe K */
@Audited
@Entity
@Table(name = "programme_entry_options")
@SQLRestriction("deleted_at IS NULL")
public class ProgrammeEntryOption extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_version_id", nullable = false)
    private ProgrammeVersion programmeVersion;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ProgrammeEntryOption() {
    }

    public ProgrammeEntryOption(
            ProgrammeVersion programmeVersion,
            String code,
            String name,
            String description,
            int sortOrder) {
        if (sortOrder < 1) {
            throw new IllegalArgumentException("Entry-option sort order must be at least one.");
        }
        this.programmeVersion = programmeVersion;
        this.code = required(code, "Entry-option code").toUpperCase(Locale.ROOT);
        this.name = required(name, "Entry-option name");
        this.description = trimToNull(description);
        this.sortOrder = sortOrder;
        this.active = true;
    }

    public ProgrammeVersion getProgrammeVersion() { return programmeVersion; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

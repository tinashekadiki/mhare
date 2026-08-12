package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Explicit programme eligibility for one application route. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_type_programme_mappings")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationTypeProgrammeMapping extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_type_id", nullable = false)
    private ApplicationType applicationType;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "programme_code", nullable = false, length = 50)
    private String programmeCode;

    @Column(name = "programme_name", nullable = false, length = 200)
    private String programmeName;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ApplicationTypeProgrammeMapping() {
    }

    public ApplicationTypeProgrammeMapping(
            ApplicationType applicationType, UUID programmeId, String programmeCode, String programmeName) {
        this.applicationType = applicationType;
        this.programmeId = java.util.Objects.requireNonNull(programmeId, "Programme id is required.");
        this.programmeCode = required(programmeCode, "Programme code").toUpperCase(Locale.ROOT);
        this.programmeName = required(programmeName, "Programme name");
        this.active = true;
    }

    public ApplicationType getApplicationType() { return applicationType; }
    public UUID getProgrammeId() { return programmeId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public boolean isActive() { return active; }

    public void refresh(String programmeCode, String programmeName) {
        this.programmeCode = required(programmeCode, "Programme code").toUpperCase(Locale.ROOT);
        this.programmeName = required(programmeName, "Programme name");
        this.active = true;
    }

    public void deactivate() { active = false; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}

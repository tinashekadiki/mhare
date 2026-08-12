package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Intake-scoped Programme capacity retained for planning and reporting only. @author Tinashe K */
@Audited
@Entity
@Table(name = "admission_quotas")
@SQLRestriction("deleted_at IS NULL")
public class AdmissionQuota extends AuditableEntity {

    @Column(name = "intake_id", nullable = false)
    private UUID intakeId;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "programme_code", nullable = false, length = 50)
    private String programmeCode;

    @Column(name = "programme_name", nullable = false, length = 200)
    private String programmeName;

    @Column(name = "quota_type_code", nullable = false, length = 50)
    private String quotaTypeCode;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "reserved_capacity", nullable = false)
    private int reservedCapacity;

    protected AdmissionQuota() {
    }

    public AdmissionQuota(
            UUID intakeId,
            UUID programmeId,
            String programmeCode,
            String programmeName,
            String quotaTypeCode,
            int capacity,
            int reservedCapacity) {
        this.intakeId = Objects.requireNonNull(intakeId, "Intake id is required.");
        this.programmeId = Objects.requireNonNull(programmeId, "Programme id is required.");
        configure(programmeCode, programmeName, quotaTypeCode, capacity, reservedCapacity, 0, true);
    }

    public void configure(
            String programmeCode,
            String programmeName,
            String quotaTypeCode,
            int capacity,
            int reservedCapacity,
            long expectedVersion) {
        configure(programmeCode, programmeName, quotaTypeCode, capacity, reservedCapacity, expectedVersion, false);
    }

    private void configure(
            String programmeCode,
            String programmeName,
            String quotaTypeCode,
            int capacity,
            int reservedCapacity,
            long expectedVersion,
            boolean creating) {
        if (!creating && getVersion() != expectedVersion) {
            throw new IllegalStateException("Programme quota was changed by another user. Refresh before retrying.");
        }
        if (capacity < 1) throw new IllegalArgumentException("Programme quota capacity must be greater than zero.");
        if (reservedCapacity < 0 || reservedCapacity > capacity) {
            throw new IllegalArgumentException("Reserved Programme capacity must be between zero and total capacity.");
        }
        this.programmeCode = required(programmeCode, "Programme code").toUpperCase(Locale.ROOT);
        this.programmeName = required(programmeName, "Programme name");
        this.quotaTypeCode = required(quotaTypeCode, "Quota type code").toUpperCase(Locale.ROOT);
        this.capacity = capacity;
        this.reservedCapacity = reservedCapacity;
    }

    public UUID getIntakeId() { return intakeId; }
    public UUID getProgrammeId() { return programmeId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public String getQuotaTypeCode() { return quotaTypeCode; }
    public int getCapacity() { return capacity; }
    public int getReservedCapacity() { return reservedCapacity; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}

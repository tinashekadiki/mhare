package zw.ac.uz.emhare.academicsetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "programme_versions")
@SQLRestriction("deleted_at IS NULL")
public class ProgrammeVersion extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id", nullable = false)
    private Programme programme;

    @Column(name = "version_code", nullable = false, length = 40)
    private String versionCode;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgrammeVersionStatus status;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ProgrammeVersion() {
    }

    public ProgrammeVersion(Programme programme, String versionCode, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.programme = programme;
        this.versionCode = versionCode.trim().toUpperCase(Locale.ROOT);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = ProgrammeVersionStatus.DRAFT;
    }

    public void approve(UUID actorUserId, Instant approvalTime, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != ProgrammeVersionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft programme version can be approved.");
        }
        status = ProgrammeVersionStatus.APPROVED;
        approvedByUserId = actorUserId;
        approvedAt = approvalTime;
    }

    public void retire(LocalDate retirementDate, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != ProgrammeVersionStatus.APPROVED) {
            throw new IllegalStateException("Only an approved programme version can be retired.");
        }
        if (retirementDate.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("Retirement date cannot be before the version effective date.");
        }
        effectiveTo = retirementDate;
        status = ProgrammeVersionStatus.RETIRED;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Programme version was changed by another user. Refresh before retrying.");
        }
    }

    public Programme getProgramme() {
        return programme;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public ProgrammeVersionStatus getStatus() {
        return status;
    }

    public UUID getApprovedByUserId() {
        return approvedByUserId;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}

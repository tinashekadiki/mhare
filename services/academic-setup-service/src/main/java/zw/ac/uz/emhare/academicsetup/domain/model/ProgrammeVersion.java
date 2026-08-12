package zw.ac.uz.emhare.academicsetup.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    @Column(name = "minimum_entry_option_selections", nullable = false)
    private int minimumEntryOptionSelections;

    @Column(name = "maximum_entry_option_selections", nullable = false)
    private int maximumEntryOptionSelections;

    @OneToMany(mappedBy = "programmeVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgrammeEntryOption> entryOptions = new ArrayList<>();

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

    public void configureEntryOptions(
            int minimumSelections,
            int maximumSelections,
            List<EntryOptionDefinition> definitions,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != ProgrammeVersionStatus.DRAFT) {
            throw new IllegalStateException("Entry options can only be changed on a draft programme version.");
        }
        if (minimumSelections < 0 || maximumSelections < minimumSelections) {
            throw new IllegalArgumentException("Entry-option selection limits are inconsistent.");
        }
        List<EntryOptionDefinition> safeDefinitions = definitions == null ? List.of() : definitions;
        if (maximumSelections > safeDefinitions.size()) {
            throw new IllegalArgumentException("Maximum entry-option selections cannot exceed the configured options.");
        }
        long distinctCodes = safeDefinitions.stream().map(definition -> definition.code().trim().toUpperCase(Locale.ROOT)).distinct().count();
        long distinctSortOrders = safeDefinitions.stream().map(EntryOptionDefinition::sortOrder).distinct().count();
        if (distinctCodes != safeDefinitions.size() || distinctSortOrders != safeDefinitions.size()) {
            throw new IllegalArgumentException("Entry-option codes and sort orders must be unique.");
        }
        entryOptions.clear();
        safeDefinitions.forEach(definition -> entryOptions.add(new ProgrammeEntryOption(
                this, definition.code(), definition.name(), definition.description(), definition.sortOrder())));
        minimumEntryOptionSelections = minimumSelections;
        maximumEntryOptionSelections = maximumSelections;
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

    public int getMinimumEntryOptionSelections() { return minimumEntryOptionSelections; }
    public int getMaximumEntryOptionSelections() { return maximumEntryOptionSelections; }
    public List<ProgrammeEntryOption> getEntryOptions() {
        return entryOptions.stream().filter(ProgrammeEntryOption::isActive)
                .sorted(Comparator.comparingInt(ProgrammeEntryOption::getSortOrder)).toList();
    }

    public record EntryOptionDefinition(String code, String name, String description, int sortOrder) { }
}

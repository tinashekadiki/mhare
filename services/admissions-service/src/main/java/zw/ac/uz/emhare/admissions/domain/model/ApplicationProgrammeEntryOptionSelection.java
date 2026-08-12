package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Ranked entry-option preference captured against an immutable programme snapshot. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_programme_entry_option_selections")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationProgrammeEntryOptionSelection extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_choice_id", nullable = false)
    private ApplicationProgrammeChoice programmeChoice;
    @Column(name = "entry_option_id", nullable = false) private UUID entryOptionId;
    @Column(name = "entry_option_code", nullable = false, length = 50) private String entryOptionCode;
    @Column(name = "entry_option_name", nullable = false, length = 200) private String entryOptionName;
    @Column(name = "preference_rank", nullable = false) private int preferenceRank;

    protected ApplicationProgrammeEntryOptionSelection() { }

    public ApplicationProgrammeEntryOptionSelection(
            ApplicationProgrammeChoice programmeChoice, UUID entryOptionId,
            String entryOptionCode, String entryOptionName, int preferenceRank) {
        if (preferenceRank < 1) throw new IllegalArgumentException("Entry-option preference rank must be positive.");
        this.programmeChoice = programmeChoice;
        this.entryOptionId = java.util.Objects.requireNonNull(entryOptionId, "Entry-option id is required.");
        this.entryOptionCode = required(entryOptionCode, "Entry-option code");
        this.entryOptionName = required(entryOptionName, "Entry-option name");
        this.preferenceRank = preferenceRank;
    }

    public UUID getEntryOptionId() { return entryOptionId; }
    public String getEntryOptionCode() { return entryOptionCode; }
    public String getEntryOptionName() { return entryOptionName; }
    public int getPreferenceRank() { return preferenceRank; }
    public UUID getProgrammeChoiceId() { return programmeChoice.getId(); }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}

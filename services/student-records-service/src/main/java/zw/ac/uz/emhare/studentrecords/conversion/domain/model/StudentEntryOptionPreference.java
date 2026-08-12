package zw.ac.uz.emhare.studentrecords.conversion.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Admission preference snapshot; it does not assign curriculum Modules. @author Tinashe K */
@Audited
@Entity
@Table(name = "student_entry_option_preferences")
@SQLRestriction("deleted_at IS NULL")
public class StudentEntryOptionPreference extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_enrolment_id", nullable = false)
    private StudentProgrammeEnrolment programmeEnrolment;
    @Column(name = "entry_option_id", nullable = false) private UUID entryOptionId;
    @Column(name = "entry_option_code", nullable = false, length = 50) private String entryOptionCode;
    @Column(name = "entry_option_name", nullable = false, length = 200) private String entryOptionName;
    @Column(name = "preference_rank", nullable = false) private int preferenceRank;

    protected StudentEntryOptionPreference() { }

    public StudentEntryOptionPreference(
            StudentProgrammeEnrolment programmeEnrolment,
            AcceptedOfferReadyForConversionEvent.EntryOptionPreference preference) {
        this.programmeEnrolment = java.util.Objects.requireNonNull(programmeEnrolment, "Programme enrolment is required.");
        this.entryOptionId = java.util.Objects.requireNonNull(preference.entryOptionId(), "Entry option id is required.");
        this.entryOptionCode = required(preference.entryOptionCode(), "Entry option code");
        this.entryOptionName = required(preference.entryOptionName(), "Entry option name");
        if (preference.preferenceRank() < 1) throw new IllegalArgumentException("Entry option rank must be positive.");
        this.preferenceRank = preference.preferenceRank();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}

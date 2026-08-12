package zw.ac.uz.emhare.accommodation.setup.domain.model;

import zw.ac.uz.emhare.accommodation.setup.*;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "residence_halls")
@SQLRestriction("deleted_at IS NULL")
public class ResidenceHall extends AuditableEntity {
    public enum ResidentGenderPolicy { ANY, FEMALE, MALE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "premise_id")
    private AccommodationPremise premise;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "resident_gender_policy", nullable = false, length = 20)
    private ResidentGenderPolicy residentGenderPolicy;
    @Column(name = "warden_name", length = 160) private String wardenName;
    @Column(name = "warden_contact", length = 160) private String wardenContact;
    @Column(nullable = false) private boolean active;

    protected ResidenceHall() {}

    public ResidenceHall(AccommodationPremise premise, String code, String name,
            ResidentGenderPolicy residentGenderPolicy, String wardenName, String wardenContact) {
        updateValues(premise, code, name, residentGenderPolicy, wardenName, wardenContact, true);
    }

    public void update(AccommodationPremise premise, String code, String name,
            ResidentGenderPolicy residentGenderPolicy, String wardenName, String wardenContact,
            boolean active, long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("The record was changed by another operator. Refresh and try again.");
        updateValues(premise, code, name, residentGenderPolicy, wardenName, wardenContact, active);
    }

    private void updateValues(AccommodationPremise premise, String code, String name,
            ResidentGenderPolicy residentGenderPolicy, String wardenName, String wardenContact, boolean active) {
        if (premise == null) throw new IllegalArgumentException("Accommodation premise is required.");
        if (!premise.isActive()) throw new IllegalArgumentException("Residence halls can only be assigned to active premises.");
        this.premise = premise;
        this.code = AccommodationPremise.required(code, "Residence hall code").toUpperCase();
        this.name = AccommodationPremise.required(name, "Residence hall name");
        this.residentGenderPolicy = residentGenderPolicy == null ? ResidentGenderPolicy.ANY : residentGenderPolicy;
        this.wardenName = AccommodationPremise.optional(wardenName);
        this.wardenContact = AccommodationPremise.optional(wardenContact);
        this.active = active;
    }

    public AccommodationPremise getPremise() { return premise; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ResidentGenderPolicy getResidentGenderPolicy() { return residentGenderPolicy; }
    public String getWardenName() { return wardenName; }
    public String getWardenContact() { return wardenContact; }
    public boolean isActive() { return active; }
}

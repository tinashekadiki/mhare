package zw.ac.uz.emhare.accommodation.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_premises")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationPremise extends AuditableEntity {
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "address_line", nullable = false, length = 300) private String addressLine;
    @Column(length = 120) private String suburb;
    @Column(name = "landlord_name", length = 160) private String landlordName;
    @Column(name = "contact_details", length = 500) private String contactDetails;
    @Column(nullable = false) private boolean active;

    protected AccommodationPremise() {}

    public AccommodationPremise(String code, String name, String addressLine, String suburb,
            String landlordName, String contactDetails) {
        updateValues(code, name, addressLine, suburb, landlordName, contactDetails, true);
    }

    public void update(String code, String name, String addressLine, String suburb, String landlordName,
            String contactDetails, boolean active, long expectedVersion) {
        requireVersion(expectedVersion);
        updateValues(code, name, addressLine, suburb, landlordName, contactDetails, active);
    }

    private void updateValues(String code, String name, String addressLine, String suburb,
            String landlordName, String contactDetails, boolean active) {
        this.code = required(code, "Premise code").toUpperCase();
        this.name = required(name, "Premise name");
        this.addressLine = required(addressLine, "Address");
        this.suburb = optional(suburb);
        this.landlordName = optional(landlordName);
        this.contactDetails = optional(contactDetails);
        this.active = active;
    }

    static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("The record was changed by another operator. Refresh and try again.");
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getAddressLine() { return addressLine; }
    public String getSuburb() { return suburb; }
    public String getLandlordName() { return landlordName; }
    public String getContactDetails() { return contactDetails; }
    public boolean isActive() { return active; }
}

package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "applicant_next_of_kin")
public class ApplicantNextOfKin extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "relationship_code", nullable = false, length = 50)
    private String relationshipCode;

    @Column(name = "phone_number", nullable = false, length = 50)
    private String phoneNumber;

    @Column(length = 200)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected ApplicantNextOfKin() {
    }

    public ApplicantNextOfKin(Applicant applicant, String fullName, String relationshipCode, String phoneNumber,
                              String email, String address, boolean primary) {
        this.applicant = applicant;
        update(fullName, relationshipCode, phoneNumber, email, address, primary);
    }

    public void update(String fullName, String relationshipCode, String phoneNumber,
                       String email, String address, boolean primary) {
        this.fullName = required(fullName, "Next-of-kin name");
        this.relationshipCode = required(relationshipCode, "Relationship").toUpperCase(Locale.ROOT);
        this.phoneNumber = required(phoneNumber, "Phone number");
        this.email = optional(email);
        this.address = optional(address);
        this.primary = primary;
    }

    public Applicant getApplicant() { return applicant; }
    public String getFullName() { return fullName; }
    public String getRelationshipCode() { return relationshipCode; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public boolean isPrimary() { return primary; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

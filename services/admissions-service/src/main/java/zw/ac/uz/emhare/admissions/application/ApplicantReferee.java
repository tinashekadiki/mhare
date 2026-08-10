package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "applicant_referees")
public class ApplicantReferee extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String organisation;

    @Column(name = "position_title", length = 150)
    private String positionTitle;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private RefereeVerificationStatus verificationStatus;

    @Column(name = "reference_document_id")
    private UUID referenceDocumentId;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    protected ApplicantReferee() {
    }

    public ApplicantReferee(Applicant applicant, String fullName, String title, String organisation,
                            String positionTitle, String email, String phoneNumber) {
        this.applicant = applicant;
        update(fullName, title, organisation, positionTitle, email, phoneNumber);
        this.verificationStatus = RefereeVerificationStatus.PENDING;
    }

    public void update(String fullName, String title, String organisation,
                       String positionTitle, String email, String phoneNumber) {
        if (verificationStatus == RefereeVerificationStatus.VERIFIED) {
            throw new IllegalStateException("A verified referee cannot be edited without a staff correction.");
        }
        this.fullName = required(fullName, "Referee name");
        this.title = optional(title);
        this.organisation = required(organisation, "Referee organisation");
        this.positionTitle = optional(positionTitle);
        this.email = required(email, "Referee email").toLowerCase(java.util.Locale.ROOT);
        this.phoneNumber = optional(phoneNumber);
        this.verificationStatus = RefereeVerificationStatus.PENDING;
        this.verifiedByUserId = null;
        this.verifiedAt = null;
        this.rejectionReason = null;
    }

    public Applicant getApplicant() { return applicant; }
    public String getFullName() { return fullName; }
    public String getTitle() { return title; }
    public String getOrganisation() { return organisation; }
    public String getPositionTitle() { return positionTitle; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public RefereeVerificationStatus getVerificationStatus() { return verificationStatus; }
    public UUID getReferenceDocumentId() { return referenceDocumentId; }
    public String getRejectionReason() { return rejectionReason; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

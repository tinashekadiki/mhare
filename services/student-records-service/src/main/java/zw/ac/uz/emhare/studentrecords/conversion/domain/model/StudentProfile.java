package zw.ac.uz.emhare.studentrecords.conversion.domain.model;

import zw.ac.uz.emhare.studentrecords.conversion.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "students")
public class StudentProfile extends AuditableEntity {

    @Column(name = "student_number", nullable = false, length = 40)
    private String studentNumber;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "source_applicant_id", nullable = false)
    private UUID sourceApplicantId;
    @Column(name = "source_applicant_number", nullable = false, length = 40)
    private String sourceApplicantNumber;
    @Column(name = "source_application_id", nullable = false)
    private UUID sourceApplicationId;
    @Column(name = "source_offer_id", nullable = false)
    private UUID sourceOfferId;
    @Column(name = "applicant_category_code", nullable = false, length = 30)
    private String applicantCategoryCode;
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    @Column(name = "middle_names", length = 150)
    private String middleNames;
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;
    @Column(name = "gender_code", length = 30)
    private String genderCode;
    @Column(name = "national_id_number", length = 50)
    private String nationalIdNumber;
    @Column(name = "passport_number", length = 50)
    private String passportNumber;
    @Column(name = "primary_email", nullable = false, length = 200)
    private String primaryEmail;
    @Column(name = "primary_phone", length = 50)
    private String primaryPhone;
    @Column(name = "postal_address", length = 500)
    private String postalAddress;
    @Column(name = "residential_address", length = 500)
    private String residentialAddress;
    @Column(name = "disability_status_code", length = 30)
    private String disabilityStatusCode;
    @Column(name = "special_needs", length = 1000)
    private String specialNeeds;
    @Column(name = "sponsor_type_code", length = 30)
    private String sponsorTypeCode;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sponsor_details", columnDefinition = "jsonb")
    private String sponsorDetails;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudentStatus status;
    @Column(name = "activated_at")
    private Instant activatedAt;

    protected StudentProfile() {
    }

    public StudentProfile(String studentNumber, AcceptedOfferReadyForConversionEvent event) {
        this.studentNumber = requireText(studentNumber, "Student number");
        this.userId = event.applicantUserId();
        this.sourceApplicantId = event.applicantId();
        this.sourceApplicantNumber = requireText(event.applicantNumber(), "Applicant number");
        this.sourceApplicationId = event.applicationId();
        this.sourceOfferId = event.offerId();
        this.applicantCategoryCode = requireText(event.applicantCategoryCode(), "Applicant category");
        this.firstName = requireText(event.firstName(), "First name");
        this.lastName = requireText(event.lastName(), "Last name");
        this.primaryEmail = requireText(event.primaryEmail(), "Primary email");
        this.status = StudentStatus.PROVISIONING;
    }

    public void activate(Instant now) {
        if (status != StudentStatus.PROVISIONING) {
            throw new IllegalStateException("Only a provisioning student can be activated.");
        }
        status = StudentStatus.ACTIVE;
        activatedAt = now;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public String getStudentNumber() { return studentNumber; }
    public UUID getUserId() { return userId; }
    public UUID getSourceApplicationId() { return sourceApplicationId; }
    public UUID getSourceOfferId() { return sourceOfferId; }
    public String getFirstName() { return firstName; }
    public String getMiddleNames() { return middleNames; }
    public String getLastName() { return lastName; }
    public String getPrimaryEmail() { return primaryEmail; }
    public String getPrimaryPhone() { return primaryPhone; }
    public java.time.LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGenderCode() { return genderCode; }
    public String getDisabilityStatusCode() { return disabilityStatusCode; }
    public StudentStatus getStatus() { return status; }
    public Instant getActivatedAt() { return activatedAt; }
    public boolean isActive() { return status == StudentStatus.ACTIVE; }
}

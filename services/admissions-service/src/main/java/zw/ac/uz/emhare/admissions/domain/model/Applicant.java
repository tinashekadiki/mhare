package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "applicants",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_applicants_user_id", columnNames = "user_id"),
      @UniqueConstraint(name = "uk_applicants_applicant_number", columnNames = "applicant_number")
    })
public class Applicant extends AuditableEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "applicant_number", nullable = false, length = 40)
  private String applicantNumber;

  @Column(name = "applicant_category_code", nullable = false, length = 30)
  private String applicantCategoryCode;

  @Column(name = "title_code", length = 30)
  private String titleCode;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "middle_names", length = 150)
  private String middleNames;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "gender_code", length = 30)
  private String genderCode;

  @Column(name = "marital_status_code", length = 30)
  private String maritalStatusCode;

  @Column(name = "national_id_number", length = 50)
  private String nationalIdNumber;

  @Column(name = "passport_number", length = 50)
  private String passportNumber;

  @Column(name = "country_id")
  private UUID countryId;

  @Column(name = "nationality_country_id")
  private UUID nationalityCountryId;

  @Column(name = "place_of_birth", length = 150)
  private String placeOfBirth;

  @Column(name = "disability_status_code", length = 30)
  private String disabilityStatusCode;

  @Column(name = "special_needs", length = 1000)
  private String specialNeeds;

  @Column(name = "sponsor_type_code", length = 30)
  private String sponsorTypeCode;

  @Column(name = "primary_email", nullable = false, length = 200)
  private String primaryEmail;

  @Column(name = "primary_phone", length = 50)
  private String primaryPhone;

  @Column(name = "postal_address", length = 500)
  private String postalAddress;

  @Column(name = "residential_address", length = 500)
  private String residentialAddress;

  @Column(name = "legacy_applicants_detail_id")
  private Long legacyApplicantsDetailId;

  protected Applicant() {}

  public Applicant(
      UUID userId,
      String applicantNumber,
      String applicantCategoryCode,
      String firstName,
      String lastName,
      String primaryEmail) {
    this.userId = userId;
    this.applicantNumber = applicantNumber;
    this.applicantCategoryCode = applicantCategoryCode;
    this.firstName = firstName;
    this.lastName = lastName;
    this.primaryEmail = primaryEmail;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getApplicantNumber() {
    return applicantNumber;
  }

  public String getApplicantCategoryCode() {
    return applicantCategoryCode;
  }

  public String getTitleCode() {
    return titleCode;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getMiddleNames() {
    return middleNames;
  }

  public String getLastName() {
    return lastName;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getGenderCode() {
    return genderCode;
  }

  public String getMaritalStatusCode() {
    return maritalStatusCode;
  }

  public String getPrimaryEmail() {
    return primaryEmail;
  }

  public String getNationalIdNumber() {
    return nationalIdNumber;
  }

  public String getPassportNumber() {
    return passportNumber;
  }

  public UUID getCountryId() {
    return countryId;
  }

  public UUID getNationalityCountryId() {
    return nationalityCountryId;
  }

  public String getPlaceOfBirth() {
    return placeOfBirth;
  }

  public String getDisabilityStatusCode() {
    return disabilityStatusCode;
  }

  public String getSpecialNeeds() {
    return specialNeeds;
  }

  public String getSponsorTypeCode() {
    return sponsorTypeCode;
  }

  public String getPrimaryPhone() {
    return primaryPhone;
  }

  public String getPostalAddress() {
    return postalAddress;
  }

  public String getResidentialAddress() {
    return residentialAddress;
  }

  public String getDisplayName() {
    return (firstName + " " + (middleNames == null ? "" : middleNames + " ") + lastName).trim();
  }

  public void recordNationalIdNumber(String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    this.nationalIdNumber = value.trim();
  }

  public void synchronizeRegisteredName(String registeredFirstName, String registeredLastName) {
    firstName = requiredText(registeredFirstName, "First name");
    lastName = requiredText(registeredLastName, "Last name");
  }

  public void synchronizeApprovedOfficialName(
      String approvedFirstName, String approvedMiddleNames, String approvedLastName) {
    firstName = requiredText(approvedFirstName, "First name");
    middleNames = optionalText(approvedMiddleNames);
    lastName = requiredText(approvedLastName, "Last name");
  }

  public void correctProfile(ApplicantProfileCorrection correction) {
    if (getVersion() != correction.expectedVersion()) {
      throw new IllegalStateException(
          "Applicant profile was changed by another user. Refresh before retrying.");
    }
    applicantCategoryCode = ApplicantCategoryCode.from(correction.applicantCategoryCode()).name();
    titleCode = optionalCode(correction.titleCode());
    firstName = requiredText(correction.firstName(), "First name");
    middleNames = optionalText(correction.middleNames());
    lastName = requiredText(correction.lastName(), "Last name");
    dateOfBirth = correction.dateOfBirth();
    genderCode = optionalCode(correction.genderCode());
    maritalStatusCode = optionalCode(correction.maritalStatusCode());
    nationalIdNumber = optionalText(correction.nationalIdNumber());
    passportNumber = optionalText(correction.passportNumber());
    countryId = correction.countryId();
    nationalityCountryId = correction.nationalityCountryId();
    placeOfBirth = optionalText(correction.placeOfBirth());
    disabilityStatusCode = optionalCode(correction.disabilityStatusCode());
    specialNeeds = optionalText(correction.specialNeeds());
    sponsorTypeCode = optionalCode(correction.sponsorTypeCode());
    primaryEmail =
        requiredText(correction.primaryEmail(), "Primary email").toLowerCase(Locale.ROOT);
    primaryPhone = optionalText(correction.primaryPhone());
    postalAddress = optionalText(correction.postalAddress());
    residentialAddress = optionalText(correction.residentialAddress());
  }

  private static String requiredText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required.");
    }
    return value.trim();
  }

  private static String optionalText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String optionalCode(String value) {
    String normalizedValue = optionalText(value);
    return normalizedValue == null ? null : normalizedValue.toUpperCase(Locale.ROOT);
  }
}

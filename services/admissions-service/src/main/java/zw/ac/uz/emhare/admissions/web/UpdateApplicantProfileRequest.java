package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** @author Tinashe K */
public record UpdateApplicantProfileRequest(
        @NotBlank @Size(max = 30) String applicantCategoryCode,
        @Size(max = 30) String titleCode,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 150) String middleNames,
        @NotBlank @Size(max = 100) String lastName,
        @Past LocalDate dateOfBirth,
        @Size(max = 30) String genderCode,
        @Size(max = 30) String maritalStatusCode,
        @Size(max = 50) String nationalIdNumber,
        @Size(max = 50) String passportNumber,
        UUID countryId,
        UUID nationalityCountryId,
        @Size(max = 150) String placeOfBirth,
        @Size(max = 30) String disabilityStatusCode,
        @Size(max = 1000) String specialNeeds,
        @Size(max = 30) String sponsorTypeCode,
        @NotBlank @Email @Size(max = 200) String primaryEmail,
        @Size(max = 50) String primaryPhone,
        @Size(max = 500) String postalAddress,
        @Size(max = 500) String residentialAddress,
        @NotBlank @Size(min = 10, max = 500) String changeReason,
        @Min(0) long expectedVersion) {
}

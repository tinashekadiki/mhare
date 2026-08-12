package zw.ac.uz.emhare.admissions.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/** Domain values required to apply an audited applicant profile correction. @author Tinashe K */
public record ApplicantProfileCorrection(
        String applicantCategoryCode,
        String titleCode,
        String firstName,
        String middleNames,
        String lastName,
        LocalDate dateOfBirth,
        String genderCode,
        String maritalStatusCode,
        String nationalIdNumber,
        String passportNumber,
        UUID countryId,
        UUID nationalityCountryId,
        String placeOfBirth,
        String disabilityStatusCode,
        String specialNeeds,
        String sponsorTypeCode,
        String primaryEmail,
        String primaryPhone,
        String postalAddress,
        String residentialAddress,
        long expectedVersion) {
}

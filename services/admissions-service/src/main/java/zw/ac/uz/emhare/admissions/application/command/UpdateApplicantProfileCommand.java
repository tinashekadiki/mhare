package zw.ac.uz.emhare.admissions.application.command;

import java.time.LocalDate;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantProfileCorrection;

/** Staff-governed correction of an applicant-owned identity profile. @author Tinashe K */
public record UpdateApplicantProfileCommand(
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
        String changeReason,
        long expectedVersion) {

    public ApplicantProfileCorrection toProfileCorrection() {
        return new ApplicantProfileCorrection(
                applicantCategoryCode,
                titleCode,
                firstName,
                middleNames,
                lastName,
                dateOfBirth,
                genderCode,
                maritalStatusCode,
                nationalIdNumber,
                passportNumber,
                countryId,
                nationalityCountryId,
                placeOfBirth,
                disabilityStatusCode,
                specialNeeds,
                sponsorTypeCode,
                primaryEmail,
                primaryPhone,
                postalAddress,
                residentialAddress,
                expectedVersion);
    }
}

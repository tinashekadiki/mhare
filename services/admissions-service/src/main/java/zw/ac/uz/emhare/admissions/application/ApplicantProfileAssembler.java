package zw.ac.uz.emhare.admissions.application;

import java.util.ArrayList;
import java.util.List;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantProfile;

/** Pure applicant profile projection and category-aware completeness rules. @author Tinashe K */
final class ApplicantProfileAssembler {

    private ApplicantProfileAssembler() {
    }

    static ApplicantProfile profile(Applicant applicant) {
        List<String> missingFields = new ArrayList<>();
        requireValue(missingFields, applicant.getTitleCode(), "Title");
        requireValue(missingFields, applicant.getFirstName(), "First name");
        requireValue(missingFields, applicant.getLastName(), "Last name");
        if (applicant.getDateOfBirth() == null) missingFields.add("Date of birth");
        requireValue(missingFields, applicant.getGenderCode(), "Gender");
        boolean internationalApplicant = ApplicantCategoryCode.INTERNATIONAL.name()
                .equals(applicant.getApplicantCategoryCode());
        requireValue(
                missingFields,
                internationalApplicant ? applicant.getPassportNumber() : applicant.getNationalIdNumber(),
                internationalApplicant ? "Passport number" : "National ID number");
        if (applicant.getNationalityCountryId() == null) missingFields.add("Nationality");
        requireValue(missingFields, applicant.getPrimaryEmail(), "Primary email");
        requireValue(missingFields, applicant.getPrimaryPhone(), "Primary phone");
        requireValue(missingFields, applicant.getResidentialAddress(), "Residential address");
        int requiredFieldCount = 10;
        int percentage = Math.max(0, Math.round(((requiredFieldCount - missingFields.size()) * 100.0f) / requiredFieldCount));
        return new ApplicantProfile(
                applicant.getId(), applicant.getUserId(), applicant.getApplicantNumber(), applicant.getApplicantCategoryCode(),
                applicant.getTitleCode(), applicant.getFirstName(), applicant.getMiddleNames(), applicant.getLastName(),
                applicant.getDateOfBirth(), applicant.getGenderCode(), applicant.getMaritalStatusCode(),
                applicant.getNationalIdNumber(), applicant.getPassportNumber(), applicant.getCountryId(),
                applicant.getNationalityCountryId(), applicant.getPlaceOfBirth(), applicant.getDisabilityStatusCode(),
                applicant.getSpecialNeeds(), applicant.getSponsorTypeCode(), applicant.getPrimaryEmail(),
                applicant.getPrimaryPhone(), applicant.getPostalAddress(), applicant.getResidentialAddress(),
                percentage, List.copyOf(missingFields), applicant.getCreatedAt(), applicant.getUpdatedAt(), applicant.getVersion());
    }

    private static void requireValue(List<String> missingFields, String value, String label) {
        if (value == null || value.isBlank()) missingFields.add(label);
    }
}

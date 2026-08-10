package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Staff-facing applicant register and profile contracts. @author Tinashe K */
public final class ApplicantViews {

    private ApplicantViews() {
    }

    public record ApplicantRegisterPage(
            List<ApplicantRegisterRow> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record ApplicantRegisterRow(
            UUID id,
            String applicantNumber,
            String displayName,
            String applicantCategoryCode,
            String primaryEmail,
            String primaryPhone,
            int profileCompletenessPercentage,
            int applicationCount,
            String latestApplicationNumber,
            String latestApplicationStatus,
            String latestIntakeCode,
            Instant updatedAt,
            long version) {
    }

    public record ApplicantProfile(
            UUID id,
            UUID userId,
            String applicantNumber,
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
            int completenessPercentage,
            List<String> missingRequiredFields,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record ApplicantDetails(
            ApplicantProfile profile,
            List<ApplicationSummary> applications) {
    }
}

package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Applicant-workspace request contracts. @author Tinashe K */
public final class ApplicantWorkspaceRequests {
    private ApplicantWorkspaceRequests() {
    }

    public record SaveOwnProfileRequest(
            @NotBlank @Size(max = 30) String applicantCategoryCode,
            @Size(max = 30) String titleCode,
            @Size(max = 150) String middleNames,
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
            @Min(0) long expectedVersion) {
    }

    public record SaveNextOfKinRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 50) String relationshipCode,
            @NotBlank @Size(max = 50) String phoneNumber,
            @Email @Size(max = 200) String email,
            @Size(max = 500) String address,
            boolean primary,
            @Min(0) long expectedVersion) {
    }

    public record SaveEmploymentRequest(
            @NotBlank @Size(max = 200) String employerName,
            @NotBlank @Size(max = 150) String positionTitle,
            @NotNull LocalDate startedOn,
            LocalDate endedOn,
            boolean current,
            @Size(max = 2000) String responsibilities,
            @Min(0) long expectedVersion) {
    }

    public record SaveRefereeRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 100) String title,
            @NotBlank @Size(max = 200) String organisation,
            @Size(max = 150) String positionTitle,
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 50) String phoneNumber,
            @Min(0) long expectedVersion) {
    }

    public record SaveQualificationSittingRequest(
            @NotBlank @Size(max = 30) String level,
            UUID examBodyId,
            @Size(max = 200) String institutionName,
            @Size(max = 50) String centreNumber,
            @Size(max = 50) String candidateNumber,
            @Min(1900) @Max(2200) Integer yearWritten,
            UUID countryId,
            UUID documentId,
            @Min(0) long expectedVersion) {
    }

    public record SaveQualificationResultRequest(
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 20) String grade,
            Boolean principalSubject,
            @Min(0) long expectedVersion) {
    }

    public record AddQualificationResultsRequest(
            @NotNull @Size(min = 1, max = 20) List<@Valid AddQualificationResultItemRequest> results) {
    }

    public record AddQualificationResultItemRequest(
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 20) String grade,
            Boolean principalSubject) {
    }

    public record ReplaceProgrammeChoicesRequest(
            @NotNull @Size(min = 1, max = 20) List<@NotNull UUID> programmeIds,
            @Size(min = 10, max = 500) String changeReason) {
    }

    public record AcceptDeclarationRequest(
            boolean accepted,
            @NotBlank @Size(max = 50) String declarationVersion) {
    }

    public record QualificationDecisionRequest(
            @NotBlank String decision,
            @Size(max = 1000) String reason,
            @Min(0) long expectedVersion) {
    }
}

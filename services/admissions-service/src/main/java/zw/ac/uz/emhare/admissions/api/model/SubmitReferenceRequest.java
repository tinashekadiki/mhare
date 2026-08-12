package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation;

/** Referee response submitted through the public token-protected API. @author Tinashe K */
public record SubmitReferenceRequest(
        @NotBlank @Size(max = 200) String relationshipToApplicant,
        @Min(0) @Max(100) int yearsKnown,
        @NotNull ApplicantRefereeInvitation.Recommendation recommendation,
        @NotBlank @Size(max = 5000) String comments,
        @AssertTrue boolean declarationAccepted) {
}

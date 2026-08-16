package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Direct rolling-admissions command bodies. @author Tinashe K */
public final class RollingAdmissionsRequests {
    private RollingAdmissionsRequests() { }

    public record EligibilityResolutionRequest(
            @NotBlank String outcome,
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record AcademicRecommendationRequest(
            @NotBlank String recommendation,
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record RecommendationReturnRequest(
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record AdmissionDecisionRequest(
            @NotBlank String decision,
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record UpdateOfferRequest(
            @NotBlank String offerType,
            @Size(max = 4000) String conditionsText) { }

    public record EmailRetryRequest(
            @NotBlank @Size(min = 10, max = 1000) String reason) { }
}

package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** Direct rolling-admissions command bodies. @author Tinashe K */
public final class RollingAdmissionsRequests {
    private RollingAdmissionsRequests() { }

    public record EligibilityResolutionRequest(
            @NotBlank String outcome,
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record AcademicRecommendationRequest(
            @NotBlank String recommendation,
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record AdmissionDecisionRequest(
            @NotBlank String decision,
            @NotBlank @Size(min = 10, max = 1000) String reason) { }

    public record UpdateOfferRequest(
            @NotBlank String offerType,
            @Size(max = 4000) String conditionsText,
            @NotNull @Future Instant acceptanceDeadline,
            LocalDate registrationDate,
            LocalDate orientationDate,
            @NotNull LocalDate commencementDate) { }

    public record EmailRetryRequest(
            @NotBlank @Size(min = 10, max = 1000) String reason) { }
}

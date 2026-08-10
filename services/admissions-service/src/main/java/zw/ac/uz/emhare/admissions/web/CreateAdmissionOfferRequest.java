package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record CreateAdmissionOfferRequest(
        @NotNull UUID offerBatchId,
        @NotNull UUID programmeChoiceId,
        @NotBlank String offerType,
        @Size(max = 4000) String conditionsText,
        @NotNull @Future Instant acceptanceDeadline,
        LocalDate registrationDate,
        LocalDate orientationDate,
        @NotNull LocalDate commencementDate,
        List<@Valid ConditionRequest> conditions) {

    public record ConditionRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 1000) String description,
            boolean required) {
    }
}

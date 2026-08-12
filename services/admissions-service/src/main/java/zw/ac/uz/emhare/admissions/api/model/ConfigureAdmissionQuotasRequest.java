package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Audited replacement of one intake's planning quotas. @author Tinashe K */
public record ConfigureAdmissionQuotasRequest(
        @NotNull @Size(max = 500) List<@Valid QuotaInput> quotas,
        @NotBlank @Size(min = 10, max = 1000) String changeReason) {

    public record QuotaInput(
            @NotNull UUID programmeId,
            @NotBlank @Size(max = 50) String programmeCode,
            @NotBlank @Size(max = 200) String programmeName,
            @NotBlank @Size(max = 50) String quotaTypeCode,
            @Min(1) int capacity,
            @Min(0) int reservedCapacity,
            @Min(0) long expectedVersion) { }
}

package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record CreateAdmissionCycleRequest(
        @NotNull UUID academicYearId,
        @NotNull UUID intakeId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull Instant opensAt,
        @NotNull Instant closesAt,
        @Min(1) @Max(20) int maximumProgrammeChoices,
        UUID applicationTypeId) {
}

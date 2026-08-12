package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateApplicationRequest(
        @jakarta.validation.constraints.NotBlank String applicantCategoryCode,
        @NotNull UUID intakeId,
        @NotNull UUID applicationTypeId,
        @NotNull @Size(max = 20) List<@NotNull UUID> programmeIds) {
}

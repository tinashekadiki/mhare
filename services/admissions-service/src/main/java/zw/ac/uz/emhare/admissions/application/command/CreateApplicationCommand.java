package zw.ac.uz.emhare.admissions.application.command;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.util.List;

public record CreateApplicationCommand(
        @NotNull UUID applicantUserId,
        @NotNull UUID applicantKeycloakUserId,
        @NotBlank String applicantCategoryCode,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String nationalIdNumber,
        @NotBlank String primaryEmail,
        @NotNull UUID intakeId,
        @NotNull UUID applicationTypeId,
        @NotNull List<UUID> programmeIds) {
}

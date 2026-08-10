package zw.ac.uz.emhare.studentrecords.registration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.util.Set;
import java.util.UUID;

/** @author Tinashe K */
public final class RegistrationCommands {
    private RegistrationCommands() {
    }

    public record CreateRegistration(
            @NotNull UUID studentId,
            @NotNull UUID programmeEnrolmentId,
            @NotNull UUID academicPeriodId,
            @Min(1) int programmePeriodNumber,
            @NotNull RegistrationType registrationType,
            @NotNull Set<UUID> selectedElectiveCurriculumModuleIds) {
    }

    public record RegistrationDecision(
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record CreateOwnRegistration(
            @NotNull UUID programmeEnrolmentId,
            @NotNull UUID academicPeriodId,
            @Min(1) int programmePeriodNumber,
            @NotNull Set<UUID> selectedElectiveCurriculumModuleIds) {
    }

    public record SubmitOwnRegistration(
            @Min(0) long expectedVersion,
            @AssertTrue(message = "The registration declaration must be accepted.") boolean declarationAccepted) {
    }
}

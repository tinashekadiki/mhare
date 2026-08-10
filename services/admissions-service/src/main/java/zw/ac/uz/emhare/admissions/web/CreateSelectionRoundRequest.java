package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @author Tinashe K */
public record CreateSelectionRoundRequest(
        @NotNull UUID intakeId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 180) String name) {
}

package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @author Tinashe K */
public record CreateOfferBatchRequest(
        @NotNull UUID intakeId,
        @NotNull UUID selectionRoundId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 180) String name,
        @NotBlank String scopeType,
        UUID scopeId) {
}

package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @author Tinashe K */
public record RecordSelectionDecisionRequest(
        @NotNull UUID programmeChoiceId,
        @NotBlank String decision,
        @Positive Integer rankPosition,
        @Size(max = 50) String quotaTypeCode,
        @NotBlank @Size(max = 1000) String reason) {
}

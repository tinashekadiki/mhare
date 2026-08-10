package zw.ac.uz.emhare.assessmentresults.result;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class ResultCommands {
    private ResultCommands() {
    }

    public record Band(
            @NotNull @Min(0) @Max(100) BigDecimal minimumMark,
            @NotNull @Min(0) @Max(100) BigDecimal maximumMark,
            @NotBlank String grade,
            @NotBlank String remark,
            boolean passing,
            @Min(1) int sortOrder) {
    }

    public record CreateGradingScheme(
            @NotBlank String code,
            @NotBlank String name,
            @NotEmpty List<@Valid Band> bands) {
    }

    public record CreateResultBatch(
            @NotNull UUID calculationRunId,
            @NotNull UUID gradingSchemeId) {
    }

    public record RequestPublishedResultAmendment(
            @NotNull UUID originalPublishedResultId,
            @NotNull UUID replacementModuleResultId,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record Decision(
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }
}

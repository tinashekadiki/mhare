package zw.ac.uz.emhare.assessmentresults.progression;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class ProgressionCommands {
    private ProgressionCommands() {
    }

    public record Outcome(
            @Min(1) int priority,
            @NotNull ProgressionRuleOutcome.DecisionCode decisionCode,
            @NotBlank @Size(max = 150) String decisionLabel,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minimumWeightedAverage,
            @DecimalMin("0.00") BigDecimal minimumPassedCredits,
            @DecimalMin("0.00") BigDecimal maximumFailedCredits,
            @Min(0) Integer maximumFailedModules,
            boolean requireAllCompulsoryPassed,
            @Min(1) Integer nextProgrammePeriodNumber,
            boolean fallbackOutcome) {
    }

    public record CreateRuleSet(
            @NotBlank @Size(max = 40) String ruleCode,
            @NotBlank @Size(max = 180) String ruleName,
            @NotNull UUID programmeId,
            @NotNull UUID programmeVersionId,
            @Min(1) int programmePeriodNumber,
            @NotEmpty List<@Valid Outcome> outcomes) {
    }

    public record CalculateDecision(
            @NotNull UUID registrationRosterImportId,
            @NotNull UUID progressionRuleSetId) {
    }

    public record WorkflowDecision(
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }
}

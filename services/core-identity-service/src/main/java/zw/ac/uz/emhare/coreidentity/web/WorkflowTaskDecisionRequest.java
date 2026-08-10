package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record WorkflowTaskDecisionRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 50) String decisionCode,
        @NotBlank @Size(max = 2000) String comment) {
}

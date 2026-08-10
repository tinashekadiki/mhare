package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.PositiveOrZero;

/** @author Tinashe K */
public record WorkflowTaskVersionRequest(@PositiveOrZero long expectedVersion) {
}

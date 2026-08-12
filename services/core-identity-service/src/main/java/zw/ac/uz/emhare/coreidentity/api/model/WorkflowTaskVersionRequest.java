package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.PositiveOrZero;

/** @author Tinashe K */
public record WorkflowTaskVersionRequest(@PositiveOrZero long expectedVersion) {
}

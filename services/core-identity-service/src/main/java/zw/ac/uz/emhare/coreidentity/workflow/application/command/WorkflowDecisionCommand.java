package zw.ac.uz.emhare.coreidentity.workflow.application.command;

import zw.ac.uz.emhare.coreidentity.workflow.*;

/** @author Tinashe K */
public record WorkflowDecisionCommand(
        long expectedVersion,
        String decisionCode,
        String comment) {
}

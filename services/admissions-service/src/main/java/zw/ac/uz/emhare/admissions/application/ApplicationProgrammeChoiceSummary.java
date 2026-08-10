package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;

/** @author Tinashe K */
public record ApplicationProgrammeChoiceSummary(
        UUID id, UUID programmeId, UUID programmeVersionId,
        String programmeCode, String programmeName, String awardName,
        String owningAcademicUnitName, String programmeVersionCode,
        int choiceRank, String choiceStatus, String evaluationSummary, String decisionReason) {

    static ApplicationProgrammeChoiceSummary from(ApplicationProgrammeChoice choice) {
        return new ApplicationProgrammeChoiceSummary(
                choice.getId(), choice.getProgrammeId(), choice.getProgrammeVersionId(),
                choice.getProgrammeCode(), choice.getProgrammeName(), choice.getAwardName(),
                choice.getOwningAcademicUnitName(), choice.getProgrammeVersionCode(),
                choice.getChoiceRank(), choice.getChoiceStatus().name(),
                choice.getEvaluationSummary(), choice.getDecisionReason());
    }
}

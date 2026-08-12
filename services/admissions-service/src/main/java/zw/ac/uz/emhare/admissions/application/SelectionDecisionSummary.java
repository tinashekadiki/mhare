package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.SelectionDecision;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record SelectionDecisionSummary(
        UUID id,
        UUID selectionRoundId,
        UUID programmeChoiceId,
        String applicationNumber,
        String programmeCode,
        String programmeName,
        String decision,
        Integer rankPosition,
        String quotaTypeCode,
        String reason,
        UUID decidedByUserId,
        Instant decidedAt) {

    static SelectionDecisionSummary from(SelectionDecision decision) {
        ApplicationProgrammeChoice choice = decision.getProgrammeChoice();
        return new SelectionDecisionSummary(
                decision.getId(), decision.getSelectionRound().getId(), choice.getId(),
                choice.getApplication().getApplicationNumber(), choice.getProgrammeCode(), choice.getProgrammeName(),
                decision.getDecision().name(), decision.getRankPosition(), decision.getQuotaTypeCode(),
                decision.getReason(), decision.getDecidedByUserId(), decision.getDecidedAt());
    }
}

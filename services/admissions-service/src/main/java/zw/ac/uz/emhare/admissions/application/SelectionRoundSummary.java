package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.SelectionRound;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record SelectionRoundSummary(
        UUID id,
        UUID intakeId,
        String intakeCode,
        String code,
        String name,
        String status,
        Instant openedAt,
        Instant approvedAt,
        Instant closedAt) {

    static SelectionRoundSummary from(SelectionRound round) {
        return new SelectionRoundSummary(
                round.getId(), round.getAdmissionCycle().getIntakeId(), round.getAdmissionCycle().getCode(),
                round.getCode(), round.getName(), round.getStatus().name(),
                round.getOpenedAt(), round.getApprovedAt(), round.getClosedAt());
    }
}

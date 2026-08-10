package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record OfferBatchSummary(
        UUID id,
        UUID intakeId,
        UUID selectionRoundId,
        String code,
        String name,
        String scopeType,
        UUID scopeId,
        String status,
        Instant approvedAt,
        Instant dispatchedAt,
        Instant closedAt) {

    static OfferBatchSummary from(OfferBatch batch) {
        return new OfferBatchSummary(
                batch.getId(), batch.getAdmissionCycle().getIntakeId(), batch.getSelectionRound().getId(),
                batch.getCode(), batch.getName(), batch.getScopeType().name(), batch.getScopeId(),
                batch.getStatus().name(), batch.getApprovedAt(), batch.getDispatchedAt(), batch.getClosedAt());
    }
}

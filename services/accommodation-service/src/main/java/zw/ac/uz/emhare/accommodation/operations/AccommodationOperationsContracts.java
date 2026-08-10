package zw.ac.uz.emhare.accommodation.operations;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class AccommodationOperationsContracts {
    private AccommodationOperationsContracts() {}

    public record CreateRate(@NotNull UUID applicationPeriodId, @NotNull UUID roomTypeId,
            @Min(1) int rateVersion, @NotNull UUID financeFeeCatalogueId,
            @NotBlank @Size(min = 3, max = 3) String transactionCurrencyCode,
            @NotNull @DecimalMin("0.0001") BigDecimal indicativeTransactionAmount,
            UUID exchangeRateId, @DecimalMin("0.0001") BigDecimal indicativeBaseAmount,
            @NotNull Instant effectiveFrom, Instant effectiveUntil) {}
    public record RateTransition(@NotNull AccommodationRate.Status targetStatus,
            @NotBlank @Size(max = 1000) String reason, @Min(0) long expectedVersion) {}

    public record SubmitApplication(@NotNull UUID applicationPeriodId, @NotNull UUID studentId,
            @NotBlank String studentNumber, @NotBlank String studentName, @Email @NotBlank String primaryEmail,
            @NotBlank String genderCode, String disabilityCode, @Size(min = 3, max = 3) @NotBlank String countryCode,
            String locationCode, @NotNull UUID programmeId, @NotBlank String programmeCode,
            @NotBlank String programmeName, @Min(1) int programmeLevel, String sponsorCode,
            @NotNull AccommodationApplication.PaymentState paymentState, UUID preferredRoomTypeId,
            @Size(max = 1000) String specialRequirements) {}
    public record EvaluateApplication(@NotNull AccommodationApplication.Status outcome, int priorityScore,
            UUID selectedGroupId, @NotBlank @Size(max = 1000) String reason,
            @Min(0) long expectedVersion) {}
    public record WithdrawApplication(@NotBlank @Size(max = 1000) String reason,
            @Min(0) long expectedVersion) {}

    public record ProposeAllocation(@NotNull UUID applicationId, @NotNull UUID roomId,
            @NotNull UUID accommodationRateId, @NotNull LocalDate occupancyStartsOn,
            @NotNull LocalDate occupancyEndsOn, @NotBlank @Size(max = 1000) String reason) {}
    public record AllocationAction(@NotBlank @Size(max = 1000) String reason,
            @Min(0) long expectedVersion) {}

    public record RateSummary(UUID id, UUID applicationPeriodId, String applicationPeriodCode,
            UUID roomTypeId, String roomTypeCode, int rateVersion, UUID financeFeeCatalogueId,
            String transactionCurrencyCode, BigDecimal indicativeTransactionAmount,
            String baseCurrencyCode, UUID exchangeRateId, BigDecimal indicativeBaseAmount,
            AccommodationRate.RatingStatus ratingStatus, Instant effectiveFrom, Instant effectiveUntil,
            AccommodationRate.Status status, UUID preparedByUserId, UUID approvedByUserId,
            Instant approvedAt, String approvalReason, long version) {}
    public record ApplicationSummary(UUID id, String applicationNumber, UUID applicationPeriodId,
            String applicationPeriodCode, UUID studentId, String studentNumber, String studentName,
            String primaryEmail, String genderCode, String disabilityCode, String countryCode,
            String locationCode, UUID programmeId, String programmeCode, String programmeName,
            int programmeLevel, String sponsorCode, AccommodationApplication.PaymentState paymentState,
            UUID preferredRoomTypeId, String preferredRoomTypeCode, String specialRequirements,
            int priorityScore, AccommodationApplication.Status status, Instant submittedAt,
            UUID evaluatedByUserId, Instant evaluatedAt, String evaluationReason,
            UUID selectedGroupId, long version) {}
    public record WaitlistSummary(UUID id, UUID applicationId, String applicationNumber,
            UUID applicationPeriodId, String applicationPeriodCode, int waitlistPosition,
            int priorityScore, AccommodationWaitlistEntry.Status status, UUID enteredByUserId,
            Instant enteredAt, UUID removedByUserId, Instant removedAt, String removalReason,
            long version) {}
    public record AllocationSummary(UUID id, String allocationNumber, UUID applicationId,
            String applicationNumber, String studentNumber, String studentName, UUID roomId,
            String roomCode, String residenceHallCode, UUID accommodationRateId,
            String transactionCurrencyCode, BigDecimal indicativeTransactionAmount,
            BigDecimal indicativeBaseAmount, LocalDate occupancyStartsOn, LocalDate occupancyEndsOn,
            RoomAllocation.Status status, UUID allocatedByUserId, Instant allocatedAt,
            UUID approvedByUserId, Instant approvedAt, String approvalReason,
            UUID checkedInByUserId, Instant checkedInAt, String checkInNotes,
            UUID checkedOutByUserId, Instant checkedOutAt, String checkOutNotes,
            UUID endedByUserId, Instant endedAt, String endReason,
            RoomAllocation.BillingStatus billingStatus, long version) {}
    public record AllocationEventSummary(UUID id, UUID allocationId, String allocationNumber,
            RoomAllocation.Status previousStatus, RoomAllocation.Status newStatus,
            RoomAllocationEvent.EventType eventType, UUID fromRoomId, UUID toRoomId,
            String reason, UUID actorUserId, Instant occurredAt) {}
    public record OperationsRegister(List<RateSummary> rates, List<ApplicationSummary> applications,
            List<WaitlistSummary> waitlistEntries, List<AllocationSummary> allocations,
            List<AllocationEventSummary> allocationEvents) {}
}

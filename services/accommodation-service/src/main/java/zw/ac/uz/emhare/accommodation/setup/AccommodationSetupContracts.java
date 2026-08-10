package zw.ac.uz.emhare.accommodation.setup;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class AccommodationSetupContracts {
    private AccommodationSetupContracts() {}

    public record CreatePremise(@NotBlank String code, @NotBlank String name, @NotBlank String addressLine,
            String suburb, String landlordName, String contactDetails) {}
    public record UpdatePremise(@NotBlank String code, @NotBlank String name, @NotBlank String addressLine,
            String suburb, String landlordName, String contactDetails, boolean active, @Min(0) long expectedVersion) {}
    public record CreateRoomType(@NotBlank String code, @NotBlank String name, String description,
            @Min(1) int defaultCapacity) {}
    public record UpdateRoomType(@NotBlank String code, @NotBlank String name, String description,
            @Min(1) int defaultCapacity, boolean active, @Min(0) long expectedVersion) {}
    public record CreateResidenceHall(@NotNull UUID premiseId, @NotBlank String code, @NotBlank String name,
            @NotNull ResidenceHall.ResidentGenderPolicy residentGenderPolicy, String wardenName, String wardenContact) {}
    public record UpdateResidenceHall(@NotNull UUID premiseId, @NotBlank String code, @NotBlank String name,
            @NotNull ResidenceHall.ResidentGenderPolicy residentGenderPolicy, String wardenName,
            String wardenContact, boolean active, @Min(0) long expectedVersion) {}
    public record CreateRoom(@NotNull UUID residenceHallId, @NotNull UUID roomTypeId, @NotBlank String code,
            String floorLabel, @Min(1) int capacity, boolean accessibilityReady,
            @NotNull AccommodationRoom.ConditionStatus conditionStatus, String conditionNotes, UUID reservedForGroupId) {}
    public record UpdateRoom(@NotNull UUID residenceHallId, @NotNull UUID roomTypeId, @NotBlank String code,
            String floorLabel, @Min(1) int capacity, boolean accessibilityReady,
            @NotNull AccommodationRoom.ConditionStatus conditionStatus, String conditionNotes,
            UUID reservedForGroupId, boolean active, @Min(0) long expectedVersion) {}
    public record CreateApplicationPeriod(@NotNull UUID academicPeriodId, @NotBlank String academicPeriodCode,
            @NotBlank String code, @NotBlank String name, @NotNull Instant applicationsOpenAt,
            @NotNull Instant applicationsCloseAt, @NotNull LocalDate occupancyStartsOn,
            @NotNull LocalDate occupancyEndsOn, @NotNull Instant allocationCutoffAt) {}
    public record UpdateApplicationPeriod(@NotNull UUID academicPeriodId, @NotBlank String academicPeriodCode,
            @NotBlank String code, @NotBlank String name, @NotNull Instant applicationsOpenAt,
            @NotNull Instant applicationsCloseAt, @NotNull LocalDate occupancyStartsOn,
            @NotNull LocalDate occupancyEndsOn, @NotNull Instant allocationCutoffAt,
            @Min(0) long expectedVersion) {}
    public record PeriodTransition(@NotNull AccommodationApplicationPeriod.Status targetStatus,
            @NotBlank @Size(max = 1000) String reason, @Min(0) long expectedVersion) {}

    public record PremiseSummary(UUID id, String code, String name, String addressLine, String suburb,
            String landlordName, String contactDetails, boolean active, long version) {}
    public record RoomTypeSummary(UUID id, String code, String name, String description,
            int defaultCapacity, boolean active, long version) {}
    public record ResidenceHallSummary(UUID id, UUID premiseId, String premiseCode, String code, String name,
            ResidenceHall.ResidentGenderPolicy residentGenderPolicy, String wardenName,
            String wardenContact, boolean active, long version) {}
    public record RoomSummary(UUID id, UUID residenceHallId, String residenceHallCode, UUID roomTypeId,
            String roomTypeCode, String code, String floorLabel, int capacity, boolean accessibilityReady,
            AccommodationRoom.ConditionStatus conditionStatus, String conditionNotes,
            UUID reservedForGroupId, boolean active, long version) {}
    public record ApplicationPeriodSummary(UUID id, UUID academicPeriodId, String academicPeriodCode,
            String code, String name, Instant applicationsOpenAt, Instant applicationsCloseAt,
            LocalDate occupancyStartsOn, LocalDate occupancyEndsOn, Instant allocationCutoffAt,
            AccommodationApplicationPeriod.Status status, UUID preparedByUserId, UUID approvedByUserId,
            Instant approvedAt, String approvalReason, long version) {}
    public record SetupRegister(List<PremiseSummary> premises, List<RoomTypeSummary> roomTypes,
            List<ResidenceHallSummary> residenceHalls, List<RoomSummary> rooms,
            List<ApplicationPeriodSummary> applicationPeriods) {}
}

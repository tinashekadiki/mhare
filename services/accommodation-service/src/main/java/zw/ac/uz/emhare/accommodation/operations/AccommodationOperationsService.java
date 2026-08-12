package zw.ac.uz.emhare.accommodation.operations;

import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationApplication;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationRate;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationWaitlistEntry;
import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocation;
import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocationEvent;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.AccommodationApplicationRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.AccommodationRateRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.AccommodationWaitlistRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.OperationalApplicationPeriodRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.OperationalRoomRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.OperationalRoomTypeRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.RoomAllocationEventRepository;
import zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence.RoomAllocationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.accommodation.operations.api.model.AccommodationOperationsApiModels.*;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoomType;

/** @author Tinashe K */
@Service
public class AccommodationOperationsService {
    private final OperationalApplicationPeriodRepository applicationPeriodRepository;
    private final OperationalRoomTypeRepository roomTypeRepository;
    private final OperationalRoomRepository roomRepository;
    private final AccommodationRateRepository rateRepository;
    private final AccommodationApplicationRepository applicationRepository;
    private final AccommodationWaitlistRepository waitlistRepository;
    private final RoomAllocationRepository allocationRepository;
    private final RoomAllocationEventRepository allocationEventRepository;
    private final Clock clock;

    public AccommodationOperationsService(OperationalApplicationPeriodRepository applicationPeriodRepository,
            OperationalRoomTypeRepository roomTypeRepository, OperationalRoomRepository roomRepository,
            AccommodationRateRepository rateRepository, AccommodationApplicationRepository applicationRepository,
            AccommodationWaitlistRepository waitlistRepository, RoomAllocationRepository allocationRepository,
            RoomAllocationEventRepository allocationEventRepository, Clock clock) {
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.rateRepository = rateRepository;
        this.applicationRepository = applicationRepository;
        this.waitlistRepository = waitlistRepository;
        this.allocationRepository = allocationRepository;
        this.allocationEventRepository = allocationEventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperationsRegister register() {
        return new OperationsRegister(
                rateRepository.findAllByDeletedAtIsNullOrderByEffectiveFromDesc().stream().map(this::view).toList(),
                applicationRepository.findAllByDeletedAtIsNullOrderBySubmittedAtDesc().stream().map(this::view).toList(),
                waitlistRepository.findAllByDeletedAtIsNullOrderByApplicationPeriodIdAscWaitlistPositionAsc().stream().map(this::view).toList(),
                allocationRepository.findAllByDeletedAtIsNullOrderByAllocatedAtDesc().stream().map(this::view).toList(),
                allocationEventRepository.findAllByDeletedAtIsNullOrderByOccurredAtDesc().stream().map(this::view).toList());
    }

    @Transactional
    public RateSummary createRate(CreateRate command, UUID actorUserId) {
        AccommodationApplicationPeriod period = requirePeriod(command.applicationPeriodId());
        AccommodationRoomType roomType = requireRoomType(command.roomTypeId());
        if (period.getStatus() != AccommodationApplicationPeriod.Status.DRAFT
                && period.getStatus() != AccommodationApplicationPeriod.Status.APPLICATION_OPEN) {
            throw new IllegalStateException("Rates can only be prepared before accommodation applications close.");
        }
        if (rateRepository.existsByApplicationPeriodIdAndRoomTypeIdAndRateVersion(
                period.getId(), roomType.getId(), command.rateVersion())) {
            throw new IllegalStateException("This accommodation rate version already exists.");
        }
        AccommodationRate rate = new AccommodationRate(period, roomType, command.rateVersion(),
                command.financeFeeCatalogueId(), command.transactionCurrencyCode(),
                command.indicativeTransactionAmount(), command.exchangeRateId(), command.indicativeBaseAmount(),
                command.effectiveFrom(), command.effectiveUntil(), actorUserId);
        return view(rateRepository.saveAndFlush(rate));
    }

    @Transactional
    public RateSummary transitionRate(UUID id, RateTransition command, UUID actorUserId) {
        AccommodationRate rate = requireRate(id);
        rate.transition(command.targetStatus(), actorUserId, command.reason(), clock.instant(), command.expectedVersion());
        return view(rateRepository.saveAndFlush(rate));
    }

    @Transactional
    public ApplicationSummary submitApplication(SubmitApplication command) {
        AccommodationApplicationPeriod period = requirePeriod(command.applicationPeriodId());
        AccommodationRoomType preferredRoomType = command.preferredRoomTypeId() == null
                ? null : requireRoomType(command.preferredRoomTypeId());
        long sequence = applicationRepository.nextApplicationNumber();
        AccommodationApplication application = new AccommodationApplication("ACC-%08d".formatted(sequence),
                period, command.studentId(), command.studentNumber(), command.studentName(), command.primaryEmail(),
                command.genderCode(), command.disabilityCode(), command.countryCode(), command.locationCode(),
                command.programmeId(), command.programmeCode(), command.programmeName(), command.programmeLevel(),
                command.sponsorCode(), command.paymentState(), preferredRoomType, command.specialRequirements(),
                clock.instant());
        return view(applicationRepository.saveAndFlush(application));
    }

    @Transactional
    public ApplicationSummary evaluateApplication(UUID id, EvaluateApplication command, UUID actorUserId) {
        AccommodationApplication application = requireApplication(id);
        Instant occurredAt = clock.instant();
        application.evaluate(command.outcome(), command.priorityScore(), command.selectedGroupId(),
                command.reason(), actorUserId, occurredAt, command.expectedVersion());
        applicationRepository.saveAndFlush(application);
        if (command.outcome() == AccommodationApplication.Status.WAITLISTED) {
            UUID periodId = application.getApplicationPeriod().getId();
            waitlistRepository.acquirePeriodLock(periodId);
            int position = waitlistRepository.maximumActivePosition(periodId) + 1;
            waitlistRepository.saveAndFlush(new AccommodationWaitlistEntry(application, position, actorUserId, occurredAt));
        }
        return view(application);
    }

    @Transactional
    public ApplicationSummary withdrawApplication(UUID id, WithdrawApplication command, UUID actorUserId) {
        AccommodationApplication application = requireApplication(id);
        Instant occurredAt = clock.instant();
        application.withdraw(actorUserId, command.reason(), occurredAt, command.expectedVersion());
        waitlistRepository.findByApplicationIdAndStatus(id, AccommodationWaitlistEntry.Status.ACTIVE)
                .ifPresent(entry -> entry.remove(AccommodationWaitlistEntry.Status.WITHDRAWN,
                        actorUserId, command.reason(), occurredAt));
        return view(applicationRepository.saveAndFlush(application));
    }

    @Transactional
    public AllocationSummary proposeAllocation(ProposeAllocation command, UUID actorUserId) {
        AccommodationApplication application = requireApplication(command.applicationId());
        AccommodationRoom room = requireRoom(command.roomId());
        AccommodationRate rate = requireRate(command.accommodationRateId());
        Instant occurredAt = clock.instant();
        long sequence = allocationRepository.nextAllocationNumber();
        RoomAllocation allocation = allocationRepository.saveAndFlush(new RoomAllocation(
                "ALL-%08d".formatted(sequence), application, room, rate, command.occupancyStartsOn(),
                command.occupancyEndsOn(), actorUserId, occurredAt));
        allocationEventRepository.saveAndFlush(new RoomAllocationEvent(allocation, null,
                RoomAllocation.Status.PROPOSED, RoomAllocationEvent.EventType.PROPOSED,
                null, room, command.reason(), actorUserId, occurredAt));
        return view(allocation);
    }

    @Transactional
    public AllocationSummary approveAllocation(UUID id, AllocationAction command, UUID actorUserId) {
        RoomAllocation allocation = requireAllocation(id);
        Instant occurredAt = clock.instant();
        RoomAllocation.Status previous = allocation.approve(actorUserId, command.reason(), occurredAt,
                command.expectedVersion());
        allocation.getApplication().markAllocated();
        waitlistRepository.findByApplicationIdAndStatus(allocation.getApplication().getId(),
                        AccommodationWaitlistEntry.Status.ACTIVE)
                .ifPresent(entry -> entry.remove(AccommodationWaitlistEntry.Status.ALLOCATED,
                        actorUserId, command.reason(), occurredAt));
        allocationRepository.saveAndFlush(allocation);
        recordEvent(allocation, previous, RoomAllocationEvent.EventType.APPROVED,
                command.reason(), actorUserId, occurredAt);
        return view(allocation);
    }

    @Transactional
    public AllocationSummary checkIn(UUID id, AllocationAction command, UUID actorUserId) {
        RoomAllocation allocation = requireAllocation(id);
        Instant occurredAt = clock.instant();
        RoomAllocation.Status previous = allocation.checkIn(actorUserId, command.reason(), occurredAt,
                command.expectedVersion());
        allocationRepository.saveAndFlush(allocation);
        recordEvent(allocation, previous, RoomAllocationEvent.EventType.CHECKED_IN,
                command.reason(), actorUserId, occurredAt);
        return view(allocation);
    }

    @Transactional
    public AllocationSummary checkOut(UUID id, AllocationAction command, UUID actorUserId) {
        RoomAllocation allocation = requireAllocation(id);
        Instant occurredAt = clock.instant();
        RoomAllocation.Status previous = allocation.checkOut(actorUserId, command.reason(), occurredAt,
                command.expectedVersion());
        allocationRepository.saveAndFlush(allocation);
        recordEvent(allocation, previous, RoomAllocationEvent.EventType.CHECKED_OUT,
                command.reason(), actorUserId, occurredAt);
        return view(allocation);
    }

    @Transactional
    public AllocationSummary cancelAllocation(UUID id, AllocationAction command, UUID actorUserId) {
        RoomAllocation allocation = requireAllocation(id);
        Instant occurredAt = clock.instant();
        RoomAllocation.Status previous = allocation.end(RoomAllocation.Status.CANCELLED, actorUserId,
                command.reason(), occurredAt, command.expectedVersion());
        allocationRepository.saveAndFlush(allocation);
        recordEvent(allocation, previous, RoomAllocationEvent.EventType.CANCELLED,
                command.reason(), actorUserId, occurredAt);
        return view(allocation);
    }

    @Transactional
    public AllocationSummary withdrawAllocation(UUID id, AllocationAction command, UUID actorUserId) {
        RoomAllocation allocation = requireAllocation(id);
        Instant occurredAt = clock.instant();
        RoomAllocation.Status previous = allocation.end(RoomAllocation.Status.WITHDRAWN, actorUserId,
                command.reason(), occurredAt, command.expectedVersion());
        allocationRepository.saveAndFlush(allocation);
        recordEvent(allocation, previous, RoomAllocationEvent.EventType.WITHDRAWN,
                command.reason(), actorUserId, occurredAt);
        return view(allocation);
    }

    private void recordEvent(RoomAllocation allocation, RoomAllocation.Status previous,
            RoomAllocationEvent.EventType type, String reason, UUID actorUserId, Instant occurredAt) {
        allocationEventRepository.saveAndFlush(new RoomAllocationEvent(allocation, previous,
                allocation.getStatus(), type, allocation.getRoom(), allocation.getRoom(),
                reason, actorUserId, occurredAt));
    }

    private AccommodationApplicationPeriod requirePeriod(UUID id) {
        return applicationPeriodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accommodation application period was not found."));
    }
    private AccommodationRoomType requireRoomType(UUID id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accommodation room type was not found."));
    }
    private AccommodationRoom requireRoom(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accommodation room was not found."));
    }
    private AccommodationRate requireRate(UUID id) {
        return rateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accommodation rate was not found."));
    }
    private AccommodationApplication requireApplication(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accommodation application was not found."));
    }
    private RoomAllocation requireAllocation(UUID id) {
        return allocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room allocation was not found."));
    }

    private RateSummary view(AccommodationRate rate) {
        return new RateSummary(rate.getId(), rate.getApplicationPeriod().getId(),
                rate.getApplicationPeriod().getCode(), rate.getRoomType().getId(), rate.getRoomType().getCode(),
                rate.getRateVersion(), rate.getFinanceFeeCatalogueId(), rate.getTransactionCurrencyCode(),
                rate.getIndicativeTransactionAmount(), rate.getBaseCurrencyCode(), rate.getExchangeRateId(),
                rate.getIndicativeBaseAmount(), rate.getRatingStatus(), rate.getEffectiveFrom(),
                rate.getEffectiveUntil(), rate.getStatus(), rate.getPreparedByUserId(), rate.getApprovedByUserId(),
                rate.getApprovedAt(), rate.getApprovalReason(), rate.getVersion());
    }

    private ApplicationSummary view(AccommodationApplication application) {
        AccommodationRoomType preference = application.getPreferredRoomType();
        return new ApplicationSummary(application.getId(), application.getApplicationNumber(),
                application.getApplicationPeriod().getId(), application.getApplicationPeriod().getCode(),
                application.getStudentId(), application.getStudentNumber(), application.getStudentName(),
                application.getPrimaryEmail(), application.getGenderCode(), application.getDisabilityCode(),
                application.getCountryCode(), application.getLocationCode(), application.getProgrammeId(),
                application.getProgrammeCode(), application.getProgrammeName(), application.getProgrammeLevel(),
                application.getSponsorCode(), application.getPaymentState(), preference == null ? null : preference.getId(),
                preference == null ? null : preference.getCode(), application.getSpecialRequirements(),
                application.getPriorityScore(), application.getStatus(), application.getSubmittedAt(),
                application.getEvaluatedByUserId(), application.getEvaluatedAt(), application.getEvaluationReason(),
                application.getSelectedGroupId(), application.getVersion());
    }

    private WaitlistSummary view(AccommodationWaitlistEntry entry) {
        return new WaitlistSummary(entry.getId(), entry.getApplication().getId(),
                entry.getApplication().getApplicationNumber(), entry.getApplicationPeriod().getId(),
                entry.getApplicationPeriod().getCode(), entry.getWaitlistPosition(), entry.getPriorityScore(),
                entry.getStatus(), entry.getEnteredByUserId(), entry.getEnteredAt(), entry.getRemovedByUserId(),
                entry.getRemovedAt(), entry.getRemovalReason(), entry.getVersion());
    }

    private AllocationSummary view(RoomAllocation allocation) {
        return new AllocationSummary(allocation.getId(), allocation.getAllocationNumber(),
                allocation.getApplication().getId(), allocation.getApplication().getApplicationNumber(),
                allocation.getApplication().getStudentNumber(), allocation.getApplication().getStudentName(),
                allocation.getRoom().getId(), allocation.getRoom().getCode(),
                allocation.getRoom().getResidenceHall().getCode(), allocation.getAccommodationRate().getId(),
                allocation.getAccommodationRate().getTransactionCurrencyCode(),
                allocation.getAccommodationRate().getIndicativeTransactionAmount(),
                allocation.getAccommodationRate().getIndicativeBaseAmount(), allocation.getOccupancyStartsOn(),
                allocation.getOccupancyEndsOn(), allocation.getStatus(), allocation.getAllocatedByUserId(),
                allocation.getAllocatedAt(), allocation.getApprovedByUserId(), allocation.getApprovedAt(),
                allocation.getApprovalReason(), allocation.getCheckedInByUserId(), allocation.getCheckedInAt(),
                allocation.getCheckInNotes(), allocation.getCheckedOutByUserId(), allocation.getCheckedOutAt(),
                allocation.getCheckOutNotes(), allocation.getEndedByUserId(), allocation.getEndedAt(),
                allocation.getEndReason(), allocation.getBillingStatus(), allocation.getVersion());
    }

    private AllocationEventSummary view(RoomAllocationEvent event) {
        return new AllocationEventSummary(event.getId(), event.getAllocation().getId(),
                event.getAllocation().getAllocationNumber(), event.getPreviousStatus(), event.getNewStatus(),
                event.getEventType(), event.getFromRoom() == null ? null : event.getFromRoom().getId(),
                event.getToRoom() == null ? null : event.getToRoom().getId(), event.getReason(),
                event.getActorUserId(), event.getOccurredAt());
    }
}

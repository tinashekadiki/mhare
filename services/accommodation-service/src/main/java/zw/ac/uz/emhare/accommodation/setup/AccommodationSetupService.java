package zw.ac.uz.emhare.accommodation.setup;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.accommodation.setup.AccommodationSetupContracts.*;

/** @author Tinashe K */
@Service
public class AccommodationSetupService {
    private final AccommodationPremiseRepository premiseRepository;
    private final AccommodationRoomTypeRepository roomTypeRepository;
    private final ResidenceHallRepository residenceHallRepository;
    private final AccommodationRoomRepository roomRepository;
    private final AccommodationApplicationPeriodRepository applicationPeriodRepository;
    private final Clock clock;

    public AccommodationSetupService(AccommodationPremiseRepository premiseRepository,
            AccommodationRoomTypeRepository roomTypeRepository, ResidenceHallRepository residenceHallRepository,
            AccommodationRoomRepository roomRepository,
            AccommodationApplicationPeriodRepository applicationPeriodRepository, Clock clock) {
        this.premiseRepository = premiseRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.residenceHallRepository = residenceHallRepository;
        this.roomRepository = roomRepository;
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SetupRegister register() {
        return new SetupRegister(
                premiseRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
                roomTypeRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
                residenceHallRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
                roomRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
                applicationPeriodRepository.findAllByDeletedAtIsNullOrderByApplicationsOpenAtDescCodeAsc().stream().map(this::view).toList());
    }

    @Transactional
    public PremiseSummary createPremise(CreatePremise command) {
        return view(premiseRepository.saveAndFlush(new AccommodationPremise(command.code(), command.name(),
                command.addressLine(), command.suburb(), command.landlordName(), command.contactDetails())));
    }

    @Transactional
    public PremiseSummary updatePremise(UUID id, UpdatePremise command) {
        AccommodationPremise premise = requirePremise(id);
        premise.update(command.code(), command.name(), command.addressLine(), command.suburb(),
                command.landlordName(), command.contactDetails(), command.active(), command.expectedVersion());
        return view(premiseRepository.saveAndFlush(premise));
    }

    @Transactional
    public RoomTypeSummary createRoomType(CreateRoomType command) {
        return view(roomTypeRepository.saveAndFlush(new AccommodationRoomType(command.code(), command.name(),
                command.description(), command.defaultCapacity())));
    }

    @Transactional
    public RoomTypeSummary updateRoomType(UUID id, UpdateRoomType command) {
        AccommodationRoomType roomType = requireRoomType(id);
        roomType.update(command.code(), command.name(), command.description(), command.defaultCapacity(),
                command.active(), command.expectedVersion());
        return view(roomTypeRepository.saveAndFlush(roomType));
    }

    @Transactional
    public ResidenceHallSummary createResidenceHall(CreateResidenceHall command) {
        AccommodationPremise premise = requirePremise(command.premiseId());
        return view(residenceHallRepository.saveAndFlush(new ResidenceHall(premise, command.code(),
                command.name(), command.residentGenderPolicy(), command.wardenName(), command.wardenContact())));
    }

    @Transactional
    public ResidenceHallSummary updateResidenceHall(UUID id, UpdateResidenceHall command) {
        ResidenceHall residenceHall = requireResidenceHall(id);
        residenceHall.update(requirePremise(command.premiseId()), command.code(), command.name(),
                command.residentGenderPolicy(), command.wardenName(), command.wardenContact(),
                command.active(), command.expectedVersion());
        return view(residenceHallRepository.saveAndFlush(residenceHall));
    }

    @Transactional
    public RoomSummary createRoom(CreateRoom command) {
        return view(roomRepository.saveAndFlush(new AccommodationRoom(requireResidenceHall(command.residenceHallId()),
                requireRoomType(command.roomTypeId()), command.code(), command.floorLabel(), command.capacity(),
                command.accessibilityReady(), command.conditionStatus(), command.conditionNotes(),
                command.reservedForGroupId())));
    }

    @Transactional
    public RoomSummary updateRoom(UUID id, UpdateRoom command) {
        AccommodationRoom room = requireRoom(id);
        room.update(requireResidenceHall(command.residenceHallId()), requireRoomType(command.roomTypeId()),
                command.code(), command.floorLabel(), command.capacity(), command.accessibilityReady(),
                command.conditionStatus(), command.conditionNotes(), command.reservedForGroupId(),
                command.active(), command.expectedVersion());
        return view(roomRepository.saveAndFlush(room));
    }

    @Transactional
    public ApplicationPeriodSummary createApplicationPeriod(CreateApplicationPeriod command, UUID actorUserId) {
        return view(applicationPeriodRepository.saveAndFlush(new AccommodationApplicationPeriod(
                command.academicPeriodId(), command.academicPeriodCode(), command.code(), command.name(),
                command.applicationsOpenAt(), command.applicationsCloseAt(), command.occupancyStartsOn(),
                command.occupancyEndsOn(), command.allocationCutoffAt(), actorUserId)));
    }

    @Transactional
    public ApplicationPeriodSummary updateApplicationPeriod(UUID id, UpdateApplicationPeriod command) {
        AccommodationApplicationPeriod period = requireApplicationPeriod(id);
        period.updateDraft(command.academicPeriodId(), command.academicPeriodCode(), command.code(), command.name(),
                command.applicationsOpenAt(), command.applicationsCloseAt(), command.occupancyStartsOn(),
                command.occupancyEndsOn(), command.allocationCutoffAt(), command.expectedVersion());
        return view(applicationPeriodRepository.saveAndFlush(period));
    }

    @Transactional
    public ApplicationPeriodSummary transitionApplicationPeriod(UUID id, PeriodTransition command, UUID actorUserId) {
        AccommodationApplicationPeriod period = requireApplicationPeriod(id);
        period.transition(command.targetStatus(), actorUserId, command.reason(), clock.instant(), command.expectedVersion());
        return view(applicationPeriodRepository.saveAndFlush(period));
    }

    private AccommodationPremise requirePremise(UUID id) {
        return premiseRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Accommodation premise was not found."));
    }
    private AccommodationRoomType requireRoomType(UUID id) {
        return roomTypeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Accommodation room type was not found."));
    }
    private ResidenceHall requireResidenceHall(UUID id) {
        return residenceHallRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Residence hall was not found."));
    }
    private AccommodationRoom requireRoom(UUID id) {
        return roomRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Accommodation room was not found."));
    }
    private AccommodationApplicationPeriod requireApplicationPeriod(UUID id) {
        return applicationPeriodRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Accommodation application period was not found."));
    }

    private PremiseSummary view(AccommodationPremise item) {
        return new PremiseSummary(item.getId(), item.getCode(), item.getName(), item.getAddressLine(),
                item.getSuburb(), item.getLandlordName(), item.getContactDetails(), item.isActive(), item.getVersion());
    }
    private RoomTypeSummary view(AccommodationRoomType item) {
        return new RoomTypeSummary(item.getId(), item.getCode(), item.getName(), item.getDescription(),
                item.getDefaultCapacity(), item.isActive(), item.getVersion());
    }
    private ResidenceHallSummary view(ResidenceHall item) {
        return new ResidenceHallSummary(item.getId(), item.getPremise().getId(), item.getPremise().getCode(),
                item.getCode(), item.getName(), item.getResidentGenderPolicy(), item.getWardenName(),
                item.getWardenContact(), item.isActive(), item.getVersion());
    }
    private RoomSummary view(AccommodationRoom item) {
        return new RoomSummary(item.getId(), item.getResidenceHall().getId(), item.getResidenceHall().getCode(),
                item.getRoomType().getId(), item.getRoomType().getCode(), item.getCode(), item.getFloorLabel(),
                item.getCapacity(), item.isAccessibilityReady(), item.getConditionStatus(), item.getConditionNotes(),
                item.getReservedForGroupId(), item.isActive(), item.getVersion());
    }
    private ApplicationPeriodSummary view(AccommodationApplicationPeriod item) {
        return new ApplicationPeriodSummary(item.getId(), item.getAcademicPeriodId(), item.getAcademicPeriodCode(),
                item.getCode(), item.getName(), item.getApplicationsOpenAt(), item.getApplicationsCloseAt(),
                item.getOccupancyStartsOn(), item.getOccupancyEndsOn(), item.getAllocationCutoffAt(), item.getStatus(),
                item.getPreparedByUserId(), item.getApprovedByUserId(), item.getApprovedAt(), item.getApprovalReason(),
                item.getVersion());
    }
}

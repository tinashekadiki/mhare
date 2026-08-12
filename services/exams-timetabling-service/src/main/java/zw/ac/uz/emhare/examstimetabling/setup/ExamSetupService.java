package zw.ac.uz.emhare.examstimetabling.setup;

import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamSession;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamSessionSlot;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamVenue;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamVenueAvailabilityWindow;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamVenueType;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ModuleExamRequirement;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.ExamSessionRepository;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.ExamSessionSlotRepository;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.ExamVenueAvailabilityRepository;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.ExamVenueRepository;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.ExamVenueTypeRepository;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.ModuleExamRequirementRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.examstimetabling.setup.api.model.ExamSetupApiModels.*;

/** @author Tinashe K */
@Service
public class ExamSetupService {
    private final ExamVenueTypeRepository venueTypeRepository; private final ExamVenueRepository venueRepository;
    private final ExamVenueAvailabilityRepository availabilityRepository; private final ExamSessionRepository sessionRepository;
    private final ExamSessionSlotRepository slotRepository; private final ModuleExamRequirementRepository requirementRepository;
    private final Clock clock;
    public ExamSetupService(ExamVenueTypeRepository venueTypeRepository,ExamVenueRepository venueRepository,
            ExamVenueAvailabilityRepository availabilityRepository,ExamSessionRepository sessionRepository,
            ExamSessionSlotRepository slotRepository,ModuleExamRequirementRepository requirementRepository,Clock clock){
        this.venueTypeRepository=venueTypeRepository;this.venueRepository=venueRepository;this.availabilityRepository=availabilityRepository;
        this.sessionRepository=sessionRepository;this.slotRepository=slotRepository;this.requirementRepository=requirementRepository;this.clock=clock;
    }
    @Transactional public VenueTypeSummary createVenueType(CreateVenueType command){return view(venueTypeRepository.saveAndFlush(new ExamVenueType(command.code(),command.name(),command.description())));}
    @Transactional public VenueSummary createVenue(CreateVenue command){ExamVenueType type=venueTypeRepository.findById(command.venueTypeId()).orElseThrow(()->new IllegalArgumentException("Exam venue type was not found."));return view(venueRepository.saveAndFlush(new ExamVenue(type,command.code(),command.name(),command.campusName(),command.buildingName(),command.roomName(),command.examinationCapacity(),command.accessibilityNotes())));}
    @Transactional public VenueSummary addAvailability(UUID venueId,AddAvailability command){ExamVenue venue=venueRepository.findById(venueId).orElseThrow(()->new IllegalArgumentException("Exam venue was not found."));availabilityRepository.saveAndFlush(new ExamVenueAvailabilityWindow(venue,command.availableFrom(),command.availableUntil(),command.notes()));return view(venue);}
    @Transactional public SessionSummary createSession(CreateSession command){return view(sessionRepository.saveAndFlush(new ExamSession(command.academicPeriodId(),command.academicPeriodCode(),command.code(),command.name(),command.assessmentType(),command.startsOn(),command.endsOn())));}
    @Transactional public SessionSummary addSlot(UUID sessionId,CreateSlot command){ExamSession session=requireSession(sessionId);slotRepository.saveAndFlush(new ExamSessionSlot(session,command.code(),command.startsAt(),command.endsAt()));return view(session);}
    @Transactional public SessionSummary approveSession(UUID sessionId,WorkflowDecision command,UUID actor){ExamSession session=requireSession(sessionId);session.approve(actor,command.reason(),clock.instant(),command.expectedVersion());return view(sessionRepository.saveAndFlush(session));}
    @Transactional public RequirementSummary createRequirement(CreateRequirement command){
        int version=requirementRepository.findAllByDeletedAtIsNullOrderByModuleCodeAscRequirementVersionDesc().stream()
                .filter(item->item.getAcademicPeriodId().equals(command.academicPeriodId())&&item.getModuleId().equals(command.moduleId()))
                .mapToInt(ModuleExamRequirement::getRequirementVersion).max().orElse(0)+1;
        ExamVenueType type=command.requiredVenueTypeId()==null?null:venueTypeRepository.findById(command.requiredVenueTypeId()).orElseThrow(()->new IllegalArgumentException("Required exam venue type was not found."));
        return view(requirementRepository.saveAndFlush(new ModuleExamRequirement(command.academicPeriodId(),command.moduleId(),command.moduleCode(),command.moduleName(),version,command.durationMinutes(),command.readingTimeMinutes(),type,command.specialRequirements())));
    }
    @Transactional public RequirementSummary approveRequirement(UUID requirementId,WorkflowDecision command,UUID actor){
        ModuleExamRequirement requirement=requirementRepository.findById(requirementId).orElseThrow(()->new IllegalArgumentException("Module exam requirement was not found."));
        requirementRepository.findByAcademicPeriodIdAndModuleIdAndStatusAndDeletedAtIsNull(requirement.getAcademicPeriodId(),requirement.getModuleId(),ModuleExamRequirement.Status.APPROVED)
                .filter(current->!current.getId().equals(requirementId)).ifPresent(ModuleExamRequirement::supersede);
        requirement.approve(actor,command.reason(),clock.instant(),command.expectedVersion());return view(requirementRepository.saveAndFlush(requirement));
    }
    @Transactional(readOnly=true) public SetupRegister register(){return new SetupRegister(
            venueTypeRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
            venueRepository.findAllByActiveTrueAndDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
            sessionRepository.findAllByDeletedAtIsNullOrderByStartsOnDescCodeAsc().stream().map(this::view).toList(),
            requirementRepository.findAllByDeletedAtIsNullOrderByModuleCodeAscRequirementVersionDesc().stream().map(this::view).toList());}
    private ExamSession requireSession(UUID id){return sessionRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Exam session was not found."));}
    private VenueTypeSummary view(ExamVenueType item){return new VenueTypeSummary(item.getId(),item.getCode(),item.getName(),item.getDescription(),item.isActive(),item.getVersion());}
    private VenueSummary view(ExamVenue item){List<AvailabilitySummary> availability=availabilityRepository.findAllByVenueIdAndDeletedAtIsNullOrderByAvailableFromAsc(item.getId()).stream().map(a->new AvailabilitySummary(a.getId(),a.getAvailableFrom(),a.getAvailableUntil(),a.getNotes())).toList();return new VenueSummary(item.getId(),item.getVenueType().getId(),item.getVenueType().getCode(),item.getCode(),item.getName(),item.getCampusName(),item.getBuildingName(),item.getRoomName(),item.getExaminationCapacity(),item.getAccessibilityNotes(),item.isActive(),item.getVersion(),availability);}
    private SessionSummary view(ExamSession item){List<SlotSummary> slots=slotRepository.findAllByExamSessionIdAndDeletedAtIsNullOrderByStartsAtAsc(item.getId()).stream().map(s->new SlotSummary(s.getId(),s.getCode(),s.getStartsAt(),s.getEndsAt())).toList();return new SessionSummary(item.getId(),item.getAcademicPeriodId(),item.getAcademicPeriodCode(),item.getCode(),item.getName(),item.getAssessmentType(),item.getStartsOn(),item.getEndsOn(),item.getStatus(),item.getApprovedByUserId(),item.getApprovedAt(),item.getApprovalReason(),item.getVersion(),slots);}
    private RequirementSummary view(ModuleExamRequirement item){ExamVenueType type=item.getRequiredVenueType();return new RequirementSummary(item.getId(),item.getAcademicPeriodId(),item.getModuleId(),item.getModuleCode(),item.getModuleName(),item.getRequirementVersion(),item.getDurationMinutes(),item.getReadingTimeMinutes(),type==null?null:type.getId(),type==null?null:type.getCode(),item.getSpecialRequirements(),item.getStatus(),item.getVersion());}
}

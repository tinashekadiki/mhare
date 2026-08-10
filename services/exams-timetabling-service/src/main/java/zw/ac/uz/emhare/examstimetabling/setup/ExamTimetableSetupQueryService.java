package zw.ac.uz.emhare.examstimetabling.setup;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Internal governed setup access for timetable generation. @author Tinashe K */
@Service
public class ExamTimetableSetupQueryService {
    private final ExamSessionRepository sessionRepository; private final ExamSessionSlotRepository slotRepository;
    private final ModuleExamRequirementRepository requirementRepository; private final ExamVenueRepository venueRepository;
    private final ExamVenueAvailabilityRepository availabilityRepository;
    public ExamTimetableSetupQueryService(ExamSessionRepository sessionRepository,ExamSessionSlotRepository slotRepository,
            ModuleExamRequirementRepository requirementRepository,ExamVenueRepository venueRepository,
            ExamVenueAvailabilityRepository availabilityRepository){this.sessionRepository=sessionRepository;this.slotRepository=slotRepository;
        this.requirementRepository=requirementRepository;this.venueRepository=venueRepository;this.availabilityRepository=availabilityRepository;}
    public ExamSession requireApprovedSession(UUID id){ExamSession session=sessionRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Exam session was not found."));if(session.getStatus()!=ExamSession.Status.APPROVED)throw new IllegalStateException("An approved exam session is required for timetable generation.");return session;}
    public List<ExamSessionSlot> slots(UUID sessionId){return slotRepository.findAllByExamSessionIdAndDeletedAtIsNullOrderByStartsAtAsc(sessionId);}
    public Map<UUID,ModuleExamRequirement> approvedRequirements(UUID academicPeriodId){return requirementRepository.findAllByAcademicPeriodIdAndStatusAndDeletedAtIsNull(academicPeriodId,ModuleExamRequirement.Status.APPROVED).stream().collect(Collectors.toMap(ModuleExamRequirement::getModuleId,Function.identity()));}
    public List<ExamVenue> activeVenues(){return venueRepository.findAllByActiveTrueAndDeletedAtIsNullOrderByCodeAsc();}
    public List<ExamVenueAvailabilityWindow> availabilityFor(ExamVenue venue){return availabilityRepository.findAllByVenueIdAndDeletedAtIsNullOrderByAvailableFromAsc(venue.getId());}
}

package zw.ac.uz.emhare.examstimetabling.timetable;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.examstimetabling.roster.ExamCandidateModule;
import zw.ac.uz.emhare.examstimetabling.roster.ExamRosterQueryService;
import zw.ac.uz.emhare.examstimetabling.setup.*;
import zw.ac.uz.emhare.examstimetabling.timetable.ExamTimetableContracts.*;

/** Deterministic, evidence-preserving exam timetable workflow. @author Tinashe K */
@Service
public class GovernedExamTimetableService {
    private final ExamTimetableGenerationRunRepository runRepository; private final ExamMasterTimetableEntryRepository masterRepository;
    private final ExamTimetableVenueAllocationRepository allocationRepository; private final ExamStudentTimetableEntryRepository studentRepository;
    private final ExamTimetableRunEventRepository eventRepository; private final ExamTimetableSetupQueryService setupQueryService;
    private final ExamRosterQueryService rosterQueryService; private final JdbcTemplate jdbcTemplate; private final Clock clock;
    public GovernedExamTimetableService(ExamTimetableGenerationRunRepository runRepository,
            ExamMasterTimetableEntryRepository masterRepository,ExamTimetableVenueAllocationRepository allocationRepository,
            ExamStudentTimetableEntryRepository studentRepository,ExamTimetableRunEventRepository eventRepository,
            ExamTimetableSetupQueryService setupQueryService,ExamRosterQueryService rosterQueryService,JdbcTemplate jdbcTemplate,Clock clock){
        this.runRepository=runRepository;this.masterRepository=masterRepository;this.allocationRepository=allocationRepository;
        this.studentRepository=studentRepository;this.eventRepository=eventRepository;this.setupQueryService=setupQueryService;
        this.rosterQueryService=rosterQueryService;this.jdbcTemplate=jdbcTemplate;this.clock=clock;
    }

    @Transactional
    public RunSummary generate(GenerateTimetable command,UUID actor) {
        jdbcTemplate.queryForObject(
                "SELECT true FROM pg_advisory_xact_lock(hashtext(?))",
                Boolean.class,
                command.examSessionId().toString());
        ExamSession session=setupQueryService.requireApprovedSession(command.examSessionId());
        List<ExamSessionSlot> slots=setupQueryService.slots(session.getId());
        if(slots.isEmpty())throw new IllegalStateException("The approved exam session has no timetable slots.");
        List<ExamCandidateModule> allCandidates=rosterQueryService.eligibleCandidates(session.getAcademicPeriodId());
        if(allCandidates.isEmpty())throw new IllegalStateException("No confirmed eligible exam candidates exist for the academic period.");
        Map<UUID,List<ExamCandidateModule>> candidatesByModule=allCandidates.stream().collect(Collectors.groupingBy(ExamCandidateModule::getModuleId));
        Map<UUID,ModuleExamRequirement> requirements=setupQueryService.approvedRequirements(session.getAcademicPeriodId());
        List<ModuleSchedule> schedules=new ArrayList<>();
        Map<UUID,List<TimeWindow>> studentBookings=new HashMap<>(); Map<UUID,List<TimeWindow>> venueBookings=new HashMap<>();
        List<ExamVenue> venues=setupQueryService.activeVenues();
        List<Map.Entry<UUID,List<ExamCandidateModule>>> modules=candidatesByModule.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID,List<ExamCandidateModule>>>comparingInt(entry->entry.getValue().size()).reversed()
                        .thenComparing(entry->entry.getValue().getFirst().getModuleCode())).toList();
        for(var module:modules){
            ModuleExamRequirement requirement=requirements.get(module.getKey());
            if(requirement==null)throw new IllegalStateException("Approved exam requirement is missing for Module "+module.getValue().getFirst().getModuleCode()+".");
            schedules.add(placeModule(session,slots,venues,requirement,module.getValue(),studentBookings,venueBookings));
        }
        int uniqueCandidates=(int)allCandidates.stream().map(candidate->candidate.getRegistrationImport().getStudentId()).distinct().count();
        Instant generatedAt=clock.instant(); String runNumber="EXM-"+session.getCode()+"-"+generatedAt.toEpochMilli();
        Map<String,Object> policy=Map.of("algorithm","largest-roster-first-v1","candidateSource","confirmed-registration-v1",
                "venueStrategy","smallest-sufficient-combination","studentAllocation","student-number-ascending","timeZone","UTC");
        ExamTimetableGenerationRun run=runRepository.saveAndFlush(new ExamTimetableGenerationRun(session,runNumber,uniqueCandidates,
                modules.size(),schedules.size(),0,policy,actor,generatedAt));
        for(ModuleSchedule schedule:schedules)persistSchedule(run,schedule);
        eventRepository.saveAndFlush(new ExamTimetableRunEvent(run,null,"Generated from confirmed registration and approved exam setup evidence.",actor,generatedAt));
        return view(run);
    }

    private ModuleSchedule placeModule(ExamSession session,List<ExamSessionSlot> slots,List<ExamVenue> venues,
            ModuleExamRequirement requirement,List<ExamCandidateModule> candidates,Map<UUID,List<TimeWindow>> studentBookings,
            Map<UUID,List<TimeWindow>> venueBookings){
        int requiredMinutes=requirement.getDurationMinutes()+requirement.getReadingTimeMinutes();
        for(ExamSessionSlot slot:slots){
            Instant endsAt=slot.getStartsAt().plus(Duration.ofMinutes(requiredMinutes));
            if(endsAt.isAfter(slot.getEndsAt())||candidates.stream().anyMatch(candidate->overlaps(studentBookings.get(candidate.getRegistrationImport().getStudentId()),slot.getStartsAt(),endsAt)))continue;
            List<ExamVenue> selected=new ArrayList<>();int capacity=0;
            List<ExamVenue> eligible=venues.stream().filter(venue->requirement.getRequiredVenueType()==null||venue.getVenueType().getId().equals(requirement.getRequiredVenueType().getId()))
                    .filter(venue->isAvailable(venue,slot.getStartsAt(),endsAt)).filter(venue->!overlaps(venueBookings.get(venue.getId()),slot.getStartsAt(),endsAt))
                    .filter(venue->!isPersistentlyOccupied(venue.getId(),slot.getStartsAt(),endsAt)).sorted(Comparator.comparingInt(ExamVenue::getExaminationCapacity).thenComparing(ExamVenue::getCode)).toList();
            for(ExamVenue venue:eligible){selected.add(venue);capacity+=venue.getExaminationCapacity();if(capacity>=candidates.size())break;}
            if(capacity<candidates.size())continue;
            TimeWindow window=new TimeWindow(slot.getStartsAt(),endsAt);
            candidates.forEach(candidate->studentBookings.computeIfAbsent(candidate.getRegistrationImport().getStudentId(),ignored->new ArrayList<>()).add(window));
            selected.forEach(venue->venueBookings.computeIfAbsent(venue.getId(),ignored->new ArrayList<>()).add(window));
            return new ModuleSchedule(slot,requirement,candidates.stream().sorted(Comparator.comparing(item->item.getRegistrationImport().getStudentNumber())).toList(),selected,endsAt);
        }
        throw new IllegalStateException("No clash-free slot and venue capacity can satisfy Module "+requirement.getModuleCode()+". No partial timetable was saved.");
    }

    private void persistSchedule(ExamTimetableGenerationRun run,ModuleSchedule schedule){
        ExamMasterTimetableEntry master=masterRepository.saveAndFlush(new ExamMasterTimetableEntry(run,schedule.slot(),schedule.requirement(),schedule.candidates().size(),schedule.endsAt()));
        int remaining=schedule.candidates().size();int candidateIndex=0;
        for(ExamVenue venue:schedule.venues()){
            int allocated=Math.min(remaining,venue.getExaminationCapacity());
            ExamTimetableVenueAllocation allocation=allocationRepository.saveAndFlush(new ExamTimetableVenueAllocation(master,venue,allocated));
            List<ExamStudentTimetableEntry> studentEntries=new ArrayList<>();
            for(int seat=1;seat<=allocated;seat++)studentEntries.add(new ExamStudentTimetableEntry(run,master,allocation,schedule.candidates().get(candidateIndex++),seat));
            studentRepository.saveAllAndFlush(studentEntries);remaining-=allocated;if(remaining==0)break;
        }
    }

    @Transactional public RunSummary move(UUID runId,String action,WorkflowDecision command,UUID actor){
        ExamTimetableGenerationRun run=requireRun(runId);Instant now=clock.instant();ExamTimetableGenerationRun.Status previous=switch(action){
            case "review"->run.review(actor,command.reason(),now,command.expectedVersion()); case "approve"->run.approve(actor,command.reason(),now,command.expectedVersion());
            case "publish"->run.publish(actor,command.reason(),now,command.expectedVersion()); case "reject"->run.reject(actor,command.reason(),now,command.expectedVersion());
            default->throw new IllegalArgumentException("Unsupported exam timetable workflow action.");};
        runRepository.saveAndFlush(run);eventRepository.saveAndFlush(new ExamTimetableRunEvent(run,previous,command.reason(),actor,now));return view(run);
    }
    @Transactional(readOnly=true) public List<RunSummary> runs(){return runRepository.findAllByDeletedAtIsNullOrderByGeneratedAtDesc().stream().map(this::view).toList();}
    @Transactional(readOnly=true) public List<StudentEntrySummary> publishedStudentTimetable(UUID studentId){return studentRepository.findAllByStudentIdAndGenerationRunStatusAndDeletedAtIsNullOrderByScheduledStartsAtAsc(studentId,ExamTimetableGenerationRun.Status.PUBLISHED).stream().map(this::studentView).toList();}
    private ExamTimetableGenerationRun requireRun(UUID id){return runRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new IllegalArgumentException("Exam timetable generation run was not found."));}
    private boolean isAvailable(ExamVenue venue,Instant startsAt,Instant endsAt){return setupQueryService.availabilityFor(venue).stream().anyMatch(window->!window.getAvailableFrom().isAfter(startsAt)&&!window.getAvailableUntil().isBefore(endsAt));}
    private boolean isPersistentlyOccupied(UUID venueId,Instant startsAt,Instant endsAt){Integer count=jdbcTemplate.queryForObject("""
        SELECT count(*) FROM exam_timetable_venue_allocations allocation
        JOIN exam_master_timetable_entries entry ON entry.id=allocation.master_timetable_entry_id
        JOIN exam_timetable_generation_runs run ON run.id=entry.generation_run_id
        WHERE allocation.venue_id=? AND allocation.deleted_at IS NULL AND run.status='PUBLISHED'
          AND tstzrange(entry.scheduled_starts_at,entry.scheduled_ends_at,'[)') && tstzrange(?,?,'[)')
        """,Integer.class,venueId,Timestamp.from(startsAt),Timestamp.from(endsAt));return count!=null&&count>0;}
    private static boolean overlaps(List<TimeWindow> windows,Instant start,Instant end){return windows!=null&&windows.stream().anyMatch(window->window.start().isBefore(end)&&start.isBefore(window.end()));}
    private RunSummary view(ExamTimetableGenerationRun run){List<MasterEntrySummary> entries=masterRepository.findAllByGenerationRunIdAndDeletedAtIsNullOrderByScheduledStartsAtAscModuleCodeAsc(run.getId()).stream().map(this::masterView).toList();return new RunSummary(run.getId(),run.getExamSession().getId(),run.getExamSession().getCode(),run.getExamSession().getName(),run.getRunNumber(),run.getStatus(),run.getCandidateCount(),run.getModuleCount(),run.getTimetableEntryCount(),run.getConflictCount(),run.getGenerationPolicy(),run.getGeneratedByUserId(),run.getGeneratedAt(),run.getReviewedByUserId(),run.getApprovedByUserId(),run.getPublishedByUserId(),run.getPublishedAt(),run.getVersion(),entries);}
    private MasterEntrySummary masterView(ExamMasterTimetableEntry entry){List<VenueAllocationSummary> venues=allocationRepository.findAllByMasterTimetableEntryIdAndDeletedAtIsNullOrderByVenueCodeAsc(entry.getId()).stream().map(allocation->new VenueAllocationSummary(allocation.getId(),allocation.getVenue().getId(),allocation.getVenue().getCode(),allocation.getVenue().getName(),allocation.getAllocatedCapacity())).toList();return new MasterEntrySummary(entry.getId(),entry.getModuleId(),entry.getModuleCode(),entry.getModuleName(),entry.getCandidateCount(),entry.getExamSessionSlot().getId(),entry.getExamSessionSlot().getCode(),entry.getScheduledStartsAt(),entry.getScheduledEndsAt(),venues);}
    private StudentEntrySummary studentView(ExamStudentTimetableEntry entry){ExamMasterTimetableEntry master=entry.getMasterTimetableEntry();ExamTimetableGenerationRun run=entry.getGenerationRun();return new StudentEntrySummary(entry.getId(),run.getId(),run.getRunNumber(),run.getExamSession().getName(),entry.getStudentId(),entry.getStudentNumber(),entry.getModuleId(),entry.getModuleCode(),master.getModuleName(),entry.getScheduledStartsAt(),entry.getScheduledEndsAt(),entry.getVenueAllocation().getVenue().getCode(),entry.getVenueAllocation().getVenue().getName(),entry.getSeatNumber(),entry.getAttendanceStatus());}
    private record TimeWindow(Instant start,Instant end) {}
    private record ModuleSchedule(ExamSessionSlot slot,ModuleExamRequirement requirement,List<ExamCandidateModule> candidates,List<ExamVenue> venues,Instant endsAt) {}
}

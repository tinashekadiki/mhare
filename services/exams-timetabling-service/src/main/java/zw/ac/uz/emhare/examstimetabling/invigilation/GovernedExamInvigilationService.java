package zw.ac.uz.emhare.examstimetabling.invigilation;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.examstimetabling.invigilation.ExamInvigilationContracts.*;
import zw.ac.uz.emhare.examstimetabling.timetable.*;

/** Published-roster attendance reconciliation and segregated incident governance. @author Tinashe K */
@Service
public class GovernedExamInvigilationService {
    private final ExamAttendanceSessionRepository attendanceSessionRepository;
    private final ExamAttendanceRecordRepository attendanceRecordRepository;
    private final ExamIncidentReportRepository incidentReportRepository;
    private final ExamTimetableOperationsQueryService timetableQueryService;
    private final Clock clock;

    public GovernedExamInvigilationService(ExamAttendanceSessionRepository attendanceSessionRepository,
            ExamAttendanceRecordRepository attendanceRecordRepository,ExamIncidentReportRepository incidentReportRepository,
            ExamTimetableOperationsQueryService timetableQueryService,Clock clock) {
        this.attendanceSessionRepository=attendanceSessionRepository;this.attendanceRecordRepository=attendanceRecordRepository;
        this.incidentReportRepository=incidentReportRepository;this.timetableQueryService=timetableQueryService;this.clock=clock;
    }

    @Transactional(readOnly=true)
    public InvigilationWorkspace workspace() {
        return new InvigilationWorkspace(timetableQueryService.publishedVenueAllocations().stream().map(this::venueOperationView).toList());
    }

    @Transactional
    public AttendanceSessionSummary open(UUID venueAllocationId,OpenAttendanceSession command,UUID actor) {
        ExamTimetableVenueAllocation venueAllocation=timetableQueryService.requirePublishedVenueAllocation(venueAllocationId);
        if(attendanceSessionRepository.findByVenueAllocationIdAndDeletedAtIsNull(venueAllocationId).isPresent()) {
            throw new IllegalStateException("An attendance session already exists for this published venue allocation.");
        }
        List<ExamStudentTimetableEntry> candidates=timetableQueryService.studentsForAllocation(venueAllocationId);
        if(candidates.size()!=venueAllocation.getAllocatedCapacity()) {
            throw new IllegalStateException("Published venue roster does not reconcile to its allocated candidate count.");
        }
        ExamAttendanceSession attendanceSession;
        try {
            attendanceSession=attendanceSessionRepository.saveAndFlush(new ExamAttendanceSession(
                    venueAllocation,candidates.size(),actor,clock.instant(),command.openingReason()));
        } catch(DataIntegrityViolationException exception) {
            throw new IllegalStateException("An attendance session was opened concurrently. Refresh before continuing.",exception);
        }
        attendanceRecordRepository.saveAllAndFlush(candidates.stream()
                .map(candidate->new ExamAttendanceRecord(attendanceSession,candidate)).toList());
        return attendanceSessionView(attendanceSession);
    }

    @Transactional
    public AttendanceSessionSummary recordAttendance(UUID attendanceRecordId,RecordAttendance command,UUID actor) {
        ExamAttendanceRecord attendanceRecord=attendanceRecordRepository.findLockedByIdAndDeletedAtIsNull(attendanceRecordId)
                .orElseThrow(()->new IllegalArgumentException("Exam attendance record was not found."));
        attendanceRecord.record(command.attendanceStatus(),command.evidenceNotes(),actor,clock.instant(),command.expectedVersion());
        attendanceRecordRepository.saveAndFlush(attendanceRecord);
        return attendanceSessionView(attendanceRecord.getAttendanceSession());
    }

    @Transactional
    public AttendanceSessionSummary close(UUID attendanceSessionId,CloseAttendanceSession command,UUID actor) {
        ExamAttendanceSession attendanceSession=attendanceSessionRepository.findLockedByIdAndDeletedAtIsNull(attendanceSessionId)
                .orElseThrow(()->new IllegalArgumentException("Exam attendance session was not found."));
        List<ExamAttendanceRecord> records=attendanceRecordRepository
                .findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByStudentTimetableEntrySeatNumberAsc(attendanceSessionId);
        int presentCount=count(records,ExamAttendanceRecord.Status.PRESENT);
        int absentCount=count(records,ExamAttendanceRecord.Status.ABSENT);
        int excusedCount=count(records,ExamAttendanceRecord.Status.EXCUSED);
        attendanceSession.close(actor,clock.instant(),command.closureReason(),command.expectedVersion(),presentCount,absentCount,excusedCount);
        attendanceSessionRepository.saveAndFlush(attendanceSession);
        return attendanceSessionView(attendanceSession);
    }

    @Transactional
    public AttendanceSessionSummary reportIncident(UUID attendanceSessionId,ReportIncident command,UUID actor) {
        ExamAttendanceSession attendanceSession=attendanceSessionRepository.findLockedByIdAndDeletedAtIsNull(attendanceSessionId)
                .orElseThrow(()->new IllegalArgumentException("Exam attendance session was not found."));
        ExamStudentTimetableEntry studentEntry=command.studentTimetableEntryId()==null?null:
                timetableQueryService.requireStudentEntry(command.studentTimetableEntryId());
        if(studentEntry!=null&&!studentEntry.getVenueAllocation().getId().equals(attendanceSession.getVenueAllocation().getId())) {
            throw new IllegalArgumentException("Incident candidate does not belong to this venue attendance session.");
        }
        Instant now=clock.instant();String incidentNumber="INC-"+now.toEpochMilli()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        incidentReportRepository.saveAndFlush(new ExamIncidentReport(attendanceSession,studentEntry,incidentNumber,
                command.incidentType(),command.severity(),command.description(),command.occurredAt(),actor,now));
        return attendanceSessionView(attendanceSession);
    }

    @Transactional
    public AttendanceSessionSummary moveIncident(UUID incidentId,String action,IncidentWorkflowDecision command,UUID actor) {
        ExamIncidentReport incident=incidentReportRepository.findLockedByIdAndDeletedAtIsNull(incidentId)
                .orElseThrow(()->new IllegalArgumentException("Exam incident report was not found."));
        if("review".equals(action))incident.review(actor,clock.instant(),command.reason(),command.expectedVersion());
        else if("resolve".equals(action))incident.resolve(actor,clock.instant(),command.reason(),command.expectedVersion());
        else throw new IllegalArgumentException("Unsupported incident workflow action.");
        incidentReportRepository.saveAndFlush(incident);
        return attendanceSessionView(incident.getAttendanceSession());
    }

    private VenueOperationSummary venueOperationView(ExamTimetableVenueAllocation allocation) {
        ExamMasterTimetableEntry masterEntry=allocation.getMasterTimetableEntry();ExamTimetableGenerationRun run=masterEntry.getGenerationRun();
        AttendanceSessionSummary attendanceSession=attendanceSessionRepository.findByVenueAllocationIdAndDeletedAtIsNull(allocation.getId())
                .map(this::attendanceSessionView).orElse(null);
        return new VenueOperationSummary(allocation.getId(),run.getId(),run.getRunNumber(),masterEntry.getId(),masterEntry.getModuleCode(),
                masterEntry.getModuleName(),masterEntry.getScheduledStartsAt(),masterEntry.getScheduledEndsAt(),allocation.getVenue().getId(),
                allocation.getVenue().getCode(),allocation.getVenue().getName(),allocation.getVenue().getCampusName(),allocation.getAllocatedCapacity(),attendanceSession);
    }

    private AttendanceSessionSummary attendanceSessionView(ExamAttendanceSession session) {
        List<ExamAttendanceRecord> records=attendanceRecordRepository
                .findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByStudentTimetableEntrySeatNumberAsc(session.getId());
        List<IncidentSummary> incidents=incidentReportRepository
                .findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByOccurredAtDesc(session.getId()).stream().map(this::incidentView).toList();
        int presentCount=session.getStatus()==ExamAttendanceSession.Status.CLOSED?session.getPresentCandidateCount():count(records,ExamAttendanceRecord.Status.PRESENT);
        int absentCount=session.getStatus()==ExamAttendanceSession.Status.CLOSED?session.getAbsentCandidateCount():count(records,ExamAttendanceRecord.Status.ABSENT);
        int excusedCount=session.getStatus()==ExamAttendanceSession.Status.CLOSED?session.getExcusedCandidateCount():count(records,ExamAttendanceRecord.Status.EXCUSED);
        int outstandingCount=count(records,ExamAttendanceRecord.Status.EXPECTED);
        return new AttendanceSessionSummary(session.getId(),session.getStatus(),session.getExpectedCandidateCount(),presentCount,
                absentCount,excusedCount,outstandingCount,session.getOpenedByUserId(),session.getOpenedAt(),session.getOpeningReason(),
                session.getClosedByUserId(),session.getClosedAt(),session.getClosureReason(),session.getVersion(),
                records.stream().map(this::attendanceRecordView).toList(),incidents);
    }
    private AttendanceRecordSummary attendanceRecordView(ExamAttendanceRecord record) {
        ExamStudentTimetableEntry student=record.getStudentTimetableEntry();
        return new AttendanceRecordSummary(record.getId(),student.getId(),student.getStudentId(),student.getStudentNumber(),
                student.getSeatNumber(),record.getAttendanceStatus(),record.getRecordedByUserId(),record.getRecordedAt(),
                record.getEvidenceNotes(),record.getVersion());
    }
    private IncidentSummary incidentView(ExamIncidentReport incident) {
        ExamStudentTimetableEntry student=incident.getStudentTimetableEntry();
        return new IncidentSummary(incident.getId(),incident.getIncidentNumber(),student==null?null:student.getId(),
                student==null?null:student.getStudentNumber(),incident.getIncidentType(),incident.getSeverity(),incident.getDescription(),
                incident.getOccurredAt(),incident.getStatus(),incident.getReportedByUserId(),incident.getReportedAt(),
                incident.getReviewedByUserId(),incident.getReviewedAt(),incident.getReviewReason(),incident.getResolvedByUserId(),
                incident.getResolvedAt(),incident.getResolution(),incident.getVersion());
    }
    private static int count(List<ExamAttendanceRecord> records,ExamAttendanceRecord.Status status) {
        return (int)records.stream().filter(record->record.getAttendanceStatus()==status).count();
    }
}

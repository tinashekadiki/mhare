package zw.ac.uz.emhare.examstimetabling.timetable;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** @author Tinashe K */
public final class ExamTimetableContracts {
    private ExamTimetableContracts() {}
    public record GenerateTimetable(@NotNull UUID examSessionId) {}
    public record WorkflowDecision(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record VenueAllocationSummary(UUID id,UUID venueId,String venueCode,String venueName,int allocatedCapacity) {}
    public record MasterEntrySummary(UUID id,UUID moduleId,String moduleCode,String moduleName,int candidateCount,
            UUID slotId,String slotCode,Instant startsAt,Instant endsAt,List<VenueAllocationSummary> venues) {}
    public record RunSummary(UUID id,UUID examSessionId,String sessionCode,String sessionName,String runNumber,
            ExamTimetableGenerationRun.Status status,int candidateCount,int moduleCount,int timetableEntryCount,int conflictCount,
            Map<String,Object> generationPolicy,UUID generatedByUserId,Instant generatedAt,UUID reviewedByUserId,
            UUID approvedByUserId,UUID publishedByUserId,Instant publishedAt,long version,List<MasterEntrySummary> entries) {}
    public record StudentEntrySummary(UUID id,UUID runId,String runNumber,String sessionName,UUID studentId,String studentNumber,
            UUID moduleId,String moduleCode,String moduleName,Instant startsAt,Instant endsAt,String venueCode,String venueName,
            int seatNumber,ExamStudentTimetableEntry.AttendanceStatus attendanceStatus) {}
}

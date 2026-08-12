package zw.ac.uz.emhare.examstimetabling.invigilation.api.model;

import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamAttendanceRecord;
import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamAttendanceSession;
import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamIncidentReport;

import zw.ac.uz.emhare.examstimetabling.invigilation.*;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class ExamInvigilationApiModels {
    private ExamInvigilationApiModels() {}
    public record OpenAttendanceSession(@NotBlank @Size(max=1000) String openingReason) {}
    public record RecordAttendance(@NotNull ExamAttendanceRecord.Status attendanceStatus,
            @Size(max=1000) String evidenceNotes,@Min(0) long expectedVersion) {}
    public record CloseAttendanceSession(@NotBlank @Size(max=1000) String closureReason,@Min(0) long expectedVersion) {}
    public record ReportIncident(UUID studentTimetableEntryId,@NotNull ExamIncidentReport.Type incidentType,
            @NotNull ExamIncidentReport.Severity severity,@NotBlank @Size(max=2000) String description,@NotNull Instant occurredAt) {}
    public record IncidentWorkflowDecision(@NotBlank @Size(max=2000) String reason,@Min(0) long expectedVersion) {}
    public record AttendanceRecordSummary(UUID id,UUID studentTimetableEntryId,UUID studentId,String studentNumber,
            int seatNumber,ExamAttendanceRecord.Status attendanceStatus,UUID recordedByUserId,Instant recordedAt,
            String evidenceNotes,long version) {}
    public record IncidentSummary(UUID id,String incidentNumber,UUID studentTimetableEntryId,String studentNumber,
            ExamIncidentReport.Type incidentType,ExamIncidentReport.Severity severity,String description,Instant occurredAt,
            ExamIncidentReport.Status status,UUID reportedByUserId,Instant reportedAt,UUID reviewedByUserId,
            Instant reviewedAt,String reviewReason,UUID resolvedByUserId,Instant resolvedAt,String resolution,long version) {}
    public record AttendanceSessionSummary(UUID id,ExamAttendanceSession.Status status,int expectedCandidateCount,
            int presentCandidateCount,int absentCandidateCount,int excusedCandidateCount,int outstandingCandidateCount,
            UUID openedByUserId,Instant openedAt,String openingReason,UUID closedByUserId,Instant closedAt,
            String closureReason,long version,List<AttendanceRecordSummary> attendanceRecords,List<IncidentSummary> incidents) {}
    public record VenueOperationSummary(UUID venueAllocationId,UUID generationRunId,String runNumber,UUID masterTimetableEntryId,
            String moduleCode,String moduleName,Instant scheduledStartsAt,Instant scheduledEndsAt,UUID venueId,
            String venueCode,String venueName,String campusName,int allocatedCandidateCount,AttendanceSessionSummary attendanceSession) {}
    public record InvigilationWorkspace(List<VenueOperationSummary> venueOperations) {}
}

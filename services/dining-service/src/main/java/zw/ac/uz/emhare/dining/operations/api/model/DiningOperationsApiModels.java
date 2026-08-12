package zw.ac.uz.emhare.dining.operations.api.model;

import zw.ac.uz.emhare.dining.operations.domain.model.DiningWorkflowEvent;
import zw.ac.uz.emhare.dining.operations.domain.model.MealAttendanceEvent;
import zw.ac.uz.emhare.dining.operations.domain.model.MealServiceSession;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDietaryRequirement;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDiningAssignment;

import zw.ac.uz.emhare.dining.operations.*;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** @author Tinashe K */
public final class DiningOperationsApiModels {
    private DiningOperationsApiModels() {}
    public record PrepareAssignment(@NotNull UUID studentId,@NotBlank String studentNumber,@NotBlank String studentName,@NotNull UUID academicPeriodId,@NotBlank String academicPeriodCode,@NotBlank String programmeCode,String studentGroupCode,@NotNull UUID diningHallId,@NotNull UUID diningPlanId,UUID accommodationAllocationId,@NotNull LocalDate effectiveFrom,@NotNull LocalDate effectiveUntil) {}
    public record AssignmentAction(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record RecordDietaryRequirement(@NotNull UUID studentId,@NotBlank String studentNumber,@NotBlank String requirementCode,@NotBlank @Size(max=1000) String description,@NotNull StudentDietaryRequirement.Severity severity,UUID clinicalDocumentId,@NotNull LocalDate effectiveFrom,LocalDate effectiveUntil) {}
    public record ResolveDietaryRequirement(@NotNull StudentDietaryRequirement.Status targetStatus,@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record PlanMealSession(@NotNull UUID diningHallId,@NotNull UUID mealOptionId,@NotNull LocalDate serviceDate,@NotNull Instant scheduledOpensAt,@NotNull Instant scheduledClosesAt,@Min(0) Integer expectedServings) {}
    public record SessionAction(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record ReconcileSession(@Min(0) int countedServings,@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record CaptureAttendance(@NotNull UUID sessionId,@NotNull UUID studentId,@NotBlank String studentNumber,@NotBlank String studentName,@NotNull MealAttendanceEvent.CaptureChannel captureChannel,String deviceId,@NotBlank @Size(max=120) String idempotencyKey) {}
    public record ReverseAttendance(@NotBlank String reasonCode,@NotBlank @Size(max=1000) String reason) {}
    public record AssignmentSummary(UUID id,String assignmentNumber,UUID studentId,String studentNumber,String studentName,UUID academicPeriodId,String academicPeriodCode,String programmeCode,String studentGroupCode,UUID diningHallId,String diningHallCode,UUID diningPlanId,String diningPlanCode,UUID accommodationAllocationId,LocalDate effectiveFrom,LocalDate effectiveUntil,StudentDiningAssignment.Status status,UUID preparedByUserId,UUID approvedByUserId,Instant approvedAt,String approvalReason,UUID endedByUserId,Instant endedAt,String endReason,StudentDiningAssignment.BillingStatus billingStatus,long version) {}
    public record DietarySummary(UUID id,UUID studentId,String studentNumber,String requirementCode,String description,StudentDietaryRequirement.Severity severity,UUID clinicalDocumentId,LocalDate effectiveFrom,LocalDate effectiveUntil,StudentDietaryRequirement.Status status,UUID recordedByUserId,UUID resolvedByUserId,Instant resolvedAt,String resolutionReason,long version) {}
    public record SessionSummary(UUID id,String sessionNumber,UUID diningHallId,String diningHallCode,UUID mealOptionId,String mealOptionCode,LocalDate serviceDate,Instant scheduledOpensAt,Instant scheduledClosesAt,MealServiceSession.Status status,UUID preparedByUserId,UUID openedByUserId,Instant openedAt,UUID closedByUserId,Instant closedAt,UUID reconciledByUserId,Instant reconciledAt,String reconciliationReason,Integer expectedServings,Integer countedServings,long netAdmitted,long version) {}
    public record AttendanceSummary(UUID id,String eventNumber,UUID sessionId,String sessionNumber,UUID assignmentId,UUID studentId,String studentNumber,String studentName,MealAttendanceEvent.Outcome outcome,String denialReasonCode,String denialReason,UUID capturedByUserId,Instant capturedAt,MealAttendanceEvent.CaptureChannel captureChannel,String deviceId,String idempotencyKey,boolean reversed) {}
    public record ReversalSummary(UUID id,UUID attendanceEventId,String eventNumber,String reasonCode,String reason,UUID reversedByUserId,Instant reversedAt) {}
    public record WorkflowEventSummary(UUID id,DiningWorkflowEvent.AggregateType aggregateType,UUID aggregateId,String previousState,String newState,String eventType,String reason,UUID actorUserId,Instant occurredAt) {}
    public record AttendanceStatisticSummary(String dimension,String groupCode,long admitted,long denied,long reversed,long netAdmitted) {}
    public record OperationsRegister(List<AssignmentSummary> assignments,List<DietarySummary> dietaryRequirements,List<SessionSummary> sessions,List<AttendanceSummary> attendanceEvents,List<ReversalSummary> reversals,List<WorkflowEventSummary> workflowEvents,List<AttendanceStatisticSummary> attendanceStatistics) {}
}

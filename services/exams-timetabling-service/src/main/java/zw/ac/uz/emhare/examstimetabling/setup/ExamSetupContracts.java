package zw.ac.uz.emhare.examstimetabling.setup;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class ExamSetupContracts {
    private ExamSetupContracts() {}
    public record CreateVenueType(@NotBlank String code,@NotBlank String name,String description) {}
    public record CreateVenue(@NotNull UUID venueTypeId,@NotBlank String code,@NotBlank String name,@NotBlank String campusName,
            String buildingName,String roomName,@Min(1) int examinationCapacity,String accessibilityNotes) {}
    public record AddAvailability(@NotNull Instant availableFrom,@NotNull Instant availableUntil,String notes) {}
    public record CreateSession(@NotNull UUID academicPeriodId,@NotBlank String academicPeriodCode,@NotBlank String code,@NotBlank String name,
            @NotNull ExamSession.AssessmentType assessmentType,@NotNull LocalDate startsOn,@NotNull LocalDate endsOn) {}
    public record CreateSlot(@NotBlank String code,@NotNull Instant startsAt,@NotNull Instant endsAt) {}
    public record CreateRequirement(@NotNull UUID academicPeriodId,@NotNull UUID moduleId,@NotBlank String moduleCode,
            @NotBlank String moduleName,@Min(15) @Max(480) int durationMinutes,@Min(0) @Max(120) int readingTimeMinutes,
            UUID requiredVenueTypeId,String specialRequirements) {}
    public record WorkflowDecision(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record VenueTypeSummary(UUID id,String code,String name,String description,boolean active,long version) {}
    public record AvailabilitySummary(UUID id,Instant availableFrom,Instant availableUntil,String notes) {}
    public record VenueSummary(UUID id,UUID venueTypeId,String venueTypeCode,String code,String name,String campusName,String buildingName,
            String roomName,int examinationCapacity,String accessibilityNotes,boolean active,long version,List<AvailabilitySummary> availability) {}
    public record SlotSummary(UUID id,String code,Instant startsAt,Instant endsAt) {}
    public record SessionSummary(UUID id,UUID academicPeriodId,String academicPeriodCode,String code,String name,
            ExamSession.AssessmentType assessmentType,LocalDate startsOn,LocalDate endsOn,ExamSession.Status status,
            UUID approvedByUserId,Instant approvedAt,String approvalReason,long version,List<SlotSummary> slots) {}
    public record RequirementSummary(UUID id,UUID academicPeriodId,UUID moduleId,String moduleCode,String moduleName,int requirementVersion,
            int durationMinutes,int readingTimeMinutes,UUID requiredVenueTypeId,String requiredVenueTypeCode,String specialRequirements,
            ModuleExamRequirement.Status status,long version) {}
    public record SetupRegister(List<VenueTypeSummary> venueTypes,List<VenueSummary> venues,List<SessionSummary> sessions,
            List<RequirementSummary> requirements) {}
}

package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.ComponentType;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.CaptureMethod;

/** @author Tinashe K */
public final class AssessmentCommands {
    private AssessmentCommands() {}
    public record CreateOffering(@NotNull UUID moduleId,@NotNull UUID academicPeriodId,@NotNull UUID assignedInstructorUserId){}
    public record CreateScheme(@NotBlank @Size(max=150) String name,@NotEmpty List<@Valid ComponentDefinition> components){}
    public record ComponentDefinition(@NotBlank @Size(max=30) String code,@NotBlank @Size(max=150) String name,@NotNull ComponentType componentType,@NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal weightPercent,@NotNull @DecimalMin("0.01") BigDecimal maximumMark,@NotNull Instant captureOpensAt,@NotNull Instant captureClosesAt,@Min(1) int sortOrder){}
    public record Decision(@Min(0) long expectedVersion,@NotBlank @Size(max=1000) String reason){}
    public record CaptureMark(@NotNull UUID rosterEntryId,@NotNull @DecimalMin("0.00") BigDecimal score,@Min(0) long expectedVersion){}
    public record CaptureMarkBatch(@NotNull CaptureMethod captureMethod,@NotEmpty List<@Valid CaptureMark> marks){}
    public record RequestAmendment(@NotNull @DecimalMin("0.00") BigDecimal proposedScore,@NotBlank @Size(max=1000) String reason){}
}

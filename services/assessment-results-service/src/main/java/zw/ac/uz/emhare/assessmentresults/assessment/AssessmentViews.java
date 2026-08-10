package zw.ac.uz.emhare.assessmentresults.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.*;

/** @author Tinashe K */
public final class AssessmentViews {
    private AssessmentViews() {}
    public record OfferingSummary(UUID id,UUID moduleId,String moduleCode,String moduleName,UUID academicPeriodId,String academicPeriodCode,String academicPeriodName,UUID assignedInstructorUserId,OfferingStatus status,long version,int rosterCount,List<SchemeSummary> schemes){}
    public record RosterSourceSummary(UUID moduleId,String moduleCode,String moduleName,UUID academicPeriodId,String academicPeriodCode,String academicPeriodName,int eligibleStudentCount,boolean offeringCreated){}
    public record SchemeSummary(UUID id,int schemeVersion,String name,SchemeStatus status,String approvalReason,UUID approvedByUserId,Instant approvedAt,long version,List<ComponentSummary> components){}
    public record ComponentSummary(UUID id,String code,String name,ComponentType componentType,BigDecimal weightPercent,BigDecimal maximumMark,Instant captureOpensAt,Instant captureClosesAt,int sortOrder){}
    public record RosterMarkSummary(UUID rosterEntryId,UUID studentId,String studentNumber,String studentName,UUID componentId,String componentCode,UUID markId,Integer revisionNumber,BigDecimal score,MarkStatus status,long markVersion){}
    public record MarkSummary(UUID id,UUID componentId,UUID rosterEntryId,int revisionNumber,UUID supersedesMarkId,BigDecimal score,MarkStatus status,CaptureMethod captureMethod,UUID capturedByUserId,Instant capturedAt,UUID submittedByUserId,Instant submittedAt,long version){}
    public record AmendmentSummary(UUID id,UUID originalMarkId,BigDecimal originalScore,BigDecimal proposedScore,String reason,AmendmentStatus status,UUID requestedByUserId,Instant requestedAt,UUID decidedByUserId,Instant decidedAt,String decisionReason,UUID replacementMarkId,long version){}
    public record CalculationOutcomeSummary(UUID rosterEntryId,String studentNumber,BigDecimal weightedTotal,boolean complete,String missingComponentCodes){}
    public record CalculationRunSummary(UUID id,UUID offeringId,UUID schemeId,int rosterCount,int completeResultCount,int incompleteResultCount,CalculationStatus status,Instant initiatedAt,boolean publicationEvidenceAvailable,List<CalculationOutcomeSummary> outcomes){}
}

package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Tinashe K
 */
public final class AdmissionsDocumentViews {
  private AdmissionsDocumentViews() {}

  public record DocumentRequirementSummary(
      UUID id,
      UUID applicationTypeId,
      String requirementCode,
      String requirementName,
      boolean required,
      String captureSectionCode,
      List<String> applicantCategoryCodes,
      int sortOrder,
      boolean active,
      long version) {}

  public record ApplicationDocumentRequirementState(
      String requirementCode,
      String requirementName,
      boolean required,
      String captureSectionCode,
      List<String> applicantCategoryCodes,
      String state,
      UUID applicationDocumentId,
      UUID documentId,
      String fileName,
      String mimeType,
      String checksumSha256,
      Instant linkedAt,
      UUID verifiedByUserId,
      Instant verifiedAt,
      String rejectionReason,
      long documentVersion,
      long version) {}

  public record ApplicationDocumentRegister(
      UUID applicationId,
      String applicationNumber,
      boolean requiredDocumentsUploaded,
      boolean requiredDocumentsVerified,
      List<String> missingRequirementCodes,
      List<String> pendingRequirementCodes,
      List<String> rejectedRequirementCodes,
      List<ApplicationDocumentRequirementState> requirements) {}

  public record AcademicUnitApplicationDocumentEntry(
      UUID applicationId,
      String applicationNumber,
      String applicantName,
      String applicationStatus,
      ApplicationDocumentRegister documents) {}
}

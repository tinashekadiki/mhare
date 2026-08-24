package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantIdentityNameCorrection;

/** Stable applicant and staff view of an identity-name mismatch workflow. @author Tinashe K */
public record IdentityNameCorrectionSummary(
    UUID id,
    UUID applicationId,
    UUID documentId,
    IdentityName registeredName,
    IdentityName documentName,
    String status,
    String requestReason,
    Instant requestedAt,
    UUID requestedByUserId,
    String decisionReason,
    Instant decidedAt,
    UUID decidedByUserId,
    Instant coreSynchronizedAt,
    long version) {

  public static IdentityNameCorrectionSummary unresolved(
      UUID applicationId, UUID documentId, IdentityName registeredName, IdentityName documentName) {
    return new IdentityNameCorrectionSummary(
        null,
        applicationId,
        documentId,
        registeredName,
        documentName,
        "UNRESOLVED",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0);
  }

  public static IdentityNameCorrectionSummary from(ApplicantIdentityNameCorrection value) {
    return new IdentityNameCorrectionSummary(
        value.getId(),
        value.getApplication().getId(),
        value.getDocumentId(),
        new IdentityName(
            value.getRegisteredFirstName(),
            value.getRegisteredMiddleNames(),
            value.getRegisteredLastName()),
        new IdentityName(
            value.getDocumentFirstName(),
            value.getDocumentMiddleNames(),
            value.getDocumentLastName()),
        value.getStatus().name(),
        value.getRequestReason(),
        value.getRequestedAt(),
        value.getRequestedByUserId(),
        value.getDecisionReason(),
        value.getDecidedAt(),
        value.getDecidedByUserId(),
        value.getCoreSynchronizedAt(),
        value.getVersion());
  }

  public boolean hasMismatch() {
    return !normalize(registeredName.firstName()).equals(normalize(documentName.firstName()))
        || !normalize(registeredName.lastName()).equals(normalize(documentName.lastName()));
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
  }

  public record IdentityName(String firstName, String middleNames, String lastName) {
    public String displayName() {
      return java.util.stream.Stream.of(firstName, middleNames, lastName)
          .filter(part -> part != null && !part.isBlank())
          .collect(java.util.stream.Collectors.joining(" "));
    }
  }
}

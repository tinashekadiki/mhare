package zw.ac.uz.emhare.admissions.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.admissions.integration.http.DocumentsReportingHttpService;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/**
 * @author Tinashe K
 */
@Component
public class DocumentsReportingClient {
  private final DocumentsReportingHttpService documentsReportingHttpService;

  public DocumentsReportingClient(DocumentsReportingHttpService documentsReportingHttpService) {
    this.documentsReportingHttpService = documentsReportingHttpService;
  }

  public UploadedDocumentSnapshot getUploadedDocument(UUID documentId) {
    try {
      UploadedDocumentSnapshot snapshot =
          documentsReportingHttpService.getUploadedDocument(documentId);
      if (snapshot == null) {
        throw new ServiceDependencyUnavailableException(
            "Documents/Reporting returned an empty document record.", null);
      }
      return snapshot;
    } catch (ServiceDependencyUnavailableException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
          || exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        throw new IllegalArgumentException("Uploaded document was not found.", exception);
      }
      if (exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
        throw new org.springframework.security.access.AccessDeniedException(
            "The uploaded document is not accessible to this user.", exception);
      }
      throw unavailable(exception);
    } catch (RestClientException exception) {
      throw unavailable(exception);
    } catch (RuntimeException exception) {
      throw unavailable(exception);
    }
  }

  public List<UploadedDocumentSnapshot> getUploadedDocuments(String ownerType, UUID ownerId) {
    try {
      List<UploadedDocumentSnapshot> snapshots =
          documentsReportingHttpService.getUploadedDocuments(ownerType, ownerId);
      return snapshots == null ? List.of() : List.copyOf(snapshots);
    } catch (RestClientException exception) {
      throw unavailable(exception);
    } catch (RuntimeException exception) {
      throw unavailable(exception);
    }
  }

  public DocumentOcrExtractionSnapshot getOcrExtraction(UUID documentId) {
    try {
      DocumentOcrExtractionSnapshot snapshot =
          documentsReportingHttpService.getOcrExtraction(documentId);
      if (snapshot == null) {
        throw new ServiceDependencyUnavailableException(
            "Documents/Reporting returned an empty OCR extraction.", null);
      }
      return snapshot;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
          || exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        throw new IllegalArgumentException("OCR extraction was not found.", exception);
      }
      if (exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
        throw new org.springframework.security.access.AccessDeniedException(
            "The OCR extraction is not accessible to this user.", exception);
      }
      throw unavailable(exception);
    } catch (RestClientException exception) {
      throw unavailable(exception);
    }
  }

  private ServiceDependencyUnavailableException unavailable(Throwable exception) {
    return new ServiceDependencyUnavailableException(
        "Documents/Reporting is unavailable, so document evidence cannot be safely linked.",
        exception);
  }

  public record UploadedDocumentSnapshot(
      UUID id,
      String ownerType,
      UUID ownerId,
      String documentTypeCode,
      String originalFileName,
      String mimeType,
      long fileSizeBytes,
      String checksumSha256,
      UUID uploadedByUserId,
      Instant uploadedAt,
      String verificationStatus,
      UUID verifiedByUserId,
      Instant verifiedAt,
      String verificationComment,
      String rejectionReason,
      UUID replacesDocumentId,
      String extractionStatus,
      long version) {}

  public record DocumentOcrExtractionSnapshot(
      UUID documentId,
      String status,
      String engineName,
      String engineVersion,
      String structuredExtractionJson,
      String proposedFactsJson,
      String confidenceJson,
      String warningsJson,
      int attemptCount,
      Instant queuedAt,
      Instant startedAt,
      Instant completedAt,
      String lastFailureCode,
      String lastFailureMessage,
      long version) {}
}

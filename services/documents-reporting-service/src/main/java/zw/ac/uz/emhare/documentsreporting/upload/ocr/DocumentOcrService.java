package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.DocumentOcrExtractionRepository;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrViews.DocumentOcrExtractionSummary;

/** Queues, reads, and explicitly retries owner-authorised OCR evidence. @author Tinashe K */
@Service
public class DocumentOcrService {

  private final DocumentOcrExtractionRepository repository;
  private final DocumentOcrProperties properties;
  private final Clock clock;

  public DocumentOcrService(
      DocumentOcrExtractionRepository repository, DocumentOcrProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public DocumentOcrExtractionSummary queue(UploadedDocument document) {
    DocumentOcrExtraction extraction =
        repository
            .findByUploadedDocumentIdAndDeletedAtIsNull(document.getId())
            .orElseGet(
                () ->
                    new DocumentOcrExtraction(
                        document,
                        properties.engineName(),
                        properties.engineVersion(),
                        clock.instant()));
    if (!properties.enabled()) {
      extraction.unsupported("[\"OCR is disabled in this environment.\"]", clock.instant());
    }
    return summary(repository.saveAndFlush(extraction));
  }

  @Transactional(readOnly = true)
  public DocumentOcrExtractionSummary extraction(UUID documentId) {
    return repository
        .findByUploadedDocumentIdAndDeletedAtIsNull(documentId)
        .map(this::summary)
        .orElseThrow(() -> new IllegalArgumentException("OCR extraction was not found."));
  }

  @Transactional(readOnly = true)
  public String statusOrNull(UUID documentId) {
    return repository
        .findByUploadedDocumentIdAndDeletedAtIsNull(documentId)
        .map(value -> value.getStatus().name())
        .orElse(null);
  }

  @Transactional
  public DocumentOcrExtractionSummary retry(UUID documentId) {
    DocumentOcrExtraction extraction =
        repository
            .findByUploadedDocumentIdAndDeletedAtIsNull(documentId)
            .orElseThrow(() -> new IllegalArgumentException("OCR extraction was not found."));
    extraction.retry(clock.instant());
    return summary(repository.saveAndFlush(extraction));
  }

  private DocumentOcrExtractionSummary summary(DocumentOcrExtraction extraction) {
    return new DocumentOcrExtractionSummary(
        extraction.getUploadedDocument().getId(),
        extraction.getStatus().name(),
        extraction.getEngineName(),
        extraction.getEngineVersion(),
        extraction.getStructuredExtractionJson(),
        extraction.getProposedFactsJson(),
        extraction.getConfidenceJson(),
        extraction.getWarningsJson(),
        extraction.getAttemptCount(),
        extraction.getQueuedAt(),
        extraction.getStartedAt(),
        extraction.getCompletedAt(),
        extraction.getLastFailureCode(),
        extraction.getLastFailureMessage(),
        extraction.getVersion());
  }
}

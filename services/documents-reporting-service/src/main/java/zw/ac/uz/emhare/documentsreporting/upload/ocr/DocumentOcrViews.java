package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Tinashe K
 */
public final class DocumentOcrViews {
  private DocumentOcrViews() {}

  public record DocumentOcrExtractionSummary(
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

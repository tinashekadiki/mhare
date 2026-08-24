package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;

/**
 * @author Tinashe K
 */
class DocumentOcrExtractionTest {

  @Test
  void retriesTransientFailuresThreeTimesThenRequiresExplicitRetry() {
    Instant queuedAt = Instant.parse("2026-08-23T10:00:00Z");
    DocumentOcrExtraction extraction =
        new DocumentOcrExtraction(null, "Docling Serve", "1.29.0", queuedAt);

    extraction.markProcessing(queuedAt);
    extraction.fail(
        "TIMEOUT", "temporary timeout\nwithout document text", 3, Duration.ofSeconds(10), queuedAt);
    assertEquals(DocumentOcrStatus.QUEUED, extraction.getStatus());
    assertEquals(queuedAt.plusSeconds(10), extraction.getNextAttemptAt());

    extraction.markProcessing(queuedAt.plusSeconds(10));
    extraction.fail(
        "HTTP_503", "service unavailable", 3, Duration.ofSeconds(10), queuedAt.plusSeconds(10));
    extraction.markProcessing(queuedAt.plusSeconds(30));
    extraction.fail(
        "HTTP_503", "service unavailable", 3, Duration.ofSeconds(10), queuedAt.plusSeconds(30));

    assertEquals(DocumentOcrStatus.FAILED, extraction.getStatus());
    assertEquals(3, extraction.getAttemptCount());
    extraction.retry(queuedAt.plusSeconds(60));
    assertEquals(DocumentOcrStatus.QUEUED, extraction.getStatus());
    assertEquals(0, extraction.getAttemptCount());
  }

  @Test
  void completedExtractionCannotBeProcessedAgain() {
    Instant now = Instant.parse("2026-08-23T10:00:00Z");
    DocumentOcrExtraction extraction =
        new DocumentOcrExtraction(null, "Docling Serve", "1.29.0", now);
    extraction.markProcessing(now);
    extraction.complete("{}", "{}", "{}", "[]", now.plusSeconds(1));

    assertEquals(DocumentOcrStatus.COMPLETED, extraction.getStatus());
    assertThrows(IllegalStateException.class, () -> extraction.markProcessing(now.plusSeconds(2)));
  }
}

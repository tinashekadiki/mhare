package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.DocumentOcrExtractionRepository;

/** OCR queue/read/retry orchestration coverage. @author Tinashe K */
class DocumentOcrServiceTest {

  private final Instant now = Instant.parse("2026-08-23T10:00:00Z");
  private final UUID documentId = UUID.randomUUID();
  private DocumentOcrExtractionRepository repository;
  private UploadedDocument document;

  @BeforeEach
  void setUp() {
    repository = mock(DocumentOcrExtractionRepository.class);
    document = mock(UploadedDocument.class);
    when(document.getId()).thenReturn(documentId);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void queuesOnlyOneExtractionAndReadsItsStatus() {
    DocumentOcrService service = service(true);
    when(repository.findByUploadedDocumentIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.empty());

    var queued = service.queue(document);

    assertThat(queued.documentId()).isEqualTo(documentId);
    assertThat(queued.status()).isEqualTo("QUEUED");
    assertThat(queued.queuedAt()).isEqualTo(now);
    verify(repository).saveAndFlush(any(DocumentOcrExtraction.class));

    DocumentOcrExtraction existing = extraction();
    when(repository.findByUploadedDocumentIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(existing));
    assertThat(service.queue(document).status()).isEqualTo("QUEUED");
    assertThat(service.statusOrNull(documentId)).isEqualTo("QUEUED");
    assertThat(service.extraction(documentId).engineName()).isEqualTo("DOCLING_RAPIDOCR");
  }

  @Test
  void marksDisabledOcrUnsupportedAndReturnsNullForUnknownStatus() {
    DocumentOcrService service = service(false);
    when(repository.findByUploadedDocumentIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.empty());

    var queued = service.queue(document);

    assertThat(queued.status()).isEqualTo("UNSUPPORTED");
    assertThat(queued.warningsJson()).contains("disabled");
    assertThat(service.statusOrNull(UUID.randomUUID())).isNull();
  }

  @Test
  void retriesOnlyFailedExtractionsAndReportsMissingRecords() {
    DocumentOcrService service = service(true);
    DocumentOcrExtraction failed = extraction();
    failed.markProcessing(now);
    failed.fail("timeout", "safe", 1, Duration.ZERO, now);
    when(repository.findByUploadedDocumentIdAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(failed));

    var retried = service.retry(documentId);

    assertThat(retried.status()).isEqualTo("QUEUED");
    assertThat(retried.attemptCount()).isZero();
    assertThat(retried.lastFailureCode()).isNull();

    UUID missing = UUID.randomUUID();
    assertThatThrownBy(() -> service.extraction(missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
    assertThatThrownBy(() -> service.retry(missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
  }

  private DocumentOcrService service(boolean enabled) {
    return new DocumentOcrService(
        repository,
        new DocumentOcrProperties(enabled, null, null, null, 3, null, null),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private DocumentOcrExtraction extraction() {
    return new DocumentOcrExtraction(document, "DOCLING_RAPIDOCR", "docling-serve-v1.29.0", now);
  }
}

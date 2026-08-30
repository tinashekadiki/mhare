package zw.ac.uz.emhare.documentsreporting.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterRequestedEvent;
import zw.ac.uz.emhare.common.messaging.ProgressionDecisionPublishedEvent;
import zw.ac.uz.emhare.documentsreporting.document.OfficialResultSlipPdfRenderer.RenderedPdf;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionResultProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.OfferLetterProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.integration.DocumentVerificationOutboxService;

/** Durable official document generation and bounded retry state. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class OfficialDocumentLifecycleTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private GeneratedDocumentRepository documents;
  @Mock private ProgressionDecisionResultProjectionRepository results;
  @Mock private OfficialResultSlipPdfRenderer resultRenderer;
  @Mock private OfficialOfferLetterPdfRenderer offerRenderer;
  @Mock private DocumentVerificationOutboxService outbox;
  @Mock private S3Client s3;
  private OfficialDocumentGenerationWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new OfficialDocumentGenerationWorker(
            documents,
            results,
            resultRenderer,
            offerRenderer,
            outbox,
            s3,
            new DocumentsStorageProperties(
                "http://storage.test",
                "us-east-1",
                "test-access",
                "test-secret",
                "official-documents",
                true,
                60,
                1000000),
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().versionId("object-version-1").build());
  }

  @ParameterizedTest
  @ValueSource(strings = {"store", "fail"})
  void queuedDocumentCannotClaimSuccessOrFailureBeforeGeneration(String operation) {
    GeneratedDocument document = offer();
    assertThatThrownBy(
            () -> {
              if (operation.equals("store"))
                document.markStored("bucket", "key", "v1", "sha", 100, 1, NOW);
              else document.markFailed(new IllegalStateException("Not started"), NOW);
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only a generating");
    assertThat(document.getStatus()).isEqualTo(GeneratedDocument.Status.REQUESTED);
  }

  @Test
  void generatingAndStoredDocumentsCannotBeStartedAgain() {
    GeneratedDocument document = offer();
    document.beginGeneration(NOW);
    assertThatThrownBy(() -> document.beginGeneration(NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("queued or failed");
    document.markStored("bucket", "key", "v1", "sha", 100, 1, NOW);
    assertThatThrownBy(() -> document.beginGeneration(NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("queued or failed");
    assertThat(document.getGenerationAttemptCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "short", "long"})
  void generationFailurePreservesBoundedDiagnosticEvidence(String reason) {
    GeneratedDocument document = offer();
    document.beginGeneration(NOW);
    String message =
        reason.equals("null")
            ? null
            : reason.equals("short") ? "Storage unavailable" : "x".repeat(1100);
    document.markFailed(new IllegalStateException(message), NOW);
    assertThat(document.getStatus()).isEqualTo(GeneratedDocument.Status.FAILED);
    assertThat(document.getLastFailureReason())
        .isEqualTo(
            reason.equals("null")
                ? "IllegalStateException"
                : reason.equals("short") ? message : "x".repeat(1000));
  }

  @Test
  void retryRequiresMatchingVersionAndFailedStateThenClearsTheFailure() {
    GeneratedDocument document = offer();
    assertThatThrownBy(() -> document.requestRetry(0, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only a failed");
    document.beginGeneration(NOW);
    document.markFailed(new IllegalStateException("Temporary failure"), NOW);
    assertThatThrownBy(() -> document.requestRetry(1, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("changed");
    document.requestRetry(0, NOW.plusSeconds(1));
    assertThat(document.getStatus()).isEqualTo(GeneratedDocument.Status.REQUESTED);
    assertThat(document.getLastFailureReason()).isNull();
    document.beginGeneration(NOW.plusSeconds(1));
    assertThat(document.getGenerationAttemptCount()).isEqualTo(2);
  }

  @Test
  void repeatedGenerationFailureHasABoundedTenAttemptBudget() {
    GeneratedDocument document = result();
    for (int attempt = 0; attempt < 10; attempt++) {
      document.beginGeneration(NOW.plusSeconds(attempt));
      document.markFailed(
          new IllegalStateException("Storage unavailable"), NOW.plusSeconds(attempt));
    }
    assertThat(document.canRetry()).isFalse();
    assertThat(document.getGenerationAttemptCount()).isEqualTo(10);
    assertThatThrownBy(() -> document.beginGeneration(NOW.plusSeconds(11)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exhausted");
    assertThatThrownBy(() -> document.requestRetry(0, NOW.plusSeconds(11)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("attempts remaining");
  }

  @ParameterizedTest
  @ValueSource(strings = {"OFFER_LETTER", "RESULT_SLIP"})
  void workerStoresImmutablePdfBytesChecksumAndObjectVersionBeforePublishingEvidence(String type)
      throws Exception {
    GeneratedDocument document = type.equals("OFFER_LETTER") ? offer() : result();
    byte[] bytes = (type + "-pdf-content").getBytes(StandardCharsets.UTF_8);
    when(documents.lockNextGenerationBatch(NOW)).thenReturn(List.of(document));
    if (type.equals("OFFER_LETTER"))
      when(offerRenderer.render(document, document.getOfferLetter()))
          .thenReturn(new RenderedPdf(bytes, 2));
    else
      when(resultRenderer.render(document, document.getProgressionDecision(), List.of()))
          .thenReturn(new RenderedPdf(bytes, 2));
    worker.generateQueuedDocuments();
    String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3).putObject(request.capture(), body.capture());
    assertThat(request.getValue().bucket()).isEqualTo("official-documents");
    assertThat(request.getValue().key()).isEqualTo(document.storageKey());
    assertThat(request.getValue().metadata())
        .containsEntry("sha256", checksum)
        .containsEntry("document-number", document.getDocumentNumber());
    assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(bytes);
    assertThat(document.getStatus()).isEqualTo(GeneratedDocument.Status.STORED);
    assertThat(document.getChecksumSha256()).isEqualTo(checksum);
    assertThat(document.getStorageObjectVersion()).isEqualTo("object-version-1");
    assertThat(document.getContentType()).isEqualTo("application/pdf");
    assertThat(document.getPageCount()).isEqualTo(2);
    assertThat(document.getGeneratedAt()).isEqualTo(NOW);
    verify(documents).saveAndFlush(document);
    if (type.equals("OFFER_LETTER")) {
      assertThat(document.storageKey()).isEqualTo("official-offers/APP-1/OFFER-OFFER-1-V2.pdf");
      verify(outbox).enqueueOfferLetterStored(document);
      verifyNoInteractions(resultRenderer);
    } else {
      assertThat(document.storageKey())
          .isEqualTo("official-results/R260001/2026-S1/RSLIP-PRG-1.pdf");
      verifyNoInteractions(outbox, offerRenderer);
    }
  }

  @Test
  void absentBucketIsCreatedBeforeTheDocumentIsStored() {
    GeneratedDocument document = offer();
    when(documents.lockNextGenerationBatch(NOW)).thenReturn(List.of(document));
    when(offerRenderer.render(document, document.getOfferLetter()))
        .thenReturn(new RenderedPdf(new byte[] {1, 2}, 1));
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(404).message("Missing bucket").build());
    worker.generateQueuedDocuments();
    verify(s3).createBucket(any(CreateBucketRequest.class));
    assertThat(document.getStatus()).isEqualTo(GeneratedDocument.Status.STORED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"render", "bucket", "upload"})
  void renderingOrStorageFailuresLeaveRetryableFailureAndDoNotPublishStoredEvidence(
      String failure) {
    GeneratedDocument document = offer();
    when(documents.lockNextGenerationBatch(NOW)).thenReturn(List.of(document));
    if (failure.equals("render"))
      when(offerRenderer.render(document, document.getOfferLetter()))
          .thenThrow(new IllegalStateException("Rendering failed"));
    else {
      when(offerRenderer.render(document, document.getOfferLetter()))
          .thenReturn(new RenderedPdf(new byte[] {1}, 1));
      if (failure.equals("bucket"))
        when(s3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(S3Exception.builder().statusCode(403).message("Access denied").build());
      else
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().statusCode(503).message("Storage offline").build());
    }
    worker.generateQueuedDocuments();
    assertThat(document.getStatus()).isEqualTo(GeneratedDocument.Status.FAILED);
    assertThat(document.canRetry()).isTrue();
    assertThat(document.getLastFailureReason()).isNotBlank();
    verifyNoInteractions(outbox);
    verify(documents, never()).saveAndFlush(any());
    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void failedDocumentDoesNotPreventLaterQueuedDocumentsFromBeingGenerated() {
    GeneratedDocument failed = offer();
    GeneratedDocument successful = offer();
    when(documents.lockNextGenerationBatch(NOW)).thenReturn(List.of(failed, successful));
    when(offerRenderer.render(failed, failed.getOfferLetter()))
        .thenThrow(new IllegalStateException("Bad source document"));
    when(offerRenderer.render(successful, successful.getOfferLetter()))
        .thenReturn(new RenderedPdf(new byte[] {1}, 1));
    worker.generateQueuedDocuments();
    assertThat(failed.getStatus()).isEqualTo(GeneratedDocument.Status.FAILED);
    assertThat(successful.getStatus()).isEqualTo(GeneratedDocument.Status.STORED);
    verify(outbox).enqueueOfferLetterStored(successful);
  }

  @Test
  void emptyGenerationBatchDoesNotContactStorageOrRenderers() {
    when(documents.lockNextGenerationBatch(NOW)).thenReturn(List.of());
    worker.generateQueuedDocuments();
    verifyNoInteractions(s3, offerRenderer, resultRenderer, outbox);
  }

  private GeneratedDocument offer() {
    OfferLetterContentSnapshot content =
        new ObjectMapper()
            .convertValue(
                Map.of("institutionName", "University of Zimbabwe"),
                OfferLetterContentSnapshot.class);
    OfferLetterRequestedEvent event =
        new OfferLetterRequestedEvent(
            UUID.randomUUID(),
            3,
            NOW,
            UUID.randomUUID(),
            0,
            2,
            "OFFER-1",
            UUID.randomUUID(),
            "APP-1",
            "A000001",
            "Tariro Moyo",
            "applicant@example.test",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BSC",
            "Science",
            UUID.randomUUID(),
            "FIRM",
            null,
            NOW.plusSeconds(3600),
            null,
            null,
            LocalDate.of(2026, 9, 1),
            content,
            UUID.randomUUID());
    return new GeneratedDocument(new OfferLetterProjection(event), NOW);
  }

  private GeneratedDocument result() {
    ProgressionDecisionPublishedEvent event =
        new ProgressionDecisionPublishedEvent(
            UUID.randomUUID(),
            1,
            NOW,
            UUID.randomUUID(),
            "PRG-1",
            1,
            null,
            UUID.randomUUID(),
            "BSC-P1",
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "R260001",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "2026-S1",
            1,
            "PROCEED",
            "Proceed",
            2,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            0,
            0,
            new BigDecimal("70"),
            UUID.randomUUID(),
            NOW,
            List.of(UUID.randomUUID()));
    ProgressionDecisionProjection projection = new ProgressionDecisionProjection(event);
    ReflectionTestUtils.setField(projection, "id", UUID.randomUUID());
    return new GeneratedDocument(projection, NOW);
  }
}

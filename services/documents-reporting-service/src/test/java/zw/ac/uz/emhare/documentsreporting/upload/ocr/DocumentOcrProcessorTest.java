package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.DocumentOcrExtractionRepository;

/** OCR processor ordering, success, and bounded retry coverage. @author Tinashe K */
class DocumentOcrProcessorTest {

  private final Instant now = Instant.parse("2026-08-23T10:00:00Z");
  private DocumentOcrExtractionRepository repository;
  private DoclingServeApi doclingServeApi;
  private S3Client s3Client;
  private OcrImagePreprocessor imagePreprocessor;
  private UploadedDocument document;
  private DocumentOcrExtraction extraction;

  @BeforeEach
  void setUp() {
    repository = mock(DocumentOcrExtractionRepository.class);
    doclingServeApi = mock(DoclingServeApi.class);
    s3Client = mock(S3Client.class);
    imagePreprocessor = mock(OcrImagePreprocessor.class);
    document = mock(UploadedDocument.class);
    when(document.getId()).thenReturn(UUID.randomUUID());
    when(document.getStorageBucket()).thenReturn("applicant-evidence");
    when(document.getStorageKey()).thenReturn("applications/evidence.pdf");
    when(document.getOriginalFileName()).thenReturn("evidence.pdf");
    when(document.getMimeType()).thenReturn("application/pdf");
    when(imagePreprocessor.prepare(any(byte[].class), eq("application/pdf"), eq("evidence.pdf")))
        .thenAnswer(
            invocation ->
                new OcrImagePreprocessor.PreparedOcrInput(
                    invocation.getArgument(0), "evidence.pdf", false));
    extraction =
        new DocumentOcrExtraction(document, "DOCLING_RAPIDOCR", "docling-serve-v1.29.0", now);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void doesNothingWhenDisabledOrNoJobIsQueued() {
    assertThat(processor(false).processNext()).isFalse();
    verify(repository, never())
        .findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
            any(), any());

    when(repository
            .findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
                DocumentOcrStatus.QUEUED, now))
        .thenReturn(Optional.empty());
    assertThat(processor(true).processNext()).isFalse();
  }

  @Test
  void convertsStoredEvidenceWithRapidOcrAndPersistsOnlyStructuredProposals() {
    queueExtraction();
    ResponseBytes<GetObjectResponse> bytes =
        ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            "redacted fixture".getBytes(StandardCharsets.UTF_8));
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytes);
    DocumentResponse convertedDocument =
        DocumentResponse.builder()
            .filename("evidence.pdf")
            .textContent("National ID: 12-345678A90\nGender: Female")
            .markdownContent("National ID: 12-345678A90")
            .build();
    when(doclingServeApi.convertSource(any(ConvertDocumentRequest.class)))
        .thenReturn(
            InBodyConvertDocumentResponse.builder()
                .document(convertedDocument)
                .status("success")
                .processingTime(0.5)
                .build());

    assertThat(processor(true).processNext()).isTrue();

    assertThat(extraction.getStatus()).isEqualTo(DocumentOcrStatus.COMPLETED);
    assertThat(extraction.getAttemptCount()).isEqualTo(1);
    assertThat(extraction.getProposedFactsJson()).contains("nationalIdNumber", "genderCode");
    assertThat(extraction.getStructuredExtractionJson())
        .contains("evidence.pdf", "processingStatus", "success");
    verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    verify(imagePreprocessor).prepare(any(byte[].class), eq("application/pdf"), eq("evidence.pdf"));
    verify(doclingServeApi).convertSource(any(ConvertDocumentRequest.class));
    verify(repository, times(2)).saveAndFlush(extraction);
  }

  @Test
  void retriesTransientFailuresThreeTimesThenStopsWithSafeFailureDetails() {
    when(repository
            .findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
                DocumentOcrStatus.QUEUED, now))
        .thenReturn(Optional.of(extraction));
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(new IllegalStateException("sensitive provider message"));

    assertThat(processor(true).processNext()).isTrue();
    assertThat(extraction.getStatus()).isEqualTo(DocumentOcrStatus.QUEUED);
    assertThat(extraction.getAttemptCount()).isEqualTo(1);
    assertThat(extraction.getLastFailureMessage()).doesNotContain("sensitive");

    extraction.markProcessing(now);
    extraction.fail("second", "safe", 3, java.time.Duration.ZERO, now);
    extraction.markProcessing(now);
    extraction.fail("third", "safe", 3, java.time.Duration.ZERO, now);
    assertThat(extraction.getStatus()).isEqualTo(DocumentOcrStatus.FAILED);
    assertThat(extraction.getAttemptCount()).isEqualTo(3);
  }

  @Test
  void extractsZimbabweNationalIdentityCardFieldsFromSpacedRegistrationNumber() {
    String recognisedText =
        """
        REPUBLIC OF ZIMBABWE NATIONAL REGISTRATION
        ID NUMBER
        47-203727 Y 47 CIT M
        SURNAME
        MUNYARADZI
        FIRST NAME
        TARIRO
        DATE OF BIRTH
        30/08/1997
        VILLAGE OF ORIGIN MUTIZE
        PLACE OF BIRTH
        MUTOKO
        DATE OF ISSUE
        22/08/2019
        """;

    ApplicantEvidenceFactExtractor.ExtractionFacts facts =
        new ApplicantEvidenceFactExtractor().extract(recognisedText);

    assertThat(facts.facts())
        .containsEntry("documentType", "ZIMBABWE_NATIONAL_ID")
        .containsEntry("nationalIdNumber", "47-203727Y47")
        .containsEntry("genderCode", "MALE")
        .containsEntry("nationality", "Zimbabwe")
        .containsEntry("firstName", "TARIRO")
        .containsEntry("lastName", "MUNYARADZI")
        .containsEntry("dateOfBirth", "30/08/1997")
        .containsEntry("placeOfBirth", "MUTOKO");
  }

  @Test
  void extractsAndValidatesIcaoTd3PassportMachineReadableZone() {
    String recognisedText =
        """
        REPUBLIC OF ZIMBABWE
        PASSPORT
        P<ZWEERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
        L898902C36ZWE7408122F1204159ZE184226B<<<<<10
        """;

    ApplicantEvidenceFactExtractor.ExtractionFacts facts =
        new ApplicantEvidenceFactExtractor().extract(recognisedText);

    assertThat(facts.facts())
        .containsEntry("documentType", "ICAO_TD3_PASSPORT")
        .containsEntry("passportNumber", "L898902C3")
        .containsEntry("lastName", "ERIKSSON")
        .containsEntry("firstName", "ANNA")
        .containsEntry("middleNames", "MARIA")
        .containsEntry("dateOfBirth", "12/08/1974")
        .containsEntry("genderCode", "FEMALE")
        .containsEntry("nationalityCode", "ZWE");
  }

  private void queueExtraction() {
    when(repository
            .findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
                DocumentOcrStatus.QUEUED, now))
        .thenReturn(Optional.of(extraction));
  }

  private DocumentOcrProcessor processor(boolean enabled) {
    return new DocumentOcrProcessor(
        repository,
        doclingServeApi,
        s3Client,
        imagePreprocessor,
        new ApplicantEvidenceFactExtractor(),
        new DocumentOcrProperties(enabled, null, null, null, 3, null, null),
        new ObjectMapper(),
        Clock.fixed(now, ZoneOffset.UTC));
  }
}

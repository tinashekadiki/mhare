package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OcrEngine;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.DocumentOcrExtractionRepository;

/** Executes one queued OCR job without logging extracted content. @author Tinashe K */
@Service
public class DocumentOcrProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentOcrProcessor.class);

  private final DocumentOcrExtractionRepository repository;
  private final DoclingServeApi doclingServeApi;
  private final S3Client s3Client;
  private final OcrImagePreprocessor imagePreprocessor;
  private final ApplicantEvidenceFactExtractor factExtractor;
  private final DocumentOcrProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public DocumentOcrProcessor(
      DocumentOcrExtractionRepository repository,
      DoclingServeApi doclingServeApi,
      S3Client s3Client,
      OcrImagePreprocessor imagePreprocessor,
      ApplicantEvidenceFactExtractor factExtractor,
      DocumentOcrProperties properties,
      ObjectMapper objectMapper,
      Clock clock) {
    this.repository = repository;
    this.doclingServeApi = doclingServeApi;
    this.s3Client = s3Client;
    this.imagePreprocessor = imagePreprocessor;
    this.factExtractor = factExtractor;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public boolean processNext() {
    if (!properties.enabled()) return false;
    Optional<DocumentOcrExtraction> candidate =
        repository
            .findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
                DocumentOcrStatus.QUEUED, clock.instant());
    if (candidate.isEmpty()) return false;
    DocumentOcrExtraction extraction = candidate.get();
    extraction.markProcessing(clock.instant());
    repository.saveAndFlush(extraction);
    try {
      ExtractionPayload payload = convert(extraction.getUploadedDocument());
      ApplicantEvidenceFactExtractor.ExtractionFacts facts =
          factExtractor.extract(
              payload.text(),
              payload.structuredExtraction(),
              extraction.getUploadedDocument().getDocumentTypeCode());
      extraction.complete(
          objectMapper.writeValueAsString(payload.structuredExtraction()),
          objectMapper.writeValueAsString(facts.facts()),
          objectMapper.writeValueAsString(facts.confidence()),
          objectMapper.writeValueAsString(facts.warnings()),
          clock.instant());
    } catch (RuntimeException exception) {
      extraction.fail(
          exception.getClass().getSimpleName(),
          "The local OCR service could not process this document.",
          properties.maximumAttempts(),
          properties.retryDelay(),
          clock.instant());
      LOGGER.warn(
          "OCR attempt {} failed for document {} ({})",
          extraction.getAttemptCount(),
          extraction.getUploadedDocument().getId(),
          exception.getClass().getSimpleName());
    } catch (Exception exception) {
      extraction.fail(
          "OCR_SERIALIZATION_FAILURE",
          "OCR output could not be stored safely.",
          properties.maximumAttempts(),
          properties.retryDelay(),
          clock.instant());
      LOGGER.warn(
          "OCR output storage failed for document {} ({})",
          extraction.getUploadedDocument().getId(),
          exception.getClass().getSimpleName());
    }
    repository.saveAndFlush(extraction);
    return true;
  }

  private ExtractionPayload convert(UploadedDocument document) {
    byte[] content =
        s3Client
            .getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(document.getStorageBucket())
                    .key(document.getStorageKey())
                    .build())
            .asByteArray();
    OcrImagePreprocessor.PreparedOcrInput preparedInput =
        imagePreprocessor.prepare(content, document.getMimeType(), document.getOriginalFileName());
    InBodyConvertDocumentResponse response = convertPreparedInput(preparedInput);
    String text = responseText(response);
    Map<String, Object> structured = new LinkedHashMap<>();
    structured.put("filename", response.getDocument().getFilename());
    structured.put("document", serializableJsonContent(response));
    structured.put("text", text);
    structured.put("markdown", response.getDocument().getMarkdownContent());
    structured.put("processingStatus", response.getStatus());
    structured.put("processingTimeSeconds", response.getProcessingTime());
    structured.put("inputPreprocessed", preparedInput.preprocessed());
    if (isSchoolQualificationEvidence(document)) {
      imagePreprocessor
          .prepareQualificationRegion(
              content, document.getMimeType(), document.getOriginalFileName())
          .ifPresent(
              region -> appendQualificationRegion(structured, region, "qualificationRegion"));
      imagePreprocessor
          .prepareQualificationContrastRegion(
              content, document.getMimeType(), document.getOriginalFileName())
          .ifPresent(
              region ->
                  appendQualificationRegion(structured, region, "qualificationContrastRegion"));
    }
    return new ExtractionPayload(text, structured);
  }

  private InBodyConvertDocumentResponse convertPreparedInput(
      OcrImagePreprocessor.PreparedOcrInput preparedInput) {
    ConvertDocumentRequest request =
        ConvertDocumentRequest.builder()
            .source(
                FileSource.builder()
                    .filename(preparedInput.fileName())
                    .base64String(Base64.getEncoder().encodeToString(preparedInput.content()))
                    .build())
            .options(
                ConvertDocumentOptions.builder()
                    .toFormat(OutputFormat.TEXT)
                    .toFormat(OutputFormat.MARKDOWN)
                    .toFormat(OutputFormat.JSON)
                    .doOcr(true)
                    .ocrEngine(OcrEngine.RAPIDOCR)
                    .ocrLang("en")
                    .documentTimeout(properties.documentTimeout())
                    .includeImages(false)
                    .abortOnError(false)
                    .build())
            .target(InBodyTarget.builder().build())
            .build();
    return (InBodyConvertDocumentResponse) doclingServeApi.convertSource(request);
  }

  private String responseText(InBodyConvertDocumentResponse response) {
    String text = response.getDocument().getTextContent();
    if (text == null || text.isBlank()) text = response.getDocument().getMarkdownContent();
    if (text == null) text = "";
    return text;
  }

  private Map<String, Object> serializableJsonContent(InBodyConvertDocumentResponse response) {
    if (response.getDocument().getJsonContent() == null) return Map.of();
    return objectMapper.convertValue(
        response.getDocument().getJsonContent(),
        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  private boolean isSchoolQualificationEvidence(UploadedDocument document) {
    String documentTypeCode =
        document.getDocumentTypeCode() == null
            ? ""
            : document.getDocumentTypeCode().trim().toUpperCase(Locale.ROOT);
    return documentTypeCode.equals("O_LEVEL") || documentTypeCode.equals("A_LEVEL");
  }

  private void appendQualificationRegion(
      Map<String, Object> structured,
      OcrImagePreprocessor.PreparedOcrInput region,
      String fieldPrefix) {
    try {
      InBodyConvertDocumentResponse response = convertPreparedInput(region);
      structured.put(fieldPrefix + "Filename", response.getDocument().getFilename());
      structured.put(fieldPrefix + "Document", serializableJsonContent(response));
      structured.put(fieldPrefix + "Text", responseText(response));
      structured.put(fieldPrefix + "ProcessingStatus", response.getStatus());
      structured.put(fieldPrefix + "ProcessingTimeSeconds", response.getProcessingTime());
    } catch (RuntimeException exception) {
      structured.put(fieldPrefix + "ProcessingStatus", "failed");
      structured.put(fieldPrefix + "FailureCode", exception.getClass().getSimpleName());
    }
  }

  private record ExtractionPayload(String text, Map<String, Object> structuredExtraction) {}
}

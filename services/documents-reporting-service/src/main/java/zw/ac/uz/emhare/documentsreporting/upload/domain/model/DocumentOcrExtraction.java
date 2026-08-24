package zw.ac.uz.emhare.documentsreporting.upload.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrStatus;

/** Sensitive structured extraction evidence for one immutable upload. @author Tinashe K */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "document_ocr_extractions")
public class DocumentOcrExtraction extends AuditableEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_document_id", nullable = false)
  private UploadedDocument uploadedDocument;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentOcrStatus status;

  @Column(name = "engine_name", nullable = false, length = 80)
  private String engineName;

  @Column(name = "engine_version", nullable = false, length = 40)
  private String engineVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "structured_extraction_json", columnDefinition = "jsonb")
  private String structuredExtractionJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proposed_facts_json", columnDefinition = "jsonb")
  private String proposedFactsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "confidence_json", columnDefinition = "jsonb")
  private String confidenceJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "warnings_json", columnDefinition = "jsonb")
  private String warningsJson;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "queued_at", nullable = false)
  private Instant queuedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_failure_code", length = 80)
  private String lastFailureCode;

  @Column(name = "last_failure_message", length = 500)
  private String lastFailureMessage;

  protected DocumentOcrExtraction() {}

  public DocumentOcrExtraction(
      UploadedDocument uploadedDocument,
      String engineName,
      String engineVersion,
      Instant queuedAt) {
    this.uploadedDocument = uploadedDocument;
    this.engineName = engineName;
    this.engineVersion = engineVersion;
    this.status = DocumentOcrStatus.QUEUED;
    this.queuedAt = queuedAt;
    this.nextAttemptAt = queuedAt;
  }

  public void markProcessing(Instant now) {
    if (status != DocumentOcrStatus.QUEUED && status != DocumentOcrStatus.FAILED) {
      throw new IllegalStateException("Only queued or failed OCR can start processing.");
    }
    status = DocumentOcrStatus.PROCESSING;
    attemptCount++;
    startedAt = now;
    lastFailureCode = null;
    lastFailureMessage = null;
  }

  public void complete(
      String structuredExtractionJson,
      String proposedFactsJson,
      String confidenceJson,
      String warningsJson,
      Instant now) {
    status = DocumentOcrStatus.COMPLETED;
    this.structuredExtractionJson = structuredExtractionJson;
    this.proposedFactsJson = proposedFactsJson;
    this.confidenceJson = confidenceJson;
    this.warningsJson = warningsJson;
    completedAt = now;
  }

  public void fail(
      String failureCode,
      String safeMessage,
      int maximumAttempts,
      Duration retryDelay,
      Instant now) {
    lastFailureCode = truncate(failureCode, 80);
    lastFailureMessage = truncate(safeMessage, 500);
    completedAt = null;
    if (attemptCount < maximumAttempts) {
      status = DocumentOcrStatus.QUEUED;
      nextAttemptAt = now.plus(retryDelay.multipliedBy(attemptCount));
    } else {
      status = DocumentOcrStatus.FAILED;
      nextAttemptAt = now;
    }
  }

  public void retry(Instant now) {
    if (status != DocumentOcrStatus.FAILED) {
      throw new IllegalStateException("Only failed OCR extraction can be retried.");
    }
    status = DocumentOcrStatus.QUEUED;
    attemptCount = 0;
    nextAttemptAt = now;
    queuedAt = now;
    lastFailureCode = null;
    lastFailureMessage = null;
  }

  public void unsupported(String warningJson, Instant now) {
    status = DocumentOcrStatus.UNSUPPORTED;
    warningsJson = warningJson;
    completedAt = now;
  }

  private String truncate(String value, int maximumLength) {
    if (value == null) return null;
    String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
    return normalized.length() <= maximumLength
        ? normalized
        : normalized.substring(0, maximumLength);
  }

  public UploadedDocument getUploadedDocument() {
    return uploadedDocument;
  }

  public DocumentOcrStatus getStatus() {
    return status;
  }

  public String getEngineName() {
    return engineName;
  }

  public String getEngineVersion() {
    return engineVersion;
  }

  public String getStructuredExtractionJson() {
    return structuredExtractionJson;
  }

  public String getProposedFactsJson() {
    return proposedFactsJson;
  }

  public String getConfidenceJson() {
    return confidenceJson;
  }

  public String getWarningsJson() {
    return warningsJson;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getQueuedAt() {
    return queuedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getLastFailureCode() {
    return lastFailureCode;
  }

  public String getLastFailureMessage() {
    return lastFailureMessage;
  }
}

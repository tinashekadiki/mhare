package zw.ac.uz.emhare.documentsreporting.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.documentsreporting.projection.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjection;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "generated_documents")
@SQLRestriction("deleted_at IS NULL")
public class GeneratedDocument extends AuditableEntity {

    private static final int MAXIMUM_GENERATION_ATTEMPTS = 10;

    public enum DocumentType { RESULT_SLIP, OFFER_LETTER }
    public enum Status { REQUESTED, GENERATING, STORED, FAILED }

    @Column(name = "document_number", nullable = false, length = 100) private String documentNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40) private DocumentType documentType;
    @Column(name = "student_id") private UUID studentId;
    @Column(name = "student_number", length = 40) private String studentNumber;
    @Column(name = "programme_id", nullable = false) private UUID programmeId;
    @Column(name = "programme_version_id") private UUID programmeVersionId;
    @Column(name = "academic_period_id") private UUID academicPeriodId;
    @Column(name = "academic_period_code", length = 50) private String academicPeriodCode;
    @Column(name = "source_progression_decision_id") private UUID sourceProgressionDecisionId;
    @Column(name = "source_progression_decision_version", nullable = false) private int sourceProgressionDecisionVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "progression_decision_projection_id")
    private ProgressionDecisionProjection progressionDecision;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_letter_projection_id")
    private OfferLetterProjection offerLetter;
    @Column(name = "template_code", nullable = false, length = 80) private String templateCode;
    @Column(name = "template_version", nullable = false) private int templateVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private Status status;
    @Column(name = "storage_bucket", length = 100) private String storageBucket;
    @Column(name = "storage_key", length = 500) private String storageKey;
    @Column(name = "storage_object_version", length = 200) private String storageObjectVersion;
    @Column(name = "content_type", length = 100) private String contentType;
    @Column(name = "checksum_sha256", length = 64) private String checksumSha256;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(name = "page_count") private Integer pageCount;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "generation_started_at") private Instant generationStartedAt;
    @Column(name = "generated_at") private Instant generatedAt;
    @Column(name = "generation_attempt_count", nullable = false) private int generationAttemptCount;
    @Column(name = "next_generation_attempt_at", nullable = false) private Instant nextGenerationAttemptAt;
    @Column(name = "last_failure_reason", length = 1000) private String lastFailureReason;

    protected GeneratedDocument() {
    }

    public GeneratedDocument(ProgressionDecisionProjection progressionDecision, Instant requestedAt) {
        this.documentNumber = "RSLIP-" + progressionDecision.getDecisionNumber();
        this.documentType = DocumentType.RESULT_SLIP;
        this.studentId = progressionDecision.getStudentId();
        this.studentNumber = progressionDecision.getStudentNumber();
        this.programmeId = progressionDecision.getProgrammeId();
        this.programmeVersionId = progressionDecision.getProgrammeVersionId();
        this.academicPeriodId = progressionDecision.getAcademicPeriodId();
        this.academicPeriodCode = progressionDecision.getAcademicPeriodCode();
        this.sourceProgressionDecisionId = progressionDecision.getSourceProgressionDecisionId();
        this.sourceProgressionDecisionVersion = progressionDecision.getDecisionVersion();
        this.progressionDecision = progressionDecision;
        this.templateCode = "OFFICIAL-RESULT-SLIP";
        this.templateVersion = 1;
        this.status = Status.REQUESTED;
        this.requestedAt = requestedAt;
        this.nextGenerationAttemptAt = requestedAt;
    }

    public GeneratedDocument(OfferLetterProjection offerLetter, Instant requestedAt) {
        this.documentNumber = "OFFER-" + offerLetter.getOfferNumber();
        this.documentType = DocumentType.OFFER_LETTER;
        this.programmeId = offerLetter.getProgrammeId();
        this.sourceProgressionDecisionVersion = Math.toIntExact(Math.max(1L, offerLetter.getOfferVersion()));
        this.offerLetter = offerLetter;
        this.templateCode = "OFFICIAL-OFFER-LETTER";
        this.templateVersion = 1;
        this.status = Status.REQUESTED;
        this.requestedAt = requestedAt;
        this.nextGenerationAttemptAt = requestedAt;
    }

    public void beginGeneration(Instant now) {
        if (status != Status.REQUESTED && status != Status.FAILED) {
            throw new IllegalStateException("Only queued or failed documents can be generated.");
        }
        if (generationAttemptCount >= MAXIMUM_GENERATION_ATTEMPTS) {
            throw new IllegalStateException("Official document exhausted its generation attempts.");
        }
        status = Status.GENERATING;
        generationStartedAt = now;
        generationAttemptCount++;
        lastFailureReason = null;
    }

    public void markStored(
            String bucket,
            String objectKey,
            String objectVersion,
            String checksum,
            long byteCount,
            int pages,
            Instant now) {
        if (status != Status.GENERATING) {
            throw new IllegalStateException("Only a generating document can be stored.");
        }
        status = Status.STORED;
        storageBucket = bucket;
        storageKey = objectKey;
        storageObjectVersion = objectVersion;
        contentType = "application/pdf";
        checksumSha256 = checksum;
        sizeBytes = byteCount;
        pageCount = pages;
        generatedAt = now;
        nextGenerationAttemptAt = now;
    }

    public void markFailed(RuntimeException exception, Instant now) {
        if (status != Status.GENERATING) {
            throw new IllegalStateException("Only a generating document can fail.");
        }
        status = Status.FAILED;
        String message = exception.getMessage();
        lastFailureReason = message == null ? exception.getClass().getSimpleName() : message;
        if (lastFailureReason.length() > 1000) {
            lastFailureReason = lastFailureReason.substring(0, 1000);
        }
        long delaySeconds = Math.min(900L, 1L << Math.min(generationAttemptCount + 1, 9));
        nextGenerationAttemptAt = now.plus(delaySeconds, ChronoUnit.SECONDS);
    }

    public boolean canRetry() { return generationAttemptCount < MAXIMUM_GENERATION_ATTEMPTS; }
    public void requestRetry(long expectedVersion, Instant now) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Official document changed. Refresh before retrying.");
        }
        if (status != Status.FAILED || !canRetry()) {
            throw new IllegalStateException("Only a failed document with attempts remaining can be retried.");
        }
        status = Status.REQUESTED;
        nextGenerationAttemptAt = now;
        lastFailureReason = null;
    }
    public String storageKey() {
        if (documentType == DocumentType.OFFER_LETTER) {
            return "official-offers/" + offerLetter.getApplicationNumber() + "/" + documentNumber + ".pdf";
        }
        return "official-results/" + studentNumber + "/" + academicPeriodCode + "/" + documentNumber + ".pdf";
    }

    public String getDocumentNumber() { return documentNumber; }
    public DocumentType getDocumentType() { return documentType; }
    public UUID getStudentId() { return studentId; }
    public String getStudentNumber() { return studentNumber; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public ProgressionDecisionProjection getProgressionDecision() { return progressionDecision; }
    public OfferLetterProjection getOfferLetter() { return offerLetter; }
    public String getTemplateCode() { return templateCode; }
    public int getTemplateVersion() { return templateVersion; }
    public Status getStatus() { return status; }
    public String getStorageBucket() { return storageBucket; }
    public String getStorageKey() { return storageKey; }
    public String getStorageObjectVersion() { return storageObjectVersion; }
    public String getContentType() { return contentType; }
    public String getChecksumSha256() { return checksumSha256; }
    public Long getSizeBytes() { return sizeBytes; }
    public Integer getPageCount() { return pageCount; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getGeneratedAt() { return generatedAt; }
    public int getGenerationAttemptCount() { return generationAttemptCount; }
    public String getLastFailureReason() { return lastFailureReason; }
}

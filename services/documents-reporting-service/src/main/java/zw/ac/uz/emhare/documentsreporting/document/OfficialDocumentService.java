package zw.ac.uz.emhare.documentsreporting.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import zw.ac.uz.emhare.documentsreporting.document.DocumentViews.DocumentDownload;
import zw.ac.uz.emhare.documentsreporting.document.DocumentViews.DocumentSummary;

/** @author Tinashe K */
@Service
public class OfficialDocumentService {

    private final GeneratedDocumentRepository documentRepository;
    private final S3Presigner s3Presigner;
    private final DocumentsStorageProperties storageProperties;
    private final Clock clock;

    public OfficialDocumentService(
            GeneratedDocumentRepository documentRepository,
            S3Presigner s3Presigner,
            DocumentsStorageProperties storageProperties,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> documents() {
        return documentRepository.findAllByDeletedAtIsNullOrderByRequestedAtDesc().stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID documentId) {
        GeneratedDocument document = requireDocument(documentId);
        if (document.getStatus() != GeneratedDocument.Status.STORED) {
            throw new IllegalStateException("Official document is not yet stored.");
        }
        long validitySeconds = Math.max(60, Math.min(storageProperties.downloadUrlValiditySeconds(), 3600));
        Instant expiresAt = clock.instant().plusSeconds(validitySeconds);
        var request = GetObjectRequest.builder()
                .bucket(document.getStorageBucket())
                .key(document.getStorageKey())
                .responseContentType("application/pdf")
                .responseContentDisposition("inline; filename=\"" + document.getDocumentNumber() + ".pdf\"")
                .build();
        String downloadUrl = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(validitySeconds))
                        .getObjectRequest(request)
                        .build())
                .url()
                .toString();
        return new DocumentDownload(
                document.getId(),
                document.getDocumentNumber(),
                document.getContentType(),
                document.getChecksumSha256(),
                downloadUrl,
                expiresAt);
    }

    @Transactional
    public DocumentSummary retry(UUID documentId, long expectedVersion) {
        GeneratedDocument document = requireDocument(documentId);
        document.requestRetry(expectedVersion, clock.instant());
        return summary(documentRepository.saveAndFlush(document));
    }

    private GeneratedDocument requireDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Official document was not found."));
    }

    private DocumentSummary summary(GeneratedDocument document) {
        var decision = document.getProgressionDecision();
        var offerLetter = document.getOfferLetter();
        String subjectNumber = decision == null ? offerLetter.getApplicantNumber() : document.getStudentNumber();
        String decisionCode = decision == null ? offerLetter.getOfferType() : decision.getDecisionCode();
        String decisionLabel = decision == null ? offerLetter.getProgrammeName() : decision.getDecisionLabel();
        return new DocumentSummary(
                document.getId(),
                document.getDocumentNumber(),
                document.getDocumentType(),
                subjectNumber,
                document.getAcademicPeriodCode(),
                decisionCode,
                decisionLabel,
                document.getStatus(),
                document.getTemplateCode(),
                document.getTemplateVersion(),
                document.getChecksumSha256(),
                document.getSizeBytes(),
                document.getPageCount(),
                document.getRequestedAt(),
                document.getGeneratedAt(),
                document.getGenerationAttemptCount(),
                document.getStatus() == GeneratedDocument.Status.FAILED && document.canRetry(),
                document.getLastFailureReason(),
                document.getVersion());
    }
}

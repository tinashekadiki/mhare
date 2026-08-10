package zw.ac.uz.emhare.documentsreporting.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import zw.ac.uz.emhare.documentsreporting.projection.ProgressionDecisionResultProjectionRepository;

/** @author Tinashe K */
@Component
public class OfficialDocumentGenerationWorker {

    private final GeneratedDocumentRepository documentRepository;
    private final ProgressionDecisionResultProjectionRepository decisionResultRepository;
    private final OfficialResultSlipPdfRenderer pdfRenderer;
    private final OfficialOfferLetterPdfRenderer offerLetterPdfRenderer;
    private final zw.ac.uz.emhare.documentsreporting.integration.DocumentVerificationOutboxService outboxService;
    private final S3Client s3Client;
    private final DocumentsStorageProperties storageProperties;
    private final Clock clock;

    public OfficialDocumentGenerationWorker(
            GeneratedDocumentRepository documentRepository,
            ProgressionDecisionResultProjectionRepository decisionResultRepository,
            OfficialResultSlipPdfRenderer pdfRenderer,
            OfficialOfferLetterPdfRenderer offerLetterPdfRenderer,
            zw.ac.uz.emhare.documentsreporting.integration.DocumentVerificationOutboxService outboxService,
            S3Client s3Client,
            DocumentsStorageProperties storageProperties,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.decisionResultRepository = decisionResultRepository;
        this.pdfRenderer = pdfRenderer;
        this.offerLetterPdfRenderer = offerLetterPdfRenderer;
        this.outboxService = outboxService;
        this.s3Client = s3Client;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${emhare.documents.generation-interval-ms:1000}")
    @Transactional
    public void generateQueuedDocuments() {
        Instant now = clock.instant();
        List<GeneratedDocument> documents = documentRepository.lockNextGenerationBatch(now);
        for (GeneratedDocument document : documents) {
            try {
                document.beginGeneration(clock.instant());
                var renderedPdf = document.getDocumentType() == GeneratedDocument.DocumentType.OFFER_LETTER
                        ? offerLetterPdfRenderer.render(document, document.getOfferLetter())
                        : pdfRenderer.render(document, document.getProgressionDecision(), decisionResultRepository
                                .findAllByProgressionDecisionIdAndDeletedAtIsNullOrderByPublishedResultModuleCodeAsc(
                                        document.getProgressionDecision().getId()).stream()
                                .map(item -> item.getPublishedResult()).toList());
                String checksum = sha256(renderedPdf.content());
                ensureBucketExists();
                String storageKey = document.storageKey();
                var response = s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(storageProperties.bucket())
                                .key(storageKey)
                                .contentType("application/pdf")
                                .contentLength((long) renderedPdf.content().length)
                                .metadata(java.util.Map.of(
                                        "sha256", checksum,
                                        "document-number", document.getDocumentNumber()))
                                .build(),
                        RequestBody.fromBytes(renderedPdf.content()));
                document.markStored(
                        storageProperties.bucket(),
                        storageKey,
                        response.versionId(),
                        checksum,
                        renderedPdf.content().length,
                        renderedPdf.pageCount(),
                        clock.instant());
                documentRepository.saveAndFlush(document);
                if (document.getDocumentType() == GeneratedDocument.DocumentType.OFFER_LETTER) {
                    outboxService.enqueueOfferLetterStored(document);
                }
            } catch (RuntimeException exception) {
                document.markFailed(exception, clock.instant());
            }
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(storageProperties.bucket()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            s3Client.createBucket(CreateBucketRequest.builder().bucket(storageProperties.bucket()).build());
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for official document verification.", exception);
        }
    }
}

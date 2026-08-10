package zw.ac.uz.emhare.documentsreporting.upload;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.documentsreporting.document.DocumentsStorageProperties;
import zw.ac.uz.emhare.documentsreporting.integration.DocumentVerificationOutboxService;
import zw.ac.uz.emhare.documentsreporting.upload.DocumentContentInspector.InspectedContent;
import zw.ac.uz.emhare.documentsreporting.upload.UploadedDocumentViews.UploadedDocumentDownload;
import zw.ac.uz.emhare.documentsreporting.upload.UploadedDocumentViews.UploadedDocumentSummary;

/** @author Tinashe K */
@Service
public class UploadedDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadedDocumentService.class);

    private static final Set<String> DOCUMENT_ADMINISTRATOR_ROLES = Set.of(
            "system-admin", "academic-admin", "admissions-officer", "registry-officer", "finance-officer");

    private final UploadedDocumentRepository repository;
    private final DocumentContentInspector contentInspector;
    private final DocumentVerificationOutboxService verificationOutboxService;
    private final EmhareCurrentUserResolver currentUserResolver;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final DocumentsStorageProperties storageProperties;
    private final Clock clock;

    public UploadedDocumentService(
            UploadedDocumentRepository repository,
            DocumentContentInspector contentInspector,
            DocumentVerificationOutboxService verificationOutboxService,
            EmhareCurrentUserResolver currentUserResolver,
            S3Client s3Client,
            S3Presigner s3Presigner,
            DocumentsStorageProperties storageProperties,
            Clock clock) {
        this.repository = repository;
        this.contentInspector = contentInspector;
        this.verificationOutboxService = verificationOutboxService;
        this.currentUserResolver = currentUserResolver;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    @Transactional
    public UploadedDocumentSummary upload(
            String ownerTypeValue,
            UUID ownerId,
            String documentTypeCode,
            UUID replacesDocumentId,
            MultipartFile file) {
        EmhareCurrentUser user = currentUserResolver.requireCurrentUser();
        UUID actorUserId = requireActorUserId(user);
        UploadedDocument.OwnerType ownerType = parseOwnerType(ownerTypeValue);
        String normalizedDocumentType = normalizeCode(documentTypeCode);
        String originalFileName = normalizeFileName(file.getOriginalFilename());
        byte[] content = readAndValidateSize(file);
        InspectedContent inspectedContent = contentInspector.inspect(content);
        UploadedDocument replacedDocument = validateReplacement(
                replacesDocumentId, ownerType, ownerId, normalizedDocumentType, user);
        String checksum = sha256(content);
        String storageKey = "uploads/" + ownerType.name().toLowerCase(Locale.ROOT) + "/" + ownerId
                + "/" + UUID.randomUUID() + "." + inspectedContent.extension();
        ensureBucketExists();
        var putResponse = s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(storageProperties.bucket())
                        .key(storageKey)
                        .contentType(inspectedContent.mimeType())
                        .contentLength((long) content.length)
                        .metadata(java.util.Map.of(
                                "sha256", checksum,
                                "owner-type", ownerType.name(),
                                "owner-id", ownerId.toString(),
                                "document-type", normalizedDocumentType))
                        .build(),
                RequestBody.fromBytes(content));
        deleteObjectIfTransactionRollsBack(storageKey);
        Instant uploadedAt = clock.instant();
        UploadedDocument document = new UploadedDocument(
                ownerType,
                ownerId,
                normalizedDocumentType,
                originalFileName,
                storageProperties.bucket(),
                storageKey,
                putResponse.versionId(),
                inspectedContent.mimeType(),
                content.length,
                checksum,
                actorUserId,
                uploadedAt,
                replacedDocument == null ? null : replacedDocument.getId());
        return summary(repository.saveAndFlush(document));
    }

    @Transactional(readOnly = true)
    public List<UploadedDocumentSummary> documents(String ownerTypeValue, UUID ownerId) {
        EmhareCurrentUser user = currentUserResolver.requireCurrentUser();
        UUID actorUserId = requireActorUserId(user);
        boolean administrator = isDocumentAdministrator(user);
        List<UploadedDocument> documents;
        if (ownerTypeValue != null || ownerId != null) {
            if (ownerTypeValue == null || ownerId == null) {
                throw new IllegalArgumentException("Owner type and owner ID must be supplied together.");
            }
            UploadedDocument.OwnerType ownerType = parseOwnerType(ownerTypeValue);
            documents = administrator
                    ? repository.findAllByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(ownerType, ownerId)
                    : repository.findAllByOwnerTypeAndOwnerIdAndUploadedByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(
                            ownerType, ownerId, actorUserId);
        } else {
            documents = administrator
                    ? repository.findAllByDeletedAtIsNullOrderByUploadedAtDesc()
                    : repository.findAllByUploadedByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(actorUserId);
        }
        return documents.stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public UploadedDocumentSummary document(UUID documentId) {
        EmhareCurrentUser user = currentUserResolver.requireCurrentUser();
        UploadedDocument document = requireDocument(documentId);
        requireReadable(document, user);
        return summary(document);
    }

    @Transactional(readOnly = true)
    public UploadedDocumentDownload download(UUID documentId, String dispositionValue) {
        EmhareCurrentUser user = currentUserResolver.requireCurrentUser();
        UploadedDocument document = requireDocument(documentId);
        requireReadable(document, user);
        String disposition = normalizeDownloadDisposition(dispositionValue);
        long validitySeconds = Math.max(60, Math.min(storageProperties.downloadUrlValiditySeconds(), 3600));
        Instant expiresAt = clock.instant().plusSeconds(validitySeconds);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(document.getStorageBucket())
                .key(document.getStorageKey())
                .responseContentType(document.getMimeType())
                .responseContentDisposition(disposition + "; filename=\"" + document.getOriginalFileName() + "\"")
                .build();
        String downloadUrl = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(validitySeconds))
                        .getObjectRequest(request)
                        .build())
                .url().toString();
        return new UploadedDocumentDownload(
                document.getId(),
                document.getOriginalFileName(),
                document.getMimeType(),
                document.getChecksumSha256(),
                downloadUrl,
                expiresAt);
    }

    private String normalizeDownloadDisposition(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("attachment")) return "attachment";
        if (value.equalsIgnoreCase("inline")) return "inline";
        throw new IllegalArgumentException("Document disposition must be attachment or inline.");
    }

    @Transactional
    public UploadedDocumentSummary verify(UUID documentId, long expectedVersion, String comment) {
        EmhareCurrentUser user = currentUserResolver.requireCurrentUser();
        requireDocumentAdministrator(user);
        UploadedDocument document = requireDocument(documentId);
        document.verify(requireActorUserId(user), comment, expectedVersion, clock.instant());
        UploadedDocument saved = repository.saveAndFlush(document);
        verificationOutboxService.enqueue(saved);
        return summary(saved);
    }

    @Transactional
    public UploadedDocumentSummary reject(UUID documentId, long expectedVersion, String reason) {
        EmhareCurrentUser user = currentUserResolver.requireCurrentUser();
        requireDocumentAdministrator(user);
        UploadedDocument document = requireDocument(documentId);
        document.reject(requireActorUserId(user), reason, expectedVersion, clock.instant());
        UploadedDocument saved = repository.saveAndFlush(document);
        verificationOutboxService.enqueue(saved);
        return summary(saved);
    }

    private UploadedDocument validateReplacement(
            UUID replacedDocumentId,
            UploadedDocument.OwnerType ownerType,
            UUID ownerId,
            String documentTypeCode,
            EmhareCurrentUser user) {
        if (replacedDocumentId == null) return null;
        UploadedDocument replaced = requireDocument(replacedDocumentId);
        requireReadable(replaced, user);
        if (replaced.getVerificationStatus() != UploadedDocument.VerificationStatus.REJECTED) {
            throw new IllegalStateException("Only a rejected document can be replaced.");
        }
        if (replaced.getOwnerType() != ownerType
                || !replaced.getOwnerId().equals(ownerId)
                || !replaced.getDocumentTypeCode().equals(documentTypeCode)) {
            throw new IllegalArgumentException("Replacement document ownership and type must match the rejected document.");
        }
        return replaced;
    }

    private byte[] readAndValidateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Document file is required.");
        if (file.getSize() > storageProperties.maximumUploadBytes()) {
            throw new IllegalArgumentException("Document exceeds the configured maximum upload size.");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) throw new IllegalArgumentException("Document file is empty.");
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("Document content could not be read.", exception);
        }
    }

    private String normalizeFileName(String value) {
        String candidate = value == null ? "document" : value.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}\"]", "_")
                .trim();
        if (candidate.isBlank()) candidate = "document";
        return candidate.length() <= 255 ? candidate : candidate.substring(candidate.length() - 255);
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Document type code is required.");
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        if (normalized.length() > 80) throw new IllegalArgumentException("Document type code is too long.");
        return normalized;
    }

    private UploadedDocument.OwnerType parseOwnerType(String value) {
        try {
            return UploadedDocument.OwnerType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported document owner type.", exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for document verification.", exception);
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(storageProperties.bucket()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw exception;
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(storageProperties.bucket()).build());
            } catch (S3Exception createException) {
                if (createException.statusCode() != 409) throw createException;
            }
        }
    }

    private void deleteObjectIfTransactionRollsBack(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        s3Client.deleteObject(DeleteObjectRequest.builder()
                                .bucket(storageProperties.bucket())
                                .key(storageKey)
                                .build());
                    } catch (RuntimeException cleanupException) {
                        LOGGER.error(
                                "Uploaded document object {} could not be removed after transaction rollback.",
                                storageKey,
                                cleanupException);
                    }
                }
            }
        });
    }

    private UploadedDocument requireDocument(UUID documentId) {
        return repository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Uploaded document was not found."));
    }

    private void requireReadable(UploadedDocument document, EmhareCurrentUser user) {
        UUID actorUserId = requireActorUserId(user);
        if (!document.getUploadedByUserId().equals(actorUserId) && !isDocumentAdministrator(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Document access is not permitted.");
        }
    }

    private void requireDocumentAdministrator(EmhareCurrentUser user) {
        if (!isDocumentAdministrator(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Document verification is not permitted.");
        }
    }

    private boolean isDocumentAdministrator(EmhareCurrentUser user) {
        return user.realmRoles().stream().anyMatch(DOCUMENT_ADMINISTRATOR_ROLES::contains);
    }

    private UUID requireActorUserId(EmhareCurrentUser user) {
        UUID actorUserId = user.auditUserId();
        if (actorUserId == null) throw new IllegalStateException("Authenticated user has no stable identifier.");
        return actorUserId;
    }

    private UploadedDocumentSummary summary(UploadedDocument document) {
        return new UploadedDocumentSummary(
                document.getId(), document.getOwnerType(), document.getOwnerId(), document.getDocumentTypeCode(),
                document.getOriginalFileName(), document.getMimeType(), document.getFileSizeBytes(),
                document.getChecksumSha256(), document.getUploadedByUserId(), document.getUploadedAt(),
                document.getVerificationStatus(), document.getVerifiedByUserId(), document.getVerifiedAt(),
                document.getVerificationComment(), document.getRejectionReason(), document.getReplacesDocumentId(),
                document.getVersion());
    }
}

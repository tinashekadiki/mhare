package zw.ac.uz.emhare.documentsreporting.upload.domain.model;

import zw.ac.uz.emhare.documentsreporting.upload.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "uploaded_documents")
public class UploadedDocument extends AuditableEntity {

    public enum OwnerType {
        APPLICANT, APPLICATION, STUDENT, STAFF, FINANCE_RECORD, ACADEMIC_WORKFLOW, INSTITUTION
    }

    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 40)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "document_type_code", nullable = false, length = 80)
    private String documentTypeCode;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "storage_object_version", length = 200)
    private String storageObjectVersion;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_comment", length = 1000)
    private String verificationComment;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "replaces_document_id")
    private UUID replacesDocumentId;

    protected UploadedDocument() {
    }

    public UploadedDocument(
            OwnerType ownerType,
            UUID ownerId,
            String documentTypeCode,
            String originalFileName,
            String storageBucket,
            String storageKey,
            String storageObjectVersion,
            String mimeType,
            long fileSizeBytes,
            String checksumSha256,
            UUID uploadedByUserId,
            Instant uploadedAt,
            UUID replacesDocumentId) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.documentTypeCode = documentTypeCode;
        this.originalFileName = originalFileName;
        this.storageBucket = storageBucket;
        this.storageKey = storageKey;
        this.storageObjectVersion = storageObjectVersion;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.checksumSha256 = checksumSha256;
        this.uploadedByUserId = uploadedByUserId;
        this.uploadedAt = uploadedAt;
        this.verificationStatus = VerificationStatus.PENDING;
        this.replacesDocumentId = replacesDocumentId;
    }

    public void verify(UUID actorUserId, String comment, long expectedVersion, Instant decidedAt) {
        requirePending(expectedVersion);
        verificationStatus = VerificationStatus.VERIFIED;
        verifiedByUserId = actorUserId;
        verifiedAt = decidedAt;
        verificationComment = normalizeOptional(comment);
        rejectionReason = null;
    }

    public void reject(UUID actorUserId, String reason, long expectedVersion, Instant decidedAt) {
        requirePending(expectedVersion);
        String normalizedReason = requireText(reason, "Rejection reason");
        if (normalizedReason.length() < 10) {
            throw new IllegalArgumentException("Rejection reason must contain at least 10 characters.");
        }
        verificationStatus = VerificationStatus.REJECTED;
        verifiedByUserId = actorUserId;
        verifiedAt = decidedAt;
        verificationComment = null;
        rejectionReason = normalizedReason;
    }

    private void requirePending(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("The document changed. Refresh the verification queue and retry.");
        }
        if (verificationStatus != VerificationStatus.PENDING) {
            throw new IllegalStateException("Only pending documents can receive a verification decision.");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required.");
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public OwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getDocumentTypeCode() { return documentTypeCode; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStorageBucket() { return storageBucket; }
    public String getStorageKey() { return storageKey; }
    public String getStorageObjectVersion() { return storageObjectVersion; }
    public String getMimeType() { return mimeType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public Instant getUploadedAt() { return uploadedAt; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public UUID getVerifiedByUserId() { return verifiedByUserId; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getVerificationComment() { return verificationComment; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReplacesDocumentId() { return replacesDocumentId; }
}

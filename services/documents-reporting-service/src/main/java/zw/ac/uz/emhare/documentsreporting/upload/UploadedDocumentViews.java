package zw.ac.uz.emhare.documentsreporting.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public final class UploadedDocumentViews {
    private UploadedDocumentViews() {
    }

    public record UploadedDocumentSummary(
            UUID id,
            UploadedDocument.OwnerType ownerType,
            UUID ownerId,
            String documentTypeCode,
            String originalFileName,
            String mimeType,
            long fileSizeBytes,
            String checksumSha256,
            UUID uploadedByUserId,
            Instant uploadedAt,
            UploadedDocument.VerificationStatus verificationStatus,
            UUID verifiedByUserId,
            Instant verifiedAt,
            String verificationComment,
            String rejectionReason,
            UUID replacesDocumentId,
            long version) {
    }

    public record UploadedDocumentDownload(
            UUID documentId,
            String originalFileName,
            String mimeType,
            String checksumSha256,
            String downloadUrl,
            Instant expiresAt) {
    }

    public record VerifyUploadedDocument(
            @NotNull Long expectedVersion,
            @Size(max = 1000) String comment) {
    }

    public record RejectUploadedDocument(
            @NotNull Long expectedVersion,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }
}

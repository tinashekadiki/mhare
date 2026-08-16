package zw.ac.uz.emhare.documentsreporting.document;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.UploadedDocumentRepository;

/** Loads a configured institution registrar signature from governed object storage. @author Tinashe K */
@Component
public class StoredOfferLetterSignatureLoader implements OfferLetterSignatureLoader {
    private static final String REGISTRAR_SIGNATURE_DOCUMENT_TYPE = "INSTITUTION_REGISTRAR_SIGNATURE";
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/png", "image/jpeg");

    private final UploadedDocumentRepository documentRepository;
    private final S3Client s3Client;

    public StoredOfferLetterSignatureLoader(UploadedDocumentRepository documentRepository, S3Client s3Client) {
        this.documentRepository = documentRepository;
        this.s3Client = s3Client;
    }

    @Override
    public InputStream load(String documentId) {
        if (documentId == null || documentId.isBlank()) return null;
        UUID signatureDocumentId;
        try {
            signatureDocumentId = UUID.fromString(documentId.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("The configured registrar signature document ID is invalid.", exception);
        }
        UploadedDocument signature = documentRepository.findByIdAndDeletedAtIsNull(signatureDocumentId)
                .orElseThrow(() -> new IllegalStateException("The configured registrar signature document is unavailable."));
        requireRegistrarSignature(signature);
        GetObjectRequest.Builder request = GetObjectRequest.builder()
                .bucket(signature.getStorageBucket())
                .key(signature.getStorageKey());
        if (signature.getStorageObjectVersion() != null && !signature.getStorageObjectVersion().isBlank()) {
            request.versionId(signature.getStorageObjectVersion());
        }
        return new ByteArrayInputStream(s3Client.getObjectAsBytes(request.build()).asByteArray());
    }

    private void requireRegistrarSignature(UploadedDocument signature) {
        if (signature.getOwnerType() != UploadedDocument.OwnerType.INSTITUTION
                || !REGISTRAR_SIGNATURE_DOCUMENT_TYPE.equals(signature.getDocumentTypeCode())
                || !SUPPORTED_IMAGE_TYPES.contains(signature.getMimeType())) {
            throw new IllegalStateException(
                    "The configured document is not an institution registrar signature image.");
        }
    }
}

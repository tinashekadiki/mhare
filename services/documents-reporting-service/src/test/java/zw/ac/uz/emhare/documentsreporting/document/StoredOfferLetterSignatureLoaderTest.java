package zw.ac.uz.emhare.documentsreporting.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.UploadedDocumentRepository;

/** @author Tinashe K */
class StoredOfferLetterSignatureLoaderTest {

    @Test
    void loadsOnlyTheConfiguredInstitutionSignatureObjectVersion() throws Exception {
        UUID documentId = UUID.randomUUID();
        UploadedDocumentRepository repository = mock(UploadedDocumentRepository.class);
        S3Client s3Client = mock(S3Client.class);
        UploadedDocument signature = signatureDocument("INSTITUTION_REGISTRAR_SIGNATURE", "image/png");
        when(repository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(signature));
        byte[] image = new byte[] {1, 2, 3, 4};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), image));

        try (var content = new StoredOfferLetterSignatureLoader(repository, s3Client).load(documentId.toString())) {
            assertArrayEquals(image, content.readAllBytes());
        }
    }

    @Test
    void rejectsMissingMalformedAndNonSignatureDocuments() {
        UploadedDocumentRepository repository = mock(UploadedDocumentRepository.class);
        S3Client s3Client = mock(S3Client.class);
        StoredOfferLetterSignatureLoader loader = new StoredOfferLetterSignatureLoader(repository, s3Client);
        UUID documentId = UUID.randomUUID();
        when(repository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> loader.load("not-a-uuid"));
        assertThrows(IllegalStateException.class, () -> loader.load(documentId.toString()));

        UUID logoId = UUID.randomUUID();
        UploadedDocument logo = signatureDocument("INSTITUTION_LOGO", "image/png");
        when(repository.findByIdAndDeletedAtIsNull(logoId)).thenReturn(Optional.of(logo));
        assertThrows(IllegalStateException.class, () -> loader.load(logoId.toString()));
    }

    private UploadedDocument signatureDocument(String documentType, String mimeType) {
        UploadedDocument document = mock(UploadedDocument.class);
        when(document.getOwnerType()).thenReturn(UploadedDocument.OwnerType.INSTITUTION);
        when(document.getDocumentTypeCode()).thenReturn(documentType);
        when(document.getMimeType()).thenReturn(mimeType);
        when(document.getStorageBucket()).thenReturn("official-documents");
        when(document.getStorageKey()).thenReturn("uploads/institution/signature.png");
        when(document.getStorageObjectVersion()).thenReturn("version-1");
        return document;
    }
}

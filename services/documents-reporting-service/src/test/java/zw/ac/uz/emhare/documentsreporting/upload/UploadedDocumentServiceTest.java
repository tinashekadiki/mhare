package zw.ac.uz.emhare.documentsreporting.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.documentsreporting.document.DocumentsStorageProperties;
import zw.ac.uz.emhare.documentsreporting.integration.DocumentVerificationOutboxService;

/** @author Tinashe K */
class UploadedDocumentServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadInspectsContentAndDeletesTheObjectWhenPersistenceRollsBack() {
        UploadedDocumentRepository repository = org.mockito.Mockito.mock(UploadedDocumentRepository.class);
        EmhareCurrentUserResolver userResolver = org.mockito.Mockito.mock(EmhareCurrentUserResolver.class);
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        UUID actorUserId = UUID.randomUUID();
        when(userResolver.requireCurrentUser()).thenReturn(new EmhareCurrentUser(
                actorUserId, actorUserId, "applicant@example.test", "applicant", "Applicant", Set.of("applicant")));
        when(s3Client.headBucket(any(java.util.function.Consumer.class))).thenReturn(HeadBucketResponse.builder().build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().versionId("object-version-1").build());
        when(repository.saveAndFlush(any(UploadedDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UploadedDocumentService service = service(repository, userResolver, s3Client, 1_000_000);
        TransactionSynchronizationManager.initSynchronization();
        UUID ownerId = UUID.randomUUID();

        var summary = service.upload(
                "APPLICATION",
                ownerId,
                "national id",
                null,
                new MockMultipartFile("file", "identity.exe", "application/octet-stream", "%PDF-1.7\ncontent".getBytes()));

        assertEquals("application/pdf", summary.mimeType());
        assertEquals("NATIONAL_ID", summary.documentTypeCode());
        assertEquals(UploadedDocument.VerificationStatus.PENDING, summary.verificationStatus());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void uploadRejectsOversizedContentBeforeCallingObjectStorage() {
        UploadedDocumentRepository repository = org.mockito.Mockito.mock(UploadedDocumentRepository.class);
        EmhareCurrentUserResolver userResolver = org.mockito.Mockito.mock(EmhareCurrentUserResolver.class);
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        UUID actorUserId = UUID.randomUUID();
        when(userResolver.requireCurrentUser()).thenReturn(new EmhareCurrentUser(
                actorUserId, actorUserId, "applicant@example.test", "applicant", "Applicant", Set.of("applicant")));
        UploadedDocumentService service = service(repository, userResolver, s3Client, 4);

        assertThrows(IllegalArgumentException.class, () -> service.upload(
                "APPLICATION",
                UUID.randomUUID(),
                "NATIONAL_ID",
                null,
                new MockMultipartFile("file", "identity.pdf", "application/pdf", "%PDF-1.7".getBytes())));

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadAcceptsInstitutionOwnedPngBrandAsset() {
        UploadedDocumentRepository repository = org.mockito.Mockito.mock(UploadedDocumentRepository.class);
        EmhareCurrentUserResolver userResolver = org.mockito.Mockito.mock(EmhareCurrentUserResolver.class);
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        UUID actorUserId = UUID.randomUUID();
        when(userResolver.requireCurrentUser()).thenReturn(new EmhareCurrentUser(
                actorUserId, actorUserId, "admin@example.test", "system-admin", "System Admin", Set.of("system-admin")));
        when(s3Client.headBucket(any(java.util.function.Consumer.class))).thenReturn(HeadBucketResponse.builder().build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().versionId("institution-logo-version-1").build());
        when(repository.saveAndFlush(any(UploadedDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UploadedDocumentService service = service(repository, userResolver, s3Client, 1_000_000);
        TransactionSynchronizationManager.initSynchronization();

        var summary = service.upload(
                "INSTITUTION",
                UUID.randomUUID(),
                "INSTITUTION_LOGO",
                null,
                new MockMultipartFile("file", "institution-logo.png", "image/png", new byte[] {
                        (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0x01
                }));

        assertEquals(UploadedDocument.OwnerType.INSTITUTION, summary.ownerType());
        assertEquals("INSTITUTION_LOGO", summary.documentTypeCode());
        assertEquals("image/png", summary.mimeType());
    }

    @Test
    void downloadSignsInlinePreviewsSeparatelyFromAttachmentDownloads() throws Exception {
        UploadedDocumentRepository repository = mock(UploadedDocumentRepository.class);
        EmhareCurrentUserResolver userResolver = mock(EmhareCurrentUserResolver.class);
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        UUID actorUserId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UploadedDocument document = new UploadedDocument(
                UploadedDocument.OwnerType.APPLICATION,
                UUID.randomUUID(),
                "IDENTITY_DOCUMENT",
                "national-id.pdf",
                "documents",
                "uploads/application/national-id.pdf",
                null,
                "application/pdf",
                128,
                "5ed7c46ae7d12e2c019a1aadcad8e3114518c0b88a0bf6a2de003aee82ebdd13",
                actorUserId,
                Instant.parse("2026-08-08T18:00:00Z"),
                null);
        when(userResolver.requireCurrentUser()).thenReturn(new EmhareCurrentUser(
                actorUserId, actorUserId, "reviewer@example.test", "reviewer", "Reviewer", Set.of("admissions-officer")));
        when(repository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(presignedRequest.url()).thenReturn(URI.create("http://localhost:9000/documents/national-id.pdf").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        UploadedDocumentService service = new UploadedDocumentService(
                repository,
                new DocumentContentInspector(),
                mock(DocumentVerificationOutboxService.class),
                userResolver,
                s3Client,
                s3Presigner,
                new DocumentsStorageProperties(
                        "http://localhost:9000",
                        "us-east-1",
                        "access-key",
                        "secret-key",
                        "documents",
                        true,
                        300,
                        1_000_000),
                Clock.fixed(Instant.parse("2026-08-08T18:00:00Z"), ZoneOffset.UTC));

        service.download(documentId, "inline");
        service.download(documentId, "attachment");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignGetObject(requestCaptor.capture());
        assertEquals(
                "inline; filename=\"national-id.pdf\"",
                requestCaptor.getAllValues().get(0).getObjectRequest().responseContentDisposition());
        assertEquals(
                "attachment; filename=\"national-id.pdf\"",
                requestCaptor.getAllValues().get(1).getObjectRequest().responseContentDisposition());
        assertThrows(IllegalArgumentException.class, () -> service.download(documentId, "unsupported"));
    }

    private UploadedDocumentService service(
            UploadedDocumentRepository repository,
            EmhareCurrentUserResolver userResolver,
            S3Client s3Client,
            long maximumUploadBytes) {
        return new UploadedDocumentService(
                repository,
                new DocumentContentInspector(),
                org.mockito.Mockito.mock(DocumentVerificationOutboxService.class),
                userResolver,
                s3Client,
                org.mockito.Mockito.mock(S3Presigner.class),
                new DocumentsStorageProperties(
                        "http://localhost:9000",
                        "us-east-1",
                        "access-key",
                        "secret-key",
                        "documents",
                        true,
                        300,
                        maximumUploadBytes),
                Clock.fixed(Instant.parse("2026-08-08T18:00:00Z"), ZoneOffset.UTC));
    }
}

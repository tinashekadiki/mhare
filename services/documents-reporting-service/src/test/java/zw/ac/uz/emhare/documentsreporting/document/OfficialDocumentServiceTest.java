package zw.ac.uz.emhare.documentsreporting.document;

import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.OfferLetterProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedOfferLetterProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedOfferLetterProjection;

/** @author Tinashe K */
class OfficialDocumentServiceTest {

    @Test
    void offerLetterAppearsInOfficialDocumentRegisterWithoutAProgressionDecision() {
        GeneratedDocumentRepository repository = mock(GeneratedDocumentRepository.class);
        GeneratedDocument document = mock(GeneratedDocument.class);
        OfferLetterProjection offerLetter = mock(OfferLetterProjection.class);
        UUID documentId = UUID.randomUUID();

        when(document.getId()).thenReturn(documentId);
        when(document.getDocumentNumber()).thenReturn("OFFER-OFR-2027-0001");
        when(document.getDocumentType()).thenReturn(GeneratedDocument.DocumentType.OFFER_LETTER);
        when(document.getOfferLetter()).thenReturn(offerLetter);
        when(document.getStatus()).thenReturn(GeneratedDocument.Status.REQUESTED);
        when(document.getTemplateCode()).thenReturn("OFFICIAL-OFFER-LETTER");
        when(document.getTemplateVersion()).thenReturn(1);
        when(document.getRequestedAt()).thenReturn(Instant.parse("2027-01-12T08:00:00Z"));
        when(offerLetter.getApplicantNumber()).thenReturn("APP-2027-0001");
        when(offerLetter.getOfferType()).thenReturn("FIRM");
        when(offerLetter.getProgrammeName()).thenReturn("Bachelor of Science Honours in Computer Science");
        when(repository.findAllByDeletedAtIsNullOrderByRequestedAtDesc()).thenReturn(List.of(document));

        OfficialDocumentService service = new OfficialDocumentService(
                repository,
                mock(PublishedOfferLetterProjectionRepository.class),
                mock(S3Presigner.class),
                new DocumentsStorageProperties(null, null, null, null, "documents", true, 300, 10_000_000),
                Clock.systemUTC());

        var summary = service.documents().getFirst();

        assertEquals(documentId, summary.id());
        assertEquals("APP-2027-0001", summary.studentNumber());
        assertEquals("FIRM", summary.decisionCode());
        assertEquals("Bachelor of Science Honours in Computer Science", summary.decisionLabel());
    }

    @Test
    void staffOfferLetterDownloadSupportsInlinePreviewAndAttachmentDisposition() throws Exception {
        GeneratedDocumentRepository repository = mock(GeneratedDocumentRepository.class);
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        GeneratedDocument document = mock(GeneratedDocument.class);
        UUID documentId = UUID.randomUUID();

        when(repository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(document.getStatus()).thenReturn(GeneratedDocument.Status.STORED);
        when(document.getStorageBucket()).thenReturn("documents");
        when(document.getStorageKey()).thenReturn("offers/OFR-2027-0001-v1.pdf");
        when(document.getDocumentNumber()).thenReturn("OFR-2027-0001-V1");
        when(document.getContentType()).thenReturn("application/pdf");
        when(document.getChecksumSha256()).thenReturn("offer-letter-checksum");
        when(presignedRequest.url()).thenReturn(URI.create("http://localhost:9000/documents/OFR-2027-0001-V1.pdf").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        OfficialDocumentService service = new OfficialDocumentService(
                repository,
                mock(PublishedOfferLetterProjectionRepository.class),
                presigner,
                new DocumentsStorageProperties(null, null, null, null, "documents", true, 300, 10_000_000),
                Clock.systemUTC());

        service.download(documentId, "inline");
        service.download(documentId, "attachment");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner, times(2)).presignGetObject(requestCaptor.capture());
        assertEquals("inline; filename=\"OFR-2027-0001-V1.pdf\"",
                requestCaptor.getAllValues().get(0).getObjectRequest().responseContentDisposition());
        assertEquals("attachment; filename=\"OFR-2027-0001-V1.pdf\"",
                requestCaptor.getAllValues().get(1).getObjectRequest().responseContentDisposition());
        assertThrows(IllegalArgumentException.class, () -> service.download(documentId, "unsupported"));
    }

    @Test
    void applicantCanDownloadOnlyTheirCurrentPublishedOfferLetter() throws Exception {
        GeneratedDocumentRepository documentRepository = mock(GeneratedDocumentRepository.class);
        PublishedOfferLetterProjectionRepository publicationRepository =
                mock(PublishedOfferLetterProjectionRepository.class);
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        GeneratedDocument document = mock(GeneratedDocument.class);
        OfferLetterProjection offerLetter = mock(OfferLetterProjection.class);
        PublishedOfferLetterProjection publication = mock(PublishedOfferLetterProjection.class);
        UUID documentId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();

        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        when(publicationRepository.findByGeneratedDocumentIdAndCurrentPublicationTrue(documentId))
                .thenReturn(Optional.of(publication));
        when(publication.getApplicantUserId()).thenReturn(applicantUserId);
        when(document.getOfferLetter()).thenReturn(offerLetter);
        when(document.getStatus()).thenReturn(GeneratedDocument.Status.STORED);
        when(document.getStorageBucket()).thenReturn("documents");
        when(document.getStorageKey()).thenReturn("offers/OFR-2027-0001-v1.pdf");
        when(document.getDocumentNumber()).thenReturn("OFR-2027-0001-V1");
        when(document.getContentType()).thenReturn("application/pdf");
        when(document.getChecksumSha256()).thenReturn("offer-letter-checksum");
        when(presignedRequest.url()).thenReturn(URI.create("http://localhost:9000/documents/OFR-2027-0001-V1.pdf").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        OfficialDocumentService service = new OfficialDocumentService(
                documentRepository, publicationRepository, presigner,
                new DocumentsStorageProperties(null, null, null, null, "documents", true, 300, 10_000_000),
                Clock.systemUTC());

        assertEquals(documentId, service.applicantOfferDownload(documentId, applicantUserId, "inline").documentId());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.applicantOfferDownload(documentId, UUID.randomUUID(), "inline"));
    }
}

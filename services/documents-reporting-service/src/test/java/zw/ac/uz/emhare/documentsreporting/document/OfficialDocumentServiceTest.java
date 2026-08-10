package zw.ac.uz.emhare.documentsreporting.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjection;

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
                mock(S3Presigner.class),
                new DocumentsStorageProperties(null, null, null, null, "documents", true, 300, 10_000_000),
                Clock.systemUTC());

        var summary = service.documents().getFirst();

        assertEquals(documentId, summary.id());
        assertEquals("APP-2027-0001", summary.studentNumber());
        assertEquals("FIRM", summary.decisionCode());
        assertEquals("Bachelor of Science Honours in Computer Science", summary.decisionLabel());
    }
}

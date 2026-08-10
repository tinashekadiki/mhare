package zw.ac.uz.emhare.documentsreporting.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjection;

/** @author Tinashe K */
class OfficialOfferLetterPdfRendererTest {
    @Test
    void rendersGovernedOfferLetterWithVerificationMetadata() throws Exception {
        GeneratedDocument document=mock(GeneratedDocument.class);
        when(document.getDocumentNumber()).thenReturn("OFFER-OF-2028-000001");
        OfferLetterProjection offer=mock(OfferLetterProjection.class);
        when(offer.getOfferNumber()).thenReturn("OF-2028-000001");
        when(offer.getApplicationNumber()).thenReturn("EMH-2028-000001");
        when(offer.getApplicantName()).thenReturn("Nyasha Moyo");
        when(offer.getProgrammeName()).thenReturn("Bachelor of Science in Computer Science");
        when(offer.getProgrammeCode()).thenReturn("BSC-CS");
        when(offer.getOfferType()).thenReturn("FIRM");
        when(offer.getAcceptanceDeadline()).thenReturn(Instant.parse("2028-02-28T21:59:59Z"));
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));

        var rendered=new OfficialOfferLetterPdfRenderer().render(document,offer);

        assertTrue(rendered.content().length>1_000); assertEquals(1,rendered.pageCount());
        try(var pdf=Loader.loadPDF(rendered.content())){
            assertEquals("Tinashe K",pdf.getDocumentInformation().getAuthor());
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("OFFICIAL ADMISSION OFFER"));
            assertTrue(text.contains("Nyasha Moyo"));
            assertTrue(text.contains("BSC-CS"));
        }
    }
}

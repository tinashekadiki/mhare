package zw.ac.uz.emhare.documentsreporting.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.documentsreporting.projection.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.projection.PublishedResultProjection;

/** @author Tinashe K */
class OfficialResultSlipPdfRendererTest {

    @Test
    void rendersMultipageOfficialResultEvidenceWithDecisionAndMetadata() throws Exception {
        GeneratedDocument document = mock(GeneratedDocument.class);
        when(document.getStudentNumber()).thenReturn("STU-2027-0000001");
        when(document.getDocumentNumber()).thenReturn("RSLIP-PRG-2027-S1-STU-2027-0000001-V1");

        ProgressionDecisionProjection decision = mock(ProgressionDecisionProjection.class);
        when(decision.getStudentNumber()).thenReturn("STU-2027-0000001");
        when(decision.getAcademicPeriodCode()).thenReturn("2027-S1");
        when(decision.getProgrammePeriodNumber()).thenReturn(1);
        when(decision.getDecisionLabel()).thenReturn("Proceed to programme period 2");
        when(decision.getDecisionNumber()).thenReturn("PRG-2027-S1-STU-2027-0000001-V1");
        when(decision.getWeightedAverage()).thenReturn(new BigDecimal("68.25"));
        when(decision.getPassedCredits()).thenReturn(new BigDecimal("300.00"));
        when(decision.getAttemptedCredits()).thenReturn(new BigDecimal("300.00"));
        when(decision.getPublishedAt()).thenReturn(Instant.parse("2027-12-20T10:00:00Z"));

        PublishedResultProjection result = mock(PublishedResultProjection.class);
        when(result.getModuleCode()).thenReturn("ACC101");
        when(result.getModuleName()).thenReturn("Financial Accounting I");
        when(result.getCreditValue()).thenReturn(new BigDecimal("12.00"));
        when(result.getFinalMark()).thenReturn(new BigDecimal("68.25"));
        when(result.getGrade()).thenReturn("2.1");
        when(result.isPassing()).thenReturn(true);

        var rendered = new OfficialResultSlipPdfRenderer().render(
                document,
                decision,
                Collections.nCopies(25, result));

        assertTrue(rendered.content().length > 1_000);
        assertEquals(2, rendered.pageCount());
        try (var pdf = Loader.loadPDF(rendered.content())) {
            assertEquals(2, pdf.getNumberOfPages());
            assertEquals("Tinashe K", pdf.getDocumentInformation().getAuthor());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("OFFICIAL RESULT SLIP"));
            assertTrue(text.contains("Proceed to programme period 2"));
            assertTrue(text.contains("STU-2027-0000001"));
        }
    }
}

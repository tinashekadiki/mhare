package zw.ac.uz.emhare.documentsreporting.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedResultProjection;

/**
 * @author Tinashe K
 */
class OfficialResultSlipPdfRendererTest {

  @Test
  void rejectsDocumentRenderingWhenTheGovernedProductWatermarkIsMissing() throws Exception {
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      assertThrows(
          IllegalStateException.class,
          () ->
              EmhareDocumentBranding.addProductWatermarks(
                  pdf, "/documents/missing-emhare-mark.png"));
    }
  }

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

    var rendered =
        new OfficialResultSlipPdfRenderer()
            .render(document, decision, Collections.nCopies(25, result));

    assertTrue(rendered.content().length > 1_000);
    assertEquals(2, rendered.pageCount());
    try (var pdf = Loader.loadPDF(rendered.content())) {
      assertEquals(2, pdf.getNumberOfPages());
      assertEquals("Tinashe K", pdf.getDocumentInformation().getAuthor());
      var firstPagePreview = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
      long visibleHeaderPixels = 0;
      for (int y = 20; y < 90; y++) {
        for (int x = 35; x < 420; x++) {
          int pixel = firstPagePreview.getRGB(x, y);
          int red = (pixel >> 16) & 0xff;
          int green = (pixel >> 8) & 0xff;
          int blue = pixel & 0xff;
          if (red > 235 && green > 235 && blue > 235) visibleHeaderPixels++;
        }
      }
      assertTrue(
          visibleHeaderPixels > 20,
          "The institution title and official-document heading must remain visible in the result-slip header.");
      for (var page : pdf.getPages()) {
        assertTrue(
            java.util.stream.StreamSupport.stream(
                        page.getResources().getXObjectNames().spliterator(), false)
                    .count()
                >= 1,
            "Every official result-slip page must carry the eMhare product watermark.");
      }
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("OFFICIAL RESULT SLIP"));
      assertTrue(text.contains("Proceed to programme period 2"));
      assertTrue(text.contains("STU-2027-0000001"));
    }
  }
}

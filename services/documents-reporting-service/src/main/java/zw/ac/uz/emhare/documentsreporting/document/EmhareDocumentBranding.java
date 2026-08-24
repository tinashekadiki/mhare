package zw.ac.uz.emhare.documentsreporting.document;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

/**
 * Applies the governed secondary eMhare mark without displacing institution identity. @author
 * Tinashe K
 */
final class EmhareDocumentBranding {
  private static final String EMBLEM_RESOURCE = "/documents/emhare-emblem-blue.png";
  private static final float WATERMARK_OPACITY = 0.045f;
  private static final float WATERMARK_PAGE_RATIO = 0.40f;

  private EmhareDocumentBranding() {}

  static void addProductWatermarks(PDDocument document) throws IOException {
    addProductWatermarks(document, EMBLEM_RESOURCE);
  }

  static void addProductWatermarks(PDDocument document, String emblemResource) throws IOException {
    byte[] emblemBytes;
    try (InputStream emblem = EmhareDocumentBranding.class.getResourceAsStream(emblemResource)) {
      if (emblem == null) {
        throw new IllegalStateException("The eMhare document watermark asset is missing.");
      }
      emblemBytes = emblem.readAllBytes();
    }

    PDImageXObject watermark =
        PDImageXObject.createFromByteArray(document, emblemBytes, "eMhare product watermark");
    for (PDPage page : document.getPages()) {
      addProductWatermark(document, page, watermark);
    }
  }

  private static void addProductWatermark(
      PDDocument document, PDPage page, PDImageXObject watermark) throws IOException {
    float pageWidth = page.getMediaBox().getWidth();
    float pageHeight = page.getMediaBox().getHeight();
    float watermarkSize = Math.min(pageWidth, pageHeight) * WATERMARK_PAGE_RATIO;
    float watermarkX = (pageWidth - watermarkSize) / 2f;
    float watermarkY = (pageHeight - watermarkSize) / 2f;

    PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
    transparency.setNonStrokingAlphaConstant(WATERMARK_OPACITY);
    try (PDPageContentStream content =
        new PDPageContentStream(
            document, page, PDPageContentStream.AppendMode.PREPEND, true, true)) {
      content.saveGraphicsState();
      content.setGraphicsStateParameters(transparency);
      content.drawImage(watermark, watermarkX, watermarkY, watermarkSize, watermarkSize);
      content.restoreGraphicsState();
    }
  }
}

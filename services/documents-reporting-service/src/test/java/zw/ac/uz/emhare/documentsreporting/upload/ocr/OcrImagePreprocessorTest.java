package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Raster OCR preprocessing regression coverage. @author Tinashe K */
class OcrImagePreprocessorTest {

  private final OcrImagePreprocessor preprocessor = new OcrImagePreprocessor();

  @Test
  void enlargesAndNormalisesLowResolutionRasterEvidence() throws Exception {
    BufferedImage source = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = source.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
    graphics.setColor(new Color(80, 50, 30));
    graphics.fillRect(40, 30, 220, 40);
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor.prepare(output.toByteArray(), "image/png", "results.png");

    BufferedImage preparedImage =
        ImageIO.read(new java.io.ByteArrayInputStream(prepared.content()));
    assertThat(prepared.fileName()).isEqualTo("results.ocr.png");
    assertThat(prepared.preprocessed()).isTrue();
    assertThat(preparedImage.getWidth()).isEqualTo(600);
    assertThat(preparedImage.getHeight()).isEqualTo(200);
    assertThat(preparedImage.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
  }

  @Test
  void leavesPdfEvidenceByteForByteUnchanged() {
    byte[] content = "%PDF-1.7 fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor.prepare(content, "application/pdf", "certificate.pdf");

    assertThat(prepared.content()).isSameAs(content);
    assertThat(prepared.fileName()).isEqualTo("certificate.pdf");
    assertThat(prepared.preprocessed()).isFalse();
  }

  @Test
  void leavesAdequatelySizedRasterEvidenceUnchanged() throws Exception {
    BufferedImage source = new BufferedImage(1300, 800, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "jpg", output);
    byte[] content = output.toByteArray();

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor.prepare(content, "image/jpeg", "passport.jpg");

    assertThat(prepared.content()).isSameAs(content);
    assertThat(prepared.fileName()).isEqualTo("passport.jpg");
    assertThat(prepared.preprocessed()).isFalse();
  }

  @Test
  void preparesAnEnlargedCentralResultsRegionForBorderlessQualificationLayouts() throws Exception {
    BufferedImage source = new BufferedImage(1500, 1125, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = source.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
    graphics.setColor(Color.BLACK);
    graphics.drawString("ENGLISH LANGUAGE", 300, 600);
    graphics.drawString("C", 1250, 600);
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor
            .prepareQualificationRegion(output.toByteArray(), "image/png", "certificate.png")
            .orElseThrow();

    BufferedImage preparedImage =
        ImageIO.read(new java.io.ByteArrayInputStream(prepared.content()));
    assertThat(prepared.fileName()).isEqualTo("certificate.qualification-region.ocr.png");
    assertThat(prepared.preprocessed()).isTrue();
    assertThat(preparedImage.getWidth()).isEqualTo(2340);
    assertThat(preparedImage.getHeight()).isGreaterThan(750);

    OcrImagePreprocessor.PreparedOcrInput contrastPrepared =
        preprocessor
            .prepareQualificationContrastRegion(
                output.toByteArray(), "image/png", "certificate.png")
            .orElseThrow();
    assertThat(contrastPrepared.fileName())
        .isEqualTo("certificate.qualification-contrast-region.ocr.png");
  }

  @Test
  void doesNotCreateAQualificationRegionForPdfEvidence() {
    assertThat(
            preprocessor.prepareQualificationRegion(
                "%PDF fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "application/pdf",
                "certificate.pdf"))
        .isEmpty();
  }

  @Test
  void includesTheCompleteResultsTableForPortraitQualificationEvidence() throws Exception {
    BufferedImage source = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor
            .prepareQualificationRegion(output.toByteArray(), "image/png", "statement.png")
            .orElseThrow();
    BufferedImage preparedImage =
        ImageIO.read(new java.io.ByteArrayInputStream(prepared.content()));

    assertThat(preparedImage.getHeight()).isEqualTo(2400);
    assertThat(preparedImage.getWidth()).isGreaterThan(1800);
  }

  @Test
  void fallsBackToOriginalInputWhenImageBytesCannotBeDecoded() {
    byte[] content = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor.prepare(content, "image/jpeg", "damaged.jpg");

    assertThat(prepared.content()).isSameAs(content);
    assertThat(prepared.fileName()).isEqualTo("damaged.jpg");
    assertThat(prepared.preprocessed()).isFalse();
  }

  @Test
  void rejectsAnUnreadableQualificationRegionWithoutFailingTheUpload() {
    assertThat(
            preprocessor.prepareQualificationRegion(
                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "image/png",
                "damaged.png"))
        .isEmpty();
  }

  @Test
  void recognisesRasterFileExtensionsWhenMimeMetadataIsMissing() throws Exception {
    BufferedImage source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);
    byte[] content = output.toByteArray();

    OcrImagePreprocessor.PreparedOcrInput jpgRegion =
        preprocessor.prepareQualificationRegion(content, null, "scan.jpg").orElseThrow();
    OcrImagePreprocessor.PreparedOcrInput jpegRegion =
        preprocessor
            .prepareQualificationRegion(content, "application/octet-stream", "scan.jpeg")
            .orElseThrow();
    OcrImagePreprocessor.PreparedOcrInput safelyNamedRegion =
        preprocessor.prepareQualificationRegion(content, "image/jpeg", null).orElseThrow();

    assertThat(jpgRegion.fileName()).isEqualTo("scan.qualification-region.ocr.png");
    assertThat(jpegRegion.fileName()).isEqualTo("scan.qualification-region.ocr.png");
    assertThat(safelyNamedRegion.fileName()).isEqualTo("evidence.qualification-region.ocr.png");
  }

  @Test
  void suppressesLightSecurityPatternNoiseInTheQualificationRegion() throws Exception {
    BufferedImage source = new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = source.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
    graphics.setColor(new Color(190, 190, 190));
    graphics.fillRect(100, 300, 400, 200);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(200, 350, 100, 30);
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor
            .prepareQualificationRegion(output.toByteArray(), "image/png", "certificate.png")
            .orElseThrow();
    BufferedImage preparedImage =
        ImageIO.read(new java.io.ByteArrayInputStream(prepared.content()));

    assertThat(preparedImage.getRaster().getSample(600, 800, 0)).isEqualTo(255);
    assertThat(preparedImage.getRaster().getSample(500, 550, 0)).isEqualTo(0);
  }

  @Test
  void retainsSparseLowContrastTextFromOnlineResultScreenshots() throws Exception {
    BufferedImage source = new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = source.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
    graphics.setColor(new Color(190, 190, 190));
    graphics.fillRect(200, 350, 100, 30);
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor
            .prepareQualificationRegion(output.toByteArray(), "image/png", "results.png")
            .orElseThrow();
    BufferedImage preparedImage =
        ImageIO.read(new java.io.ByteArrayInputStream(prepared.content()));

    assertThat(preparedImage.getRaster().getSample(500, 550, 0)).isEqualTo(0);
  }
}

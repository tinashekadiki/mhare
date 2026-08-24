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
  void fallsBackToOriginalInputWhenImageBytesCannotBeDecoded() {
    byte[] content = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    OcrImagePreprocessor.PreparedOcrInput prepared =
        preprocessor.prepare(content, "image/jpeg", "damaged.jpg");

    assertThat(prepared.content()).isSameAs(content);
    assertThat(prepared.fileName()).isEqualTo("damaged.jpg");
    assertThat(prepared.preprocessed()).isFalse();
  }
}

package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Normalises applicant raster evidence before OCR without altering the stored source file. @author
 * Tinashe K
 */
@Component
public class OcrImagePreprocessor {

  private static final int PREPROCESS_BELOW_WIDTH = 1200;
  private static final int MAX_DIMENSION = 2400;
  private static final double CONTRAST_TAIL_PERCENTAGE = 0.005;

  public PreparedOcrInput prepare(byte[] content, String mimeType, String originalFileName) {
    if (!isSupportedRaster(mimeType, originalFileName)) {
      return new PreparedOcrInput(content, originalFileName, false);
    }
    try {
      BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
      if (decoded == null) return new PreparedOcrInput(content, originalFileName, false);
      if (decoded.getWidth() >= PREPROCESS_BELOW_WIDTH) {
        return new PreparedOcrInput(content, originalFileName, false);
      }
      BufferedImage enlarged = enlargeLowResolutionImage(decoded);
      BufferedImage normalised = normaliseContrast(enlarged);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(normalised, "png", output)) {
        return new PreparedOcrInput(content, originalFileName, false);
      }
      return new PreparedOcrInput(output.toByteArray(), ocrFileName(originalFileName), true);
    } catch (IOException | RuntimeException exception) {
      return new PreparedOcrInput(content, originalFileName, false);
    }
  }

  private boolean isSupportedRaster(String mimeType, String fileName) {
    String normalisedMimeType = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
    String normalisedFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    return normalisedMimeType.equals("image/png")
        || normalisedMimeType.equals("image/jpeg")
        || normalisedFileName.endsWith(".png")
        || normalisedFileName.endsWith(".jpg")
        || normalisedFileName.endsWith(".jpeg");
  }

  private BufferedImage enlargeLowResolutionImage(BufferedImage source) {
    double scale = 2.0;
    scale = Math.min(scale, (double) MAX_DIMENSION / source.getWidth());
    scale = Math.min(scale, (double) MAX_DIMENSION / source.getHeight());
    if (scale <= 1.0) return source;
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage enlarged = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = enlarged.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(source, 0, 0, width, height, null);
    graphics.dispose();
    return enlarged;
  }

  private BufferedImage normaliseContrast(BufferedImage source) {
    int width = source.getWidth();
    int height = source.getHeight();
    int[] greyValues = new int[width * height];
    int[] histogram = new int[256];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rgb = source.getRGB(x, y);
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        int grey = Math.max(0, Math.min(255, (red * 299 + green * 587 + blue * 114) / 1000));
        greyValues[y * width + x] = grey;
        histogram[grey]++;
      }
    }
    int tailPixels = (int) Math.floor(greyValues.length * CONTRAST_TAIL_PERCENTAGE);
    int lower = percentileBoundary(histogram, tailPixels, true);
    int upper = percentileBoundary(histogram, tailPixels, false);
    if (upper <= lower) {
      lower = 0;
      upper = 255;
    }
    BufferedImage normalised = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int grey = greyValues[y * width + x];
        int stretched = Math.max(0, Math.min(255, (grey - lower) * 255 / (upper - lower)));
        normalised.getRaster().setSample(x, y, 0, stretched);
      }
    }
    return normalised;
  }

  private int percentileBoundary(int[] histogram, int tailPixels, boolean ascending) {
    int accumulated = 0;
    for (int offset = 0; offset < histogram.length; offset++) {
      int value = ascending ? offset : histogram.length - 1 - offset;
      accumulated += histogram[value];
      if (accumulated > tailPixels) return value;
    }
    return ascending ? 0 : 255;
  }

  private String ocrFileName(String originalFileName) {
    String safeFileName =
        originalFileName == null || originalFileName.isBlank() ? "evidence" : originalFileName;
    int extensionStart = safeFileName.lastIndexOf('.');
    String baseName = extensionStart > 0 ? safeFileName.substring(0, extensionStart) : safeFileName;
    return baseName + ".ocr.png";
  }

  public record PreparedOcrInput(byte[] content, String fileName, boolean preprocessed) {}
}

package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
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
  private static final int SECURITY_BACKGROUND_FOREGROUND_THRESHOLD = 150;
  private static final int LOW_CONTRAST_FOREGROUND_THRESHOLD = 220;
  private static final double SECURITY_PATTERN_PIXEL_RATIO = 0.12;
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

  public Optional<PreparedOcrInput> prepareQualificationRegion(
      byte[] content, String mimeType, String originalFileName) {
    return prepareQualificationRegion(content, mimeType, originalFileName, true);
  }

  public Optional<PreparedOcrInput> prepareQualificationContrastRegion(
      byte[] content, String mimeType, String originalFileName) {
    return prepareQualificationRegion(content, mimeType, originalFileName, false);
  }

  private Optional<PreparedOcrInput> prepareQualificationRegion(
      byte[] content,
      String mimeType,
      String originalFileName,
      boolean suppressSecurityBackground) {
    if (!isSupportedRaster(mimeType, originalFileName)) return Optional.empty();
    try {
      BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
      if (decoded == null) return Optional.empty();
      boolean portrait = decoded.getHeight() > decoded.getWidth() * 1.1;
      double leftRatio = portrait ? 0.05 : 0.12;
      double topRatio = portrait ? 0.10 : 0.35;
      double widthRatio = portrait ? 0.90 : 0.78;
      double heightRatio = portrait ? 0.85 : 0.35;
      int left = Math.max(0, (int) Math.round(decoded.getWidth() * leftRatio));
      int top = Math.max(0, (int) Math.round(decoded.getHeight() * topRatio));
      int width =
          Math.max(
              1,
              Math.min(
                  decoded.getWidth() - left, (int) Math.round(decoded.getWidth() * widthRatio)));
      int height =
          Math.max(
              1,
              Math.min(
                  decoded.getHeight() - top, (int) Math.round(decoded.getHeight() * heightRatio)));
      BufferedImage region = decoded.getSubimage(left, top, width, height);
      BufferedImage enlarged = enlargeLowResolutionImage(region);
      BufferedImage normalised =
          suppressSecurityBackground
              ? suppressLightQualificationBackground(enlarged)
              : normaliseContrast(enlarged);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(normalised, "png", output)) return Optional.empty();
      return Optional.of(
          new PreparedOcrInput(
              output.toByteArray(),
              qualificationRegionFileName(originalFileName, suppressSecurityBackground),
              true));
    } catch (IOException | RuntimeException exception) {
      return Optional.empty();
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

  private BufferedImage suppressLightQualificationBackground(BufferedImage source) {
    int width = source.getWidth();
    int height = source.getHeight();
    int foregroundThreshold = qualificationForegroundThreshold(source);
    BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rgb = source.getRGB(x, y);
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        int grey = (red * 299 + green * 587 + blue * 114) / 1000;
        binary.getRaster().setSample(x, y, 0, grey <= foregroundThreshold ? 0 : 255);
      }
    }
    return binary;
  }

  private int qualificationForegroundThreshold(BufferedImage source) {
    long securityPatternPixels = 0;
    long totalPixels = (long) source.getWidth() * source.getHeight();
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        int rgb = source.getRGB(x, y);
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        int grey = (red * 299 + green * 587 + blue * 114) / 1000;
        if (grey >= SECURITY_BACKGROUND_FOREGROUND_THRESHOLD
            && grey <= LOW_CONTRAST_FOREGROUND_THRESHOLD) securityPatternPixels++;
      }
    }
    return (double) securityPatternPixels / totalPixels >= SECURITY_PATTERN_PIXEL_RATIO
        ? SECURITY_BACKGROUND_FOREGROUND_THRESHOLD
        : LOW_CONTRAST_FOREGROUND_THRESHOLD;
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

  private String qualificationRegionFileName(
      String originalFileName, boolean suppressSecurityBackground) {
    String safeFileName =
        originalFileName == null || originalFileName.isBlank() ? "evidence" : originalFileName;
    int extensionStart = safeFileName.lastIndexOf('.');
    String baseName = extensionStart > 0 ? safeFileName.substring(0, extensionStart) : safeFileName;
    return baseName
        + (suppressSecurityBackground
            ? ".qualification-region.ocr.png"
            : ".qualification-contrast-region.ocr.png");
  }

  public record PreparedOcrInput(byte[] content, String fileName, boolean preprocessed) {}
}

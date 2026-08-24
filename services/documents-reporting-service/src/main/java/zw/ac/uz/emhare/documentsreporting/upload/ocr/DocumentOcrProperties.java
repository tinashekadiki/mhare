package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Tinashe K
 */
@ConfigurationProperties("emhare.documents.ocr")
public record DocumentOcrProperties(
    boolean enabled,
    String baseUrl,
    String engineName,
    String engineVersion,
    int maximumAttempts,
    Duration retryDelay,
    Duration documentTimeout) {

  public DocumentOcrProperties {
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:5001" : baseUrl;
    engineName = engineName == null || engineName.isBlank() ? "DOCLING_RAPIDOCR" : engineName;
    engineVersion =
        engineVersion == null || engineVersion.isBlank() ? "docling-serve-v1.29.0" : engineVersion;
    maximumAttempts = maximumAttempts < 1 ? 3 : Math.min(maximumAttempts, 3);
    retryDelay = retryDelay == null ? Duration.ofSeconds(10) : retryDelay;
    documentTimeout = documentTimeout == null ? Duration.ofMinutes(2) : documentTimeout;
  }
}

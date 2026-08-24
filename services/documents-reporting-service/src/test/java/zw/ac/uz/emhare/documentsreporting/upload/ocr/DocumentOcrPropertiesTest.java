package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** OCR configuration boundary coverage. @author Tinashe K */
class DocumentOcrPropertiesTest {

  @Test
  void suppliesSafeLocalDefaultsAndCapsRetryAttempts() {
    DocumentOcrProperties defaults = new DocumentOcrProperties(true, " ", null, "", 0, null, null);

    assertThat(defaults.baseUrl()).isEqualTo("http://localhost:5001");
    assertThat(defaults.engineName()).isEqualTo("DOCLING_RAPIDOCR");
    assertThat(defaults.engineVersion()).isEqualTo("docling-serve-v1.29.0");
    assertThat(defaults.maximumAttempts()).isEqualTo(3);
    assertThat(defaults.retryDelay()).isEqualTo(Duration.ofSeconds(10));
    assertThat(defaults.documentTimeout()).isEqualTo(Duration.ofMinutes(2));

    DocumentOcrProperties capped =
        new DocumentOcrProperties(
            false,
            "http://docling:5001",
            "engine",
            "version",
            9,
            Duration.ofSeconds(4),
            Duration.ofSeconds(30));
    assertThat(capped.maximumAttempts()).isEqualTo(3);
    assertThat(capped.baseUrl()).isEqualTo("http://docling:5001");
  }
}

package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.client.DoclingServeClient;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Docling HTTP timeout wiring regression coverage. @author Tinashe K */
class DocumentOcrConfigurationTest {

  @Test
  void keepsTheClientReadTimeoutBeyondTheServerDocumentTimeout() throws Exception {
    DocumentOcrProperties properties =
        new DocumentOcrProperties(
            true,
            "http://localhost:5001",
            "DOCLING_RAPIDOCR",
            "docling-serve-v1.29.0",
            3,
            Duration.ofSeconds(10),
            Duration.ofMinutes(2));

    DoclingServeApi client = new DocumentOcrConfiguration().doclingServeApi(properties);

    Field readTimeoutField = DoclingServeClient.class.getDeclaredField("readTimeout");
    readTimeoutField.setAccessible(true);
    Duration configuredReadTimeout = (Duration) readTimeoutField.get(client);
    assertThat(configuredReadTimeout)
        .isEqualTo(properties.documentTimeout().plusSeconds(30))
        .isGreaterThan(properties.documentTimeout());
  }
}

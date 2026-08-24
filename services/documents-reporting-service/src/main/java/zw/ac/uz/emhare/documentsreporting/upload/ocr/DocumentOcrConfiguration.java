package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import ai.docling.serve.api.DoclingServeApi;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local Docling client wiring. Request/response logging is deliberately disabled for PII
 * safety. @author Tinashe K
 */
@Configuration
@EnableConfigurationProperties(DocumentOcrProperties.class)
public class DocumentOcrConfiguration {

  private static final Duration CLIENT_RESPONSE_GRACE_PERIOD = Duration.ofSeconds(30);

  @Bean
  DoclingServeApi doclingServeApi(DocumentOcrProperties properties) {
    return DoclingServeApi.builder()
        .baseUrl(properties.baseUrl())
        .readTimeout(properties.documentTimeout().plus(CLIENT_RESPONSE_GRACE_PERIOD))
        .build();
  }
}

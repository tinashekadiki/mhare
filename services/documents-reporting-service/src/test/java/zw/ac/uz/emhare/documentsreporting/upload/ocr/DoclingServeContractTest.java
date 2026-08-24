package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OcrEngine;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Pinned Docling Serve and official Java client compatibility contract. @author Tinashe K */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_DOCLING_CONTRACT_TEST", matches = "true")
class DoclingServeContractTest {

  @Container
  private static final GenericContainer<?> DOCLING =
      new GenericContainer<>(
              DockerImageName.parse("ghcr.io/docling-project/docling-serve-cpu:v1.29.0"))
          .withExposedPorts(5001)
          .withEnv("DOCLING_SERVE_ENABLE_UI", "false")
          .withEnv("DOCLING_SERVE_ENG_KIND", "local")
          .withEnv("DOCLING_SERVE_LOAD_MODELS_AT_BOOT", "true")
          .withEnv("DOCLING_DEVICE", "cpu")
          .waitingFor(
              Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

  @Test
  @Timeout(value = 6, unit = TimeUnit.MINUTES)
  void convertsApplicantEvidenceWithRapidOcrUsingTheOfficialJavaClient() throws Exception {
    DoclingServeApi client =
        DoclingServeApi.builder()
            .baseUrl("http://" + DOCLING.getHost() + ":" + DOCLING.getMappedPort(5001))
            .build();
    ConvertDocumentRequest request =
        ConvertDocumentRequest.builder()
            .source(
                FileSource.builder()
                    .filename("redacted-national-id-fixture.pdf")
                    .base64String(Base64.getEncoder().encodeToString(redactedFixturePdf()))
                    .build())
            .options(
                ConvertDocumentOptions.builder()
                    .toFormat(OutputFormat.TEXT)
                    .toFormat(OutputFormat.JSON)
                    .doOcr(true)
                    .ocrEngine(OcrEngine.RAPIDOCR)
                    .ocrLang("en")
                    .documentTimeout(Duration.ofMinutes(2))
                    .includeImages(false)
                    .abortOnError(false)
                    .build())
            .target(InBodyTarget.builder().build())
            .build();

    InBodyConvertDocumentResponse response =
        (InBodyConvertDocumentResponse) client.convertSource(request);

    assertThat(response.getStatus()).isEqualToIgnoringCase("success");
    assertThat(response.getDocument().getTextContent()).contains("National ID", "63-000000A00");
    assertThat(response.getDocument().getJsonContent()).isNotNull();
  }

  private byte[] redactedFixturePdf() throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(72, 700);
        content.showText("National ID: 63-000000A00");
        content.endText();
      }
      document.save(output);
      return output.toByteArray();
    }
  }
}

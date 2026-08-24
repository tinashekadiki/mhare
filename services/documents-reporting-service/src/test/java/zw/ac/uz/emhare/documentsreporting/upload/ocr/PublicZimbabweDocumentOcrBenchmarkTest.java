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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in benchmark for locally curated public Zimbabwean document specimens. Raw OCR output and
 * personal values are deliberately excluded from the generated report.
 *
 * @author Tinashe K
 */
@EnabledIfEnvironmentVariable(named = "RUN_PUBLIC_OCR_BENCHMARK", matches = "true")
class PublicZimbabweDocumentOcrBenchmarkTest {

  private static final Duration BENCHMARK_DOCUMENT_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration CLIENT_RESPONSE_GRACE_PERIOD = Duration.ofSeconds(30);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ApplicantEvidenceFactExtractor factExtractor = new ApplicantEvidenceFactExtractor();
  private final OcrImagePreprocessor imagePreprocessor = new OcrImagePreprocessor();

  @Test
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void benchmarksRapidOcrAgainstCuratedPublicZimbabweanDocuments() throws Exception {
    Path corpusDirectory =
        Path.of(requireEnvironmentVariable("OCR_BENCHMARK_DIRECTORY")).toAbsolutePath();
    BenchmarkManifest manifest =
        objectMapper.readValue(
            Files.readString(corpusDirectory.resolve("manifest.json")), BenchmarkManifest.class);
    String baseUrl =
        System.getenv().getOrDefault("DOCLING_BENCHMARK_BASE_URL", "http://localhost:5001");
    DoclingServeApi client =
        DoclingServeApi.builder()
            .baseUrl(baseUrl)
            .readTimeout(BENCHMARK_DOCUMENT_TIMEOUT.plus(CLIENT_RESPONSE_GRACE_PERIOD))
            .build();
    List<BenchmarkDocumentResult> results = new ArrayList<>();
    List<String> failures = new ArrayList<>();

    for (BenchmarkDocument document : manifest.documents()) {
      Path documentPath = corpusDirectory.resolve(document.file()).normalize();
      assertThat(documentPath).startsWith(corpusDirectory);
      assertThat(documentPath).isRegularFile();
      assertThat(sha256(documentPath)).isEqualTo(document.sha256());
      long startedAt = System.nanoTime();
      BenchmarkConversion conversion = convert(client, documentPath);
      InBodyConvertDocumentResponse response = conversion.response();
      String text = response.getDocument().getTextContent();
      if (text == null || text.isBlank()) text = response.getDocument().getMarkdownContent();
      if (text == null) text = "";
      String normalizedText = normalize(text);
      List<String> matchedFragments = new ArrayList<>();
      List<String> missingFragments = new ArrayList<>();
      for (String expectedFragment : document.expectedTextFragments()) {
        if (normalizedText.contains(normalize(expectedFragment)))
          matchedFragments.add(expectedFragment);
        else missingFragments.add(expectedFragment);
      }
      double recall =
          document.expectedTextFragments().isEmpty()
              ? 1.0
              : (double) matchedFragments.size() / document.expectedTextFragments().size();
      ApplicantEvidenceFactExtractor.ExtractionFacts extractedFacts = factExtractor.extract(text);
      Map<String, Boolean> expectedFactMatches = new LinkedHashMap<>();
      for (Map.Entry<String, String> expectedFact : document.expectedFacts().entrySet()) {
        boolean matches =
            expectedFact
                .getValue()
                .equals(String.valueOf(extractedFacts.facts().get(expectedFact.getKey())));
        expectedFactMatches.put(expectedFact.getKey(), matches);
      }
      long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
      results.add(
          new BenchmarkDocumentResult(
              document.id(),
              response.getStatus(),
              conversion.inputPreprocessed(),
              durationMillis,
              recall,
              List.copyOf(matchedFragments),
              List.copyOf(missingFragments),
              extractedFacts.facts().keySet().stream()
                  .filter(key -> !"lines".equals(key))
                  .sorted()
                  .toList(),
              Map.copyOf(expectedFactMatches)));
      if (!"success".equalsIgnoreCase(response.getStatus())) {
        failures.add(document.id() + " conversion status was " + response.getStatus());
      }
      if (recall < document.minimumExpectedFragmentRecall()) {
        failures.add(
            document.id()
                + " expected-fragment recall was "
                + String.format(Locale.ROOT, "%.2f", recall)
                + " but requires "
                + String.format(Locale.ROOT, "%.2f", document.minimumExpectedFragmentRecall()));
      }
      expectedFactMatches.forEach(
          (key, matches) -> {
            if (!matches) failures.add(document.id() + " did not extract expected fact " + key);
          });
    }

    BenchmarkReport report =
        new BenchmarkReport(
            "DOCLING_RAPIDOCR",
            manifest.corpusVersion(),
            Instant.now().toString(),
            List.copyOf(results),
            List.copyOf(failures));
    Path reportPath = corpusDirectory.resolve("reports/latest-benchmark.json");
    Files.createDirectories(reportPath.getParent());
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

    assertThat(failures)
        .withFailMessage("Public Zimbabwe document OCR benchmark failures: %s", failures)
        .isEmpty();
  }

  private BenchmarkConversion convert(DoclingServeApi client, Path documentPath) throws Exception {
    OcrImagePreprocessor.PreparedOcrInput preparedInput =
        imagePreprocessor.prepare(
            Files.readAllBytes(documentPath),
            Files.probeContentType(documentPath),
            documentPath.getFileName().toString());
    ConvertDocumentRequest request =
        ConvertDocumentRequest.builder()
            .source(
                FileSource.builder()
                    .filename(preparedInput.fileName())
                    .base64String(Base64.getEncoder().encodeToString(preparedInput.content()))
                    .build())
            .options(
                ConvertDocumentOptions.builder()
                    .toFormat(OutputFormat.TEXT)
                    .toFormat(OutputFormat.JSON)
                    .doOcr(true)
                    .ocrEngine(OcrEngine.RAPIDOCR)
                    .ocrLang("en")
                    .documentTimeout(BENCHMARK_DOCUMENT_TIMEOUT)
                    .includeImages(false)
                    .abortOnError(false)
                    .build())
            .target(InBodyTarget.builder().build())
            .build();
    return new BenchmarkConversion(
        (InBodyConvertDocumentResponse) client.convertSource(request),
        preparedInput.preprocessed());
  }

  private String requireEnvironmentVariable(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must point to the local benchmark corpus.");
    }
    return value;
  }

  private String normalize(String value) {
    return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
  }

  private String sha256(Path documentPath) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(documentPath)));
  }

  record BenchmarkManifest(
      String corpusVersion, String author, String handling, List<BenchmarkDocument> documents) {}

  record BenchmarkDocument(
      String id,
      String file,
      String sourceUrl,
      String sha256,
      double minimumExpectedFragmentRecall,
      List<String> expectedTextFragments,
      Map<String, String> expectedFacts) {}

  record BenchmarkDocumentResult(
      String id,
      String conversionStatus,
      boolean inputPreprocessed,
      long durationMillis,
      double expectedFragmentRecall,
      List<String> matchedExpectedFragments,
      List<String> missingExpectedFragments,
      List<String> proposedFactKeys,
      Map<String, Boolean> expectedFactMatches) {}

  record BenchmarkConversion(InBodyConvertDocumentResponse response, boolean inputPreprocessed) {}

  record BenchmarkReport(
      String engine,
      String corpusVersion,
      String executedAt,
      List<BenchmarkDocumentResult> documents,
      List<String> failures) {}
}

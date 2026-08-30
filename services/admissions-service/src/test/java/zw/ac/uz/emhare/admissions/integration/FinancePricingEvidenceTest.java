package zw.ac.uz.emhare.admissions.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveApplicationFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeLine;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeStructure;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/**
 * Reject incomplete or out-of-scope Finance evidence before an application snapshots it. @author
 * Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class FinancePricingEvidenceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private FinanceHttpService http;
  private FinanceCatalogueClient client;
  private final UUID level = UUID.randomUUID();
  private final ResolveApplicationFeeStructureRequest request =
      new ResolveApplicationFeeStructureRequest("APPLICATION", NOW, level, "UG", "LOCAL");

  @BeforeEach
  void setUp() {
    client = new FinanceCatalogueClient(http);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "response",
        "id",
        "code",
        "blankCode",
        "name",
        "blankName",
        "context",
        "status",
        "version",
        "effectiveFrom",
        "currency",
        "invalidCurrency",
        "lines",
        "emptyLines"
      })
  void applicationPricingRequiresCompleteActiveEffectiveHeaderEvidence(String invalid) {
    when(http.resolveApplicationFeeStructure("Bearer applicant", request))
        .thenReturn(invalid.equals("response") ? null : pricing(invalid, List.of(line("valid"))));
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incomplete application-fee pricing evidence");
  }

  @ParameterizedTest
  @ValueSource(strings = {"rule", "amount", "zero", "negative", "currency", "rating", "status"})
  void eachFeeLineMustBeApprovedRatedPositiveAndInTheQuotedCurrency(String invalid) {
    when(http.resolveApplicationFeeStructure("Bearer applicant", request))
        .thenReturn(pricing("valid", List.of(line("valid"), line(invalid))));
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incomplete or unrated application-fee line evidence");
  }

  @ParameterizedTest
  @ValueSource(strings = {"future", "expired"})
  void applicationFeeEffectivePeriodIsStartInclusiveAndEndExclusive(String invalid) {
    when(http.resolveApplicationFeeStructure("Bearer applicant", request))
        .thenReturn(pricing(invalid, List.of(line("valid"))));
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside its effective period");
  }

  @ParameterizedTest
  @ValueSource(strings = {"missingLevel", "wrongLevel"})
  void mismatchedProgrammeLevelCannotLeakPricingFromAnotherRoute(String invalid) {
    when(http.resolveApplicationFeeStructure("Bearer applicant", request))
        .thenReturn(pricing(invalid, List.of(line("valid"))));
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("different programme level");
  }

  @ParameterizedTest
  @ValueSource(strings = {"valid", "codeFallback", "differentIdMatchingCode", "bounded"})
  void matchingIdOrCompatibleCaseInsensitiveLevelCodePreservesTheFinanceQuotation(String variant) {
    ResolvedApplicationFeeStructure quotation =
        pricing(variant, List.of(line("valid"), line("valid")));
    when(http.resolveApplicationFeeStructure("Bearer applicant", request)).thenReturn(quotation);
    ResolvedApplicationFeeStructure actual =
        client.resolveApplicationFeeStructure("Bearer applicant", request);
    assertThat(actual).isSameAs(quotation);
    assertThat(actual.totalTransactionAmount()).isEqualByComparingTo("40.00");
    assertThat(actual.transactionCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void knownDependencyAndBusinessRejectionsArePreservedButUnknownTransportFailureFailsClosed() {
    ServiceDependencyUnavailableException unavailable =
        new ServiceDependencyUnavailableException("Finance offline", null);
    when(http.resolveApplicationFeeStructure("Bearer applicant", request)).thenThrow(unavailable);
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isSameAs(unavailable);
    IllegalStateException business =
        new IllegalStateException("No active fee structure matches this billing context.");
    doThrow(business).when(http).resolveApplicationFeeStructure("Bearer applicant", request);
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isSameAs(business);
    assertThat(FinanceCatalogueClient.isMissingAcademicFeeStructure(business)).isTrue();
    assertThat(
            FinanceCatalogueClient.isMissingAcademicFeeStructure(
                new IllegalStateException("Other problem")))
        .isFalse();
    doThrow(new IllegalArgumentException("Connection unavailable"))
        .when(http)
        .resolveApplicationFeeStructure("Bearer applicant", request);
    assertThatThrownBy(() -> client.resolveApplicationFeeStructure("Bearer applicant", request))
        .isInstanceOf(ServiceDependencyUnavailableException.class)
        .hasMessageContaining("pricing cannot be safely resolved");
  }

  private ResolvedApplicationFeeStructure pricing(
      String invalid, List<ResolvedApplicationFeeLine> lines) {
    return new ResolvedApplicationFeeStructure(
        invalid.equals("id") ? null : UUID.randomUUID(),
        invalid.equals("code") ? null : invalid.equals("blankCode") ? " " : "APP-UG",
        invalid.equals("name") ? null : invalid.equals("blankName") ? " " : "Application fee",
        invalid.equals("context") ? "ACADEMIC" : "APPLICATION",
        invalid.equals("status") ? "DRAFT" : "ACTIVE",
        invalid.equals("version") ? -1 : 1,
        invalid.equals("currency") ? null : invalid.equals("invalidCurrency") ? "usd" : "USD",
        invalid.equals("effectiveFrom")
            ? null
            : invalid.equals("future") ? NOW.plusSeconds(1) : NOW,
        invalid.equals("expired") ? NOW : invalid.equals("bounded") ? NOW.plusSeconds(1) : null,
        "LOCAL",
        invalid.equals("codeFallback") || invalid.equals("missingLevel")
            ? null
            : invalid.equals("wrongLevel") || invalid.equals("differentIdMatchingCode")
                ? UUID.randomUUID()
                : level,
        invalid.equals("missingLevel") ? null : invalid.equals("wrongLevel") ? "PG" : "ug",
        invalid.equals("lines") ? null : invalid.equals("emptyLines") ? List.of() : lines);
  }

  private ResolvedApplicationFeeLine line(String invalid) {
    return new ResolvedApplicationFeeLine(
        invalid.equals("rule") ? null : UUID.randomUUID(),
        1,
        "APPLICATION",
        invalid.equals("amount")
            ? null
            : invalid.equals("zero")
                ? BigDecimal.ZERO
                : invalid.equals("negative") ? BigDecimal.ONE.negate() : new BigDecimal("20.00"),
        invalid.equals("currency") ? "ZWG" : "USD",
        invalid.equals("rating") ? "UNRATED" : "RATED",
        invalid.equals("status") ? "DRAFT" : "APPROVED");
  }
}

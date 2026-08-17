package zw.ac.uz.emhare.admissions.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveApplicationFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeLine;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeStructure;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;

/**
 * @author Tinashe K
 */
class FinanceCatalogueClientTest {
  private static final String AUTHORIZATION = "Bearer applicant-token";
  private static final Instant EFFECTIVE_AT = Instant.parse("2027-01-15T10:00:00Z");

  @Test
  void resolveApplicationFeeStructure_shouldReturnFinanceOwnedScopedPricing() {
    FinanceHttpService httpService = mock(FinanceHttpService.class);
    FinanceCatalogueClient client = new FinanceCatalogueClient(httpService);
    UUID structureId = UUID.randomUUID();
    UUID programmeLevelId = UUID.randomUUID();
    ResolveApplicationFeeStructureRequest request =
        new ResolveApplicationFeeStructureRequest(
            "APPLICATION", EFFECTIVE_AT, programmeLevelId, "UG", "LOCAL");
    ResolvedApplicationFeeStructure response =
        new ResolvedApplicationFeeStructure(
            structureId,
            "APP-UG-LOCAL",
            "Local undergraduate application",
            "APPLICATION",
            "ACTIVE",
            4,
            "USD",
            EFFECTIVE_AT.minusSeconds(86400),
            null,
            "LOCAL",
            programmeLevelId,
            "UG",
            List.of(
                new ResolvedApplicationFeeLine(
                    UUID.randomUUID(),
                    1,
                    "APPLICATION",
                    new BigDecimal("20.00"),
                    "USD",
                    "RATED",
                    "APPROVED"),
                new ResolvedApplicationFeeLine(
                    UUID.randomUUID(),
                    2,
                    "PROCESSING",
                    new BigDecimal("5.00"),
                    "USD",
                    "RATED",
                    "APPROVED")));
    when(httpService.resolveApplicationFeeStructure(AUTHORIZATION, request)).thenReturn(response);

    ResolvedApplicationFeeStructure resolved =
        client.resolveApplicationFeeStructure(AUTHORIZATION, request);

    assertEquals(structureId, resolved.id());
    assertEquals(new BigDecimal("25.00"), resolved.totalTransactionAmount());
    verify(httpService).resolveApplicationFeeStructure(AUTHORIZATION, request);
  }

  @Test
  void resolveApplicationFeeStructure_shouldRejectIncompleteFinanceEvidence() {
    FinanceHttpService httpService = mock(FinanceHttpService.class);
    FinanceCatalogueClient client = new FinanceCatalogueClient(httpService);
    ResolveApplicationFeeStructureRequest request =
        new ResolveApplicationFeeStructureRequest(
            "APPLICATION", EFFECTIVE_AT, UUID.randomUUID(), "UG", "LOCAL");
    when(httpService.resolveApplicationFeeStructure(AUTHORIZATION, request))
        .thenReturn(
            new ResolvedApplicationFeeStructure(
                UUID.randomUUID(),
                "APP-UG",
                "Application",
                "APPLICATION",
                "ACTIVE",
                1,
                "USD",
                EFFECTIVE_AT.minusSeconds(1),
                null,
                "LOCAL",
                request.programmeLevelId(),
                "UG",
                List.of(
                    new ResolvedApplicationFeeLine(
                        UUID.randomUUID(),
                        1,
                        "APPLICATION",
                        new BigDecimal("25.00"),
                        "USD",
                        "UNRATED",
                        "PENDING_RATE"))));

    assertThrows(
        IllegalStateException.class,
        () -> client.resolveApplicationFeeStructure(AUTHORIZATION, request));
  }
}

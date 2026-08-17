package zw.ac.uz.emhare.admissions.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveApplicationFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;

/**
 * Verifies the Admissions consumer against Finance's actual application-fee JSON shape. @author
 * Tinashe K
 */
class FinanceApplicationFeeConsumerContractTest {

  private static final UUID PROGRAMME_LEVEL_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");

  private MockRestServiceServer server;
  private FinanceCatalogueClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.baseUrl("http://finance.test").build();
    FinanceHttpService httpService =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(FinanceHttpService.class);
    client = new FinanceCatalogueClient(httpService);
  }

  @AfterEach
  void verifyContract() {
    server.verify();
  }

  @Test
  void sendsScopedContextAndConsumesCompleteFinanceSnapshot() {
    server
        .expect(requestTo("http://finance.test/api/finance/fee-structures/resolve"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer applicant-jwt"))
        .andExpect(
            content()
                .json(
                    """
                        {
                          "feeContext": "APPLICATION",
                          "effectiveAt": "2027-01-15T10:00:00Z",
                          "programmeLevelId": "10000000-0000-0000-0000-000000000001",
                          "programmeLevelCode": "UNDERGRADUATE",
                          "applicantCategoryCode": "LOCAL"
                        }
                        """))
        .andRespond(withSuccess(providerResponse(), MediaType.APPLICATION_JSON));

    var response =
        client.resolveApplicationFeeStructure(
            "Bearer applicant-jwt",
            new ResolveApplicationFeeStructureRequest(
                "APPLICATION",
                Instant.parse("2027-01-15T10:00:00Z"),
                PROGRAMME_LEVEL_ID,
                "UNDERGRADUATE",
                "LOCAL"));

    assertEquals("UG_LOCAL_APPLICATION", response.code());
    assertEquals(4, response.version());
    assertEquals(new BigDecimal("25.00"), response.totalTransactionAmount());
    assertEquals("RATED", response.lines().getFirst().ratingStatus());
  }

  private String providerResponse() {
    return """
                {
                  "id": "20000000-0000-0000-0000-000000000002",
                  "code": "UG_LOCAL_APPLICATION",
                  "name": "Undergraduate local application",
                  "feeContext": "APPLICATION",
                  "scopeType": "PROGRAMME_LEVEL",
                  "scopeReferenceId": "10000000-0000-0000-0000-000000000001",
                  "scopeReferenceCode": "UNDERGRADUATE",
                  "programmeLevelId": "10000000-0000-0000-0000-000000000001",
                  "programmeLevelCode": "UNDERGRADUATE",
                  "programmeLevelName": "Undergraduate",
                  "applicantCategoryCode": "LOCAL",
                  "transactionCurrencyCode": "USD",
                  "effectiveFrom": "2027-01-01T00:00:00Z",
                  "effectiveUntil": null,
                  "status": "ACTIVE",
                  "version": 4,
                  "lines": [{
                    "feeRuleId": "30000000-0000-0000-0000-000000000003",
                    "lineNumber": 1,
                    "feeCatalogueId": "40000000-0000-0000-0000-000000000004",
                    "feeCode": "APPLICATION_FEE",
                    "feeName": "Application fee",
                    "description": "Governed application fee",
                    "transactionAmount": 25.00,
                    "transactionCurrencyCode": "USD",
                    "baseCurrencyCode": "USD",
                    "exchangeRateId": null,
                    "exchangeRateToBase": 1,
                    "baseAmount": 25.00,
                    "ratingStatus": "RATED",
                    "status": "APPROVED"
                  }]
                }
                """;
  }
}

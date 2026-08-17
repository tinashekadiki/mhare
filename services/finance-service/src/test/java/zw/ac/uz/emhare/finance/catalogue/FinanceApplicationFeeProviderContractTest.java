package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.finance.catalogue.api.controller.FinanceFeeStructureController;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.ResolveStructure;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureLineSummary;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureSummary;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRule;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructure;

/**
 * Verifies the Finance provider's real HTTP JSON boundary consumed by Admissions. @author Tinashe K
 */
class FinanceApplicationFeeProviderContractTest {

  private static final UUID PROGRAMME_LEVEL_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

  @Test
  void resolvesApplicationFeeContextAndReturnsImmutablePricingEvidence() throws Exception {
    GovernedFinanceFeeStructureService service = mock(GovernedFinanceFeeStructureService.class);
    when(service.resolve(any(ResolveStructure.class))).thenReturn(structureSummary());
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(
                new FinanceFeeStructureController(service, mock(EmhareCurrentUserResolver.class)))
            .build();

    mockMvc
        .perform(
            post("/api/finance/fee-structures/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "feeContext": "APPLICATION",
                                  "effectiveAt": "2027-01-15T10:00:00Z",
                                  "programmeLevelId": "10000000-0000-0000-0000-000000000001",
                                  "programmeLevelCode": "UNDERGRADUATE",
                                  "applicantCategoryCode": "LOCAL"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(STRUCTURE_ID.toString()))
        .andExpect(jsonPath("$.feeContext").value("APPLICATION"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value(4))
        .andExpect(jsonPath("$.transactionCurrencyCode").value("USD"))
        .andExpect(jsonPath("$.lines[0].feeRuleId").value(RULE_ID.toString()))
        .andExpect(jsonPath("$.lines[0].ratingStatus").value("RATED"))
        .andExpect(jsonPath("$.lines[0].status").value("APPROVED"));

    ArgumentCaptor<ResolveStructure> request = ArgumentCaptor.forClass(ResolveStructure.class);
    org.mockito.Mockito.verify(service).resolve(request.capture());
    assertEquals(FinanceFeeStructure.FeeContext.APPLICATION, request.getValue().feeContext());
    assertEquals(PROGRAMME_LEVEL_ID, request.getValue().programmeLevelId());
    assertEquals("LOCAL", request.getValue().applicantCategoryCode());
  }

  private StructureSummary structureSummary() {
    Instant effectiveFrom = Instant.parse("2027-01-01T00:00:00Z");
    StructureLineSummary line =
        new StructureLineSummary(
            RULE_ID,
            1,
            UUID.fromString("40000000-0000-0000-0000-000000000004"),
            "APPLICATION_FEE",
            "Application fee",
            "Governed application fee",
            FinanceFeeCatalogue.ChargeType.APPLICATION,
            "1100",
            "4100",
            null,
            new BigDecimal("25.00"),
            "USD",
            "USD",
            null,
            BigDecimal.ONE,
            new BigDecimal("25.00"),
            FinanceFeeRule.RatingStatus.RATED,
            FinanceFeeRule.Status.APPROVED);
    return new StructureSummary(
        STRUCTURE_ID,
        "UG_LOCAL_APPLICATION",
        "Undergraduate local application",
        "Finance-owned application pricing",
        FinanceFeeStructure.FeeContext.APPLICATION,
        FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL,
        PROGRAMME_LEVEL_ID,
        "UNDERGRADUATE",
        "Undergraduate",
        PROGRAMME_LEVEL_ID,
        "UNDERGRADUATE",
        "Undergraduate",
        null,
        null,
        null,
        null,
        "LOCAL",
        "USD",
        effectiveFrom,
        null,
        FinanceFeeStructure.Status.ACTIVE,
        UUID.randomUUID(),
        UUID.randomUUID(),
        effectiveFrom,
        4,
        List.of(line),
        List.of(),
        null);
  }
}

package zw.ac.uz.emhare.finance.catalogue;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;

/** @author Tinashe K */
class FinanceFeeGovernanceTest {
    @Test void requiresIndependentCatalogueAndPricingApprovals() {
        UUID preparer=UUID.randomUUID();UUID activator=UUID.randomUUID();UUID priceApprover=UUID.randomUUID();Instant now=Instant.now();
        FinanceFeeCatalogue catalogue=new FinanceFeeCatalogue("TUITION","Programme tuition",null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,"1100-AR","4100-TUITION",null,preparer);
        assertThrows(IllegalStateException.class,()->catalogue.activate(preparer,now,"Self activation",0));
        catalogue.activate(activator,now,"Chart accounts and charge definition independently checked.",0);
        FinanceFeeRule rule=new FinanceFeeRule(catalogue,1,"USD",new BigDecimal("1250.00"),null,
                new BigDecimal("1250.00"),now,now.plusSeconds(86400),preparer);
        assertThrows(IllegalStateException.class,()->rule.approve(preparer,now,"Self approval","PROGRAMME:BCOM",0));
        rule.approve(priceApprover,now,"Price and effective window independently checked.","PROGRAMME:BCOM",0);
        assertEquals(FinanceFeeRule.Status.APPROVED,rule.getStatus());
        assertEquals(new BigDecimal("1250.00"),rule.getBaseAmount());
    }

    @Test void keepsForeignPricePendingUntilEffectiveRateEvidenceExists() {
        UUID preparer=UUID.randomUUID();Instant now=Instant.now();FinanceFeeCatalogue catalogue=new FinanceFeeCatalogue(
                "ZWG-MODULE","ZWG Module charge",null,FinanceFeeCatalogue.ChargeType.MODULE,"1100-AR","4200-MODULE",null,preparer);
        FinanceFeeRule rule=new FinanceFeeRule(catalogue,1,"ZWG",new BigDecimal("2500.00"),null,null,now,null,preparer);
        assertEquals(FinanceFeeRule.Status.PENDING_RATE,rule.getStatus());
        assertThrows(IllegalStateException.class,()->rule.approve(UUID.randomUUID(),now,"Premature approval","MODULE:ACC101",0));
        rule.applyRate(mock(ExchangeRate.class),new BigDecimal("92.59"),0);
        assertEquals(FinanceFeeRule.Status.DRAFT,rule.getStatus());
        assertEquals(FinanceFeeRule.RatingStatus.RATED,rule.getRatingStatus());
    }
}

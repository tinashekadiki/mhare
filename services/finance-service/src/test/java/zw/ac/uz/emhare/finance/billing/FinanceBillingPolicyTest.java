package zw.ac.uz.emhare.finance.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeCatalogue;

/** @author Tinashe K */
class FinanceBillingPolicyTest {
    @Test void requiresIndependentActivationAndPreservesVersionedQuantityPolicy(){UUID preparer=UUID.randomUUID();Instant now=Instant.now();FinanceBillingPolicy policy=new FinanceBillingPolicy("REG-TUITION",2,"Registration tuition","student-records.registration-confirmed.v1",mock(FinanceFeeCatalogue.class),FinanceBillingPolicy.LineBasis.REGISTRATION,FinanceBillingPolicy.QuantityBasis.FIXED,new BigDecimal("1.0000"),now,now.plusSeconds(86400),preparer);assertThrows(IllegalStateException.class,()->policy.activate(preparer,now,"Self approval",0));policy.activate(UUID.randomUUID(),now,"Independent source and charge approval.",0);assertEquals(FinanceBillingPolicy.Status.ACTIVE,policy.getStatus());assertEquals(new BigDecimal("1.0000"),policy.quantityForModule(null));}
    @Test void allowsModuleCreditQuantityOnlyForRegisteredModuleLines(){Instant now=Instant.now();FinanceBillingPolicy policy=new FinanceBillingPolicy("MODULE-CREDIT",1,"Per-credit Module fee","student-records.registration-confirmed.v1",mock(FinanceFeeCatalogue.class),FinanceBillingPolicy.LineBasis.REGISTERED_MODULE,FinanceBillingPolicy.QuantityBasis.MODULE_CREDIT_VALUE,null,now,null,UUID.randomUUID());assertEquals(new BigDecimal("15.0000"),policy.quantityForModule(new BigDecimal("15")));assertThrows(IllegalArgumentException.class,()->new FinanceBillingPolicy("INVALID",1,"Invalid","event",mock(FinanceFeeCatalogue.class),FinanceBillingPolicy.LineBasis.REGISTRATION,FinanceBillingPolicy.QuantityBasis.MODULE_CREDIT_VALUE,null,now,null,UUID.randomUUID()));}
}

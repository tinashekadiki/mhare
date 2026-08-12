package zw.ac.uz.emhare.admissions.integration.http;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ApplicationFeeStructurePricing;

/** Consumer-owned Admissions view of Finance fee pricing. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface FinanceHttpService {

    @GetExchange("/api/finance/fee-structures/{feeStructureId}/pricing")
    ApplicationFeeStructurePricing getApplicationFeeStructurePricing(
            @PathVariable("feeStructureId") UUID feeStructureId);
}

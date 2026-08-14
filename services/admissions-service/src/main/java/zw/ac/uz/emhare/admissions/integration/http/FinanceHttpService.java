package zw.ac.uz.emhare.admissions.integration.http;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ApplicationFeeStructurePricing;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveAcademicFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedAcademicFeeStructure;

/** Consumer-owned Admissions view of Finance fee pricing. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface FinanceHttpService {

    @GetExchange("/api/finance/fee-structures/{feeStructureId}/pricing")
    ApplicationFeeStructurePricing getApplicationFeeStructurePricing(
            @PathVariable("feeStructureId") UUID feeStructureId);

    @PostExchange("/api/finance/fee-structures/resolve")
    ResolvedAcademicFeeStructure resolveAcademicFeeStructure(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ResolveAcademicFeeStructureRequest request);
}

package zw.ac.uz.emhare.admissions.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveAcademicFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveApplicationFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedAcademicFeeStructure;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeStructure;

/** Consumer-owned Admissions view of Finance fee pricing. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface FinanceHttpService {

  @PostExchange("/api/finance/fee-structures/resolve")
  ResolvedAcademicFeeStructure resolveAcademicFeeStructure(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody ResolveAcademicFeeStructureRequest request);

  @PostExchange("/api/finance/fee-structures/resolve")
  ResolvedApplicationFeeStructure resolveApplicationFeeStructure(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestBody ResolveApplicationFeeStructureRequest request);
}

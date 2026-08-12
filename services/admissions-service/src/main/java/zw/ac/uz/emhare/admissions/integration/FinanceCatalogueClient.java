package zw.ac.uz.emhare.admissions.integration;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;

/** @author Tinashe K */
@Component
public class FinanceCatalogueClient {

    private final FinanceHttpService financeHttpService;

    public FinanceCatalogueClient(FinanceHttpService financeHttpService) {
        this.financeHttpService = financeHttpService;
    }

    public ApplicationFeeStructurePricing getApplicationFeeStructurePricing(UUID feeStructureId) {
        try {
            ApplicationFeeStructurePricing pricing = financeHttpService.getApplicationFeeStructurePricing(feeStructureId);
            if (pricing == null) {
                throw new ServiceDependencyUnavailableException("Finance returned an empty fee structure pricing response.", null);
            }
            return pricing;
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String rejectionDetail = pricingRejectionDetail(exception);
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new IllegalArgumentException(rejectionDetail, exception);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private ServiceDependencyUnavailableException unavailable(Throwable exception) {
        return new ServiceDependencyUnavailableException(
                "Finance is unavailable, so the application fee cannot be safely resolved.", exception);
    }

    private String pricingRejectionDetail(RestClientResponseException exception) {
        try {
            ProblemDetail problemDetail = exception.getResponseBodyAs(ProblemDetail.class);
            if (problemDetail != null && problemDetail.getDetail() != null && !problemDetail.getDetail().isBlank()) {
                return problemDetail.getDetail();
            }
        } catch (RestClientException ignored) {
            // Use the stable local message when Finance returns a non-standard error body.
        }
        return "Finance rejected the fee structure pricing request.";
    }

    public record ApplicationFeeStructurePricing(
            UUID id, String code, String name, String status,
            String transactionCurrencyCode, BigDecimal totalTransactionAmount) {
    }
}

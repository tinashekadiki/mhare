package zw.ac.uz.emhare.admissions.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/**
 * @author Tinashe K
 */
@Component
public class FinanceCatalogueClient {

  private static final String NO_ACTIVE_FEE_STRUCTURE_DETAIL =
      "No active fee structure matches this billing context.";

  private final FinanceHttpService financeHttpService;

  public FinanceCatalogueClient(FinanceHttpService financeHttpService) {
    this.financeHttpService = financeHttpService;
  }

  public ResolvedAcademicFeeStructure resolveAcademicFeeStructure(
      String authorization, ResolveAcademicFeeStructureRequest request) {
    try {
      ResolvedAcademicFeeStructure pricing =
          financeHttpService.resolveAcademicFeeStructure(authorization, request);
      if (pricing == null || pricing.lines() == null || pricing.lines().isEmpty()) {
        throw new ServiceDependencyUnavailableException(
            "Finance returned an incomplete academic fee schedule.", null);
      }
      return pricing;
    } catch (ServiceDependencyUnavailableException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      RestClientResponseException responseException = responseException(exception);
      if (responseException != null) throw academicPricingRejection(responseException);
      throw unavailable(exception);
    }
  }

  public ResolvedApplicationFeeStructure resolveApplicationFeeStructure(
      String authorization, ResolveApplicationFeeStructureRequest request) {
    try {
      ResolvedApplicationFeeStructure pricing =
          financeHttpService.resolveApplicationFeeStructure(authorization, request);
      validateApplicationFeeStructure(pricing, request);
      return pricing;
    } catch (ServiceDependencyUnavailableException exception) {
      throw exception;
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      RestClientResponseException responseException = responseException(exception);
      if (responseException != null) throw academicPricingRejection(responseException);
      throw unavailable(exception);
    }
  }

  public static boolean isMissingAcademicFeeStructure(IllegalStateException exception) {
    return NO_ACTIVE_FEE_STRUCTURE_DETAIL.equals(exception.getMessage());
  }

  private RuntimeException academicPricingRejection(RestClientResponseException exception) {
    String detail = pricingRejectionDetail(exception);
    if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
        || exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
      return new IllegalStateException(detail, exception);
    }
    return unavailable(exception);
  }

  private RestClientResponseException responseException(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof RestClientResponseException responseException)
        return responseException;
      if (current == current.getCause()) return null;
      current = current.getCause();
    }
    return null;
  }

  private ServiceDependencyUnavailableException unavailable(Throwable exception) {
    return new ServiceDependencyUnavailableException(
        "Finance is unavailable, so pricing cannot be safely resolved.", exception);
  }

  private String pricingRejectionDetail(RestClientResponseException exception) {
    try {
      ProblemDetail problemDetail = exception.getResponseBodyAs(ProblemDetail.class);
      if (problemDetail != null
          && problemDetail.getDetail() != null
          && !problemDetail.getDetail().isBlank()) {
        return problemDetail.getDetail();
      }
    } catch (RestClientException ignored) {
      // Use the stable local message when Finance returns a non-standard error body.
    }
    return "Finance rejected the fee structure pricing request.";
  }

  private void validateApplicationFeeStructure(
      ResolvedApplicationFeeStructure pricing, ResolveApplicationFeeStructureRequest request) {
    if (pricing == null
        || pricing.id() == null
        || pricing.code() == null
        || pricing.code().isBlank()
        || pricing.name() == null
        || pricing.name().isBlank()
        || !"APPLICATION".equals(pricing.feeContext())
        || !"ACTIVE".equals(pricing.status())
        || pricing.version() < 0
        || pricing.effectiveFrom() == null
        || pricing.transactionCurrencyCode() == null
        || !pricing.transactionCurrencyCode().matches("^[A-Z]{3}$")
        || pricing.lines() == null
        || pricing.lines().isEmpty()) {
      throw new IllegalStateException(
          "Finance returned incomplete application-fee pricing evidence.");
    }
    if (request.effectiveAt().isBefore(pricing.effectiveFrom())
        || pricing.effectiveUntil() != null
            && !request.effectiveAt().isBefore(pricing.effectiveUntil())) {
      throw new IllegalStateException(
          "Finance returned an application fee outside its effective period.");
    }
    boolean programmeLevelMatches =
        pricing.programmeLevelId() != null
            && pricing.programmeLevelId().equals(request.programmeLevelId());
    if (!programmeLevelMatches
        && (pricing.programmeLevelCode() == null
            || !pricing.programmeLevelCode().equalsIgnoreCase(request.programmeLevelCode()))) {
      throw new IllegalStateException(
          "Finance returned an application fee for a different programme level.");
    }
    boolean invalidLine =
        pricing.lines().stream()
            .anyMatch(
                line ->
                    line.feeRuleId() == null
                        || line.transactionAmount() == null
                        || line.transactionAmount().signum() <= 0
                        || !pricing.transactionCurrencyCode().equals(line.transactionCurrencyCode())
                        || !"RATED".equals(line.ratingStatus())
                        || !"APPROVED".equals(line.status()));
    if (invalidLine) {
      throw new IllegalStateException(
          "Finance returned incomplete or unrated application-fee line evidence.");
    }
  }

  public record ResolveAcademicFeeStructureRequest(
      String feeContext,
      Instant effectiveAt,
      UUID academicPeriodId,
      UUID programmeId,
      List<AcademicUnitPathItem> academicUnitPath,
      UUID programmeLevelId,
      String programmeLevelCode,
      UUID programmeTypeId,
      String applicantCategoryCode,
      Integer programmePeriodNumber) {}

  public record ResolveApplicationFeeStructureRequest(
      String feeContext,
      Instant effectiveAt,
      UUID programmeLevelId,
      String programmeLevelCode,
      String applicantCategoryCode) {}

  public record AcademicUnitPathItem(UUID id, String code, String name) {}

  public record ResolvedAcademicFeeStructure(
      UUID id,
      String code,
      String name,
      String feeContext,
      String status,
      long version,
      String transactionCurrencyCode,
      List<ResolvedAcademicFeeLine> lines) {}

  public record ResolvedAcademicFeeLine(
      UUID feeRuleId,
      int lineNumber,
      UUID feeCatalogueId,
      String feeCode,
      String feeName,
      String description,
      BigDecimal transactionAmount,
      String transactionCurrencyCode,
      String baseCurrencyCode,
      UUID exchangeRateId,
      BigDecimal exchangeRateToBase,
      BigDecimal baseAmount,
      String ratingStatus,
      String status) {}

  public record ResolvedApplicationFeeStructure(
      UUID id,
      String code,
      String name,
      String feeContext,
      String status,
      long version,
      String transactionCurrencyCode,
      Instant effectiveFrom,
      Instant effectiveUntil,
      String applicantCategoryCode,
      UUID programmeLevelId,
      String programmeLevelCode,
      List<ResolvedApplicationFeeLine> lines) {
    public BigDecimal totalTransactionAmount() {
      return lines.stream()
          .map(ResolvedApplicationFeeLine::transactionAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
  }

  public record ResolvedApplicationFeeLine(
      UUID feeRuleId,
      int lineNumber,
      String feeCode,
      BigDecimal transactionAmount,
      String transactionCurrencyCode,
      String ratingStatus,
      String status) {}
}

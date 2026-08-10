package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApplicationPaymentSummary(
        UUID financePaymentReferenceId,
        String reference,
        BigDecimal amountDue,
        String currencyCode,
        String baseCurrencyCode,
        BigDecimal baseAmountDue,
        String ratingStatus,
        String status,
        boolean requiredForSubmission,
        boolean workflowCleared,
        Instant paidAt) {

    static ApplicationPaymentSummary from(ApplicationPaymentReference paymentReference) {
        boolean workflowCleared = paymentReference.getStatus() == PaymentReferenceStatus.PAID
                && "RATED".equals(paymentReference.getRatingStatus());
        return new ApplicationPaymentSummary(
                paymentReference.getFinancePaymentReferenceId(),
                paymentReference.getReference(),
                paymentReference.getAmountDue(),
                paymentReference.getCurrencyCode(),
                paymentReference.getBaseCurrencyCode(),
                paymentReference.getBaseAmountDue(),
                paymentReference.getRatingStatus(),
                paymentReference.getStatus().name(),
                paymentReference.isRequiredForSubmission(),
                workflowCleared,
                paymentReference.getPaidAt());
    }
}

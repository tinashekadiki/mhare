package zw.ac.uz.emhare.finance.payment;

import zw.ac.uz.emhare.finance.payment.domain.model.ApplicationPaymentReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.finance.payment.domain.model.MoneyRatingStatus;
import zw.ac.uz.emhare.finance.payment.domain.model.PaymentReferenceStatus;

public record ApplicationPaymentReferenceSummary(
        UUID id,
        UUID applicationId,
        String reference,
        BigDecimal amountDue,
        String currencyCode,
        String baseCurrencyCode,
        BigDecimal baseAmountDue,
        String ratingStatus,
        String status,
        boolean requiredForSubmission,
        boolean workflowCleared,
        Instant expiresAt,
        Instant paidAt) {

    static ApplicationPaymentReferenceSummary from(ApplicationPaymentReference paymentReference) {
        return new ApplicationPaymentReferenceSummary(
                paymentReference.getId(),
                paymentReference.getSourceApplicationId(),
                paymentReference.getReference(),
                paymentReference.getAmountDue(),
                paymentReference.getCurrencyCode(),
                paymentReference.getBaseCurrencyCode(),
                paymentReference.getBaseAmountDue(),
                paymentReference.getRatingStatus().name(),
                paymentReference.getStatus().name(),
                paymentReference.isRequiredForSubmission(),
                paymentReference.getStatus() == PaymentReferenceStatus.PAID
                        && paymentReference.getRatingStatus() == MoneyRatingStatus.RATED,
                paymentReference.getExpiresAt(),
                paymentReference.getPaidAt());
    }
}

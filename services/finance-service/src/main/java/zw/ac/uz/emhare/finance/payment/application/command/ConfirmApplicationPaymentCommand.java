package zw.ac.uz.emhare.finance.payment.application.command;

import zw.ac.uz.emhare.finance.payment.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConfirmApplicationPaymentCommand(
        UUID sourceApplicationId,
        String providerCode,
        String providerTransactionReference,
        BigDecimal amount,
        String currencyCode,
        Instant paidAt,
        String providerEventFingerprint) {
}

package zw.ac.uz.emhare.finance.payment.application.command;

import zw.ac.uz.emhare.finance.payment.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateApplicationPaymentReferenceCommand(
        UUID sourceApplicationId,
        UUID applicantUserId,
        UUID applicantKeycloakUserId,
        BigDecimal amountDue,
        String currencyCode,
        boolean requiredForSubmission) {
}

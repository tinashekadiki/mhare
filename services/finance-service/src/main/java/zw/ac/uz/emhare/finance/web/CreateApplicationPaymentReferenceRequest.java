package zw.ac.uz.emhare.finance.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateApplicationPaymentReferenceRequest(
        @NotNull UUID applicationId,
        @NotNull UUID applicantUserId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amountDue,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        boolean requiredForSubmission) {
}

package zw.ac.uz.emhare.finance.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConfirmReconciledApplicationPaymentRequest(
        @NotNull UUID applicationId,
        @NotBlank @Size(max = 50) String providerCode,
        @NotBlank @Size(max = 160) String providerTransactionReference,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @NotNull Instant paidAt,
        @NotBlank @Size(max = 128) String providerEventFingerprint) {
}

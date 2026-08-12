package zw.ac.uz.emhare.finance.payment.api.model;

import zw.ac.uz.emhare.finance.payment.*;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record ApplicationPaymentReferenceQueryRequest(
        @NotEmpty @Size(max = 500) Set<UUID> applicationIds) {
}

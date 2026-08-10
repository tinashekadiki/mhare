package zw.ac.uz.emhare.finance.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record ApplicationPaymentReferenceQueryRequest(
        @NotEmpty @Size(max = 500) Set<UUID> applicationIds) {
}

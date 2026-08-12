package zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model;

import zw.ac.uz.emhare.finance.payment.provider.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Server-verified status returned by the hosted payment gateway. @author Tinashe K */
public record CbzIveriLiteTransactionStatus(
        String merchantTrace,
        String providerStatusCode,
        String resultDescription,
        String transactionIndex,
        String bankReference,
        String merchantReference,
        BigDecimal amount,
        Instant paidAt) {

    public boolean approved() {
        return "0".equals(providerStatusCode);
    }

    public String providerTransactionReference() {
        if (transactionIndex != null && !transactionIndex.isBlank()) return transactionIndex;
        if (bankReference != null && !bankReference.isBlank()) return bankReference;
        return merchantReference;
    }
}

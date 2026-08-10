package zw.ac.uz.emhare.finance.payment.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @author Tinashe K */
@ConfigurationProperties("emhare.payments.cbz-iveri-lite")
public record CbzIveriLiteProperties(
        boolean enabled,
        String gatewayUrl,
        String applicationId,
        String sharedSecret,
        String transactionCurrencyCode,
        String successfulUrl,
        String failUrl,
        String tryLaterUrl,
        String errorUrl) {

    public boolean ready() {
        return enabled
                && hasText(gatewayUrl)
                && hasText(applicationId)
                && hasText(transactionCurrencyCode)
                && transactionCurrencyCode.trim().length() == 3
                && hasText(successfulUrl)
                && hasText(failUrl)
                && hasText(tryLaterUrl)
                && hasText(errorUrl);
    }

    public boolean transactionTokenEnabled() {
        return hasText(sharedSecret);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

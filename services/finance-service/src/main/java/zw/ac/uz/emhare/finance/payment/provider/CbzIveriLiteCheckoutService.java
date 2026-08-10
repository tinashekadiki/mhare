package zw.ac.uz.emhare.finance.payment.provider;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReference;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReferenceRepository;
import zw.ac.uz.emhare.finance.payment.provider.ApplicationPaymentCheckoutViews.HostedCheckout;
import zw.ac.uz.emhare.finance.payment.provider.ApplicationPaymentCheckoutViews.OnlinePayment;
import zw.ac.uz.emhare.finance.payment.provider.ApplicationPaymentCheckoutViews.PaymentOptions;

/** @author Tinashe K */
@Service
public class CbzIveriLiteCheckoutService {

    static final String PROVIDER_CODE = "CBZ_IVERI_LITE";
    static final String RESOURCE = "/Lite/Authorise.aspx";
    private static final Duration CHECKOUT_LIFETIME = Duration.ofMinutes(20);

    private final ApplicationPaymentReferenceRepository paymentReferenceRepository;
    private final ApplicationPaymentProviderAttemptRepository attemptRepository;
    private final CbzIveriLiteProperties properties;
    private final Clock clock;

    public CbzIveriLiteCheckoutService(
            ApplicationPaymentReferenceRepository paymentReferenceRepository,
            ApplicationPaymentProviderAttemptRepository attemptRepository,
            CbzIveriLiteProperties properties,
            Clock clock) {
        this.paymentReferenceRepository = paymentReferenceRepository;
        this.attemptRepository = attemptRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public PaymentOptions paymentOptions() {
        String message = properties.ready()
                ? "Pay the application fee securely by debit or credit card."
                : "Online card payment is not yet available.";
        return new PaymentOptions(true, new OnlinePayment(properties.ready(), message));
    }

    @Transactional
    public HostedCheckout startCheckout(UUID applicationId, UUID keycloakUserId, String emailAddress) {
        if (!properties.ready()) {
            throw new IllegalStateException("Online card payment is not configured.");
        }
        ApplicationPaymentReference paymentReference = paymentReferenceRepository
                .findBySourceApplicationIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Payment reference not found."));
        if (!paymentReference.isOwnedByKeycloakUser(keycloakUserId)) {
            throw new IllegalArgumentException("Payment reference not found.");
        }
        if (!"PENDING".equals(paymentReference.getStatusCode())) {
            throw new IllegalStateException("Only a pending application fee can start an online payment.");
        }
        String configuredCurrencyCode = properties.transactionCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (!configuredCurrencyCode.equals(paymentReference.getCurrencyCode())) {
            throw new IllegalStateException(
                    "Online card payment is not configured for this application fee currency.");
        }

        Instant initiatedAt = clock.instant();
        Instant expiresAt = initiatedAt.plus(CHECKOUT_LIFETIME);
        String merchantTrace = UUID.randomUUID().toString().replace("-", "");
        String returnNonce = UUID.randomUUID().toString().replace("-", "");
        String amountInMinorUnits = minorUnits(paymentReference.getAmountDue());
        String applicationIdValue = normalizeApplicationId(properties.applicationId());
        String normalizedEmailAddress = emailAddress.trim().toLowerCase(Locale.ROOT);
        ApplicationPaymentProviderAttempt attempt = attemptRepository.saveAndFlush(
                new ApplicationPaymentProviderAttempt(
                        paymentReference, PROVIDER_CODE, merchantTrace, paymentReference.getReference(),
                        sha256(returnNonce), paymentReference.getCurrencyCode(), paymentReference.getAmountDue(),
                        properties.gatewayUrl().trim(), expiresAt));

        Map<String, String> formParameters = new LinkedHashMap<>();
        formParameters.put("Lite_Merchant_ApplicationId", applicationIdValue);
        formParameters.put("Lite_Order_Amount", amountInMinorUnits);
        formParameters.put("Lite_Website_Successful_Url",
                returnUrl(properties.successfulUrl(), attempt.getId(), returnNonce));
        formParameters.put("Lite_Website_Fail_Url",
                returnUrl(properties.failUrl(), attempt.getId(), returnNonce));
        formParameters.put("Lite_Website_TryLater_Url",
                returnUrl(properties.tryLaterUrl(), attempt.getId(), returnNonce));
        formParameters.put("Lite_Website_Error_Url",
                returnUrl(properties.errorUrl(), attempt.getId(), returnNonce));
        formParameters.put("Lite_Order_LineItems_Product_1", "eMhare application fee");
        formParameters.put("Lite_Order_LineItems_Quantity_1", "1");
        formParameters.put("Lite_Order_LineItems_Amount_1", amountInMinorUnits);
        formParameters.put("Ecom_BillTo_Online_Email", normalizedEmailAddress);
        formParameters.put("Ecom_Payment_Card_Protocols", "IVERI");
        formParameters.put("Lite_ConsumerOrderID_PreFix", "EMH");
        formParameters.put("Ecom_ConsumerOrderID", consumerOrderId(paymentReference.getReference()));
        formParameters.put("Ecom_TransactionComplete", "False");
        formParameters.put("Lite_Merchant_Trace", merchantTrace);
        if (properties.transactionTokenEnabled()) {
            formParameters.put("Lite_Transaction_Token", transactionToken(
                    properties.sharedSecret(), initiatedAt.getEpochSecond(), applicationIdValue,
                    amountInMinorUnits, normalizedEmailAddress));
        }

        return new HostedCheckout(
                attempt.getId(), embeddedCheckoutUrl(properties.gatewayUrl()),
                returnMessageOrigin(properties.successfulUrl()), Map.copyOf(formParameters), expiresAt);
    }

    static String embeddedCheckoutUrl(String gatewayUrl) {
        URI gatewayUri = URI.create(gatewayUrl.trim());
        if (gatewayUri.getScheme() == null || gatewayUri.getRawAuthority() == null) {
            throw new IllegalArgumentException("The online payment gateway URL is invalid.");
        }
        return gatewayUri.getScheme() + "://" + gatewayUri.getRawAuthority() + "/Lite/LiteBox";
    }

    static String returnMessageOrigin(String returnUrl) {
        URI returnUri = URI.create(returnUrl.trim());
        if (returnUri.getScheme() == null || returnUri.getRawAuthority() == null) {
            throw new IllegalArgumentException("The online payment return URL is invalid.");
        }
        return returnUri.getScheme() + "://" + returnUri.getRawAuthority();
    }

    private static String returnUrl(String configuredUrl, UUID attemptId, String returnNonce) {
        String trimmedUrl = configuredUrl.trim();
        String separator = trimmedUrl.contains("?") ? "&" : "?";
        return trimmedUrl + separator + "attemptId=" + attemptId + "&nonce=" + returnNonce;
    }

    static String transactionToken(
            String sharedSecret,
            long epochSeconds,
            String applicationId,
            String amountInMinorUnits,
            String emailAddress) {
        String payload = sharedSecret + epochSeconds + RESOURCE + applicationId + amountInMinorUnits + emailAddress;
        return epochSeconds + ":" + sha256(payload);
    }

    private static String minorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).toBigIntegerExact().toString();
    }

    private static String normalizeApplicationId(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("{")) normalized = "{" + normalized;
        if (!normalized.endsWith("}")) normalized = normalized + "}";
        return normalized;
    }

    private static String consumerOrderId(String reference) {
        return reference.length() <= 20 ? reference : reference.substring(reference.length() - 20);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for payment security.", exception);
        }
    }
}

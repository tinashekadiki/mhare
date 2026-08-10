package zw.ac.uz.emhare.finance.payment.provider;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** @author Tinashe K */
public final class ApplicationPaymentCheckoutViews {
    private ApplicationPaymentCheckoutViews() {
    }

    public record PaymentOptions(
            boolean proofOfPaymentUploadAvailable,
            OnlinePayment onlinePayment) {
    }

    public record OnlinePayment(
            boolean available,
            String availabilityMessage) {
    }

    public record HostedCheckout(
            UUID attemptId,
            String embeddedCheckoutUrl,
            String returnMessageOrigin,
            Map<String, String> formParameters,
            Instant expiresAt) {
    }
}

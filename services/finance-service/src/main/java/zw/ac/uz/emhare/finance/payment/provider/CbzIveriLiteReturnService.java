package zw.ac.uz.emhare.finance.payment.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReferenceSummary;
import zw.ac.uz.emhare.finance.payment.ConfirmApplicationPaymentCommand;
import zw.ac.uz.emhare.finance.payment.FinanceApplicationPaymentService;

/** Validates and records the browser return from the contained card checkout. @author Tinashe K */
@Service
public class CbzIveriLiteReturnService {

    private final ApplicationPaymentProviderAttemptRepository attemptRepository;
    private final CbzIveriLiteTransactionStatusClient transactionStatusClient;
    private final FinanceApplicationPaymentService financeApplicationPaymentService;
    private final Clock clock;

    public CbzIveriLiteReturnService(
            ApplicationPaymentProviderAttemptRepository attemptRepository,
            CbzIveriLiteTransactionStatusClient transactionStatusClient,
            FinanceApplicationPaymentService financeApplicationPaymentService,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.transactionStatusClient = transactionStatusClient;
        this.financeApplicationPaymentService = financeApplicationPaymentService;
        this.clock = clock;
    }

    public Map<String, String> processReturn(
            UUID attemptId,
            String nonce,
            String outcome,
            MultiValueMap<String, String> parameters) {
        ApplicationPaymentProviderAttempt attempt = attemptRepository
                .findByIdAndDeletedAtIsNull(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt was not found."));
        requireNonce(attempt, nonce);
        String merchantTrace = first(parameters, "Lite_Merchant_Trace");
        if (!attempt.getMerchantTrace().equals(merchantTrace)) {
            throw new IllegalArgumentException("Payment return does not match the checkout attempt.");
        }

        CbzIveriLiteTransactionStatus verifiedStatus = transactionStatusClient.query(attempt.getMerchantTrace());
        validateVerifiedStatus(attempt, verifiedStatus);
        String normalizedOutcome = verifiedStatus.approved() ? "VERIFIED_SUCCESS" : "VERIFIED_FAILED";
        String providerTransactionReference = verifiedStatus.providerTransactionReference();
        attempt.recordProviderReturn(
                normalizedOutcome,
                providerTransactionReference,
                verifiedStatus.providerStatusCode(),
                verifiedStatus.resultDescription(),
                clock.instant());
        attemptRepository.saveAndFlush(attempt);
        if (verifiedStatus.approved()) {
            Instant paidAt = trustedPaidAt(verifiedStatus);
            financeApplicationPaymentService.confirmReconciledPayment(new ConfirmApplicationPaymentCommand(
                    attempt.getSourceApplicationId(),
                    attempt.getProviderCode(),
                    providerTransactionReference,
                    attempt.getTransactionAmount(),
                    attempt.getTransactionCurrencyCode(),
                    paidAt,
                    providerEventFingerprint(attempt, verifiedStatus, paidAt)));
        }

        Map<String, String> response = new LinkedHashMap<>();
        response.put("Lite_Payment_Card_Status", verifiedStatus.providerStatusCode());
        response.put("Lite_Merchant_Trace", attempt.getMerchantTrace());
        if (providerTransactionReference != null) {
            response.put("Lite_TransactionIndex", providerTransactionReference);
        }
        if (verifiedStatus.resultDescription() != null) {
            response.put("Lite_Result_Description", verifiedStatus.resultDescription());
        }
        return Map.copyOf(response);
    }

    public ApplicationPaymentReferenceSummary reconcileApplicantCheckout(
            UUID applicationId,
            UUID keycloakUserId,
            UUID attemptId) {
        ApplicationPaymentReferenceSummary currentPayment = financeApplicationPaymentService
                .findApplicantPaymentReference(applicationId, keycloakUserId);
        if (currentPayment.workflowCleared()) {
            return currentPayment;
        }

        ApplicationPaymentProviderAttempt attempt = attemptId == null
                ? attemptRepository
                        .findFirstBySourceApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                        .orElse(null)
                : attemptRepository.findByIdAndDeletedAtIsNull(attemptId).orElse(null);
        if (attempt == null) {
            return currentPayment;
        }
        if (!applicationId.equals(attempt.getSourceApplicationId())) {
            throw new IllegalArgumentException("Payment attempt was not found.");
        }

        CbzIveriLiteTransactionStatus verifiedStatus = transactionStatusClient.query(attempt.getMerchantTrace());
        validateVerifiedStatus(attempt, verifiedStatus);
        if (!verifiedStatus.approved()) {
            return currentPayment;
        }

        String providerTransactionReference = verifiedStatus.providerTransactionReference();
        attempt.recordProviderReturn(
                "CONFIRMED",
                providerTransactionReference,
                verifiedStatus.providerStatusCode(),
                verifiedStatus.resultDescription(),
                clock.instant());
        attemptRepository.saveAndFlush(attempt);
        Instant paidAt = trustedPaidAt(verifiedStatus);
        return financeApplicationPaymentService.confirmReconciledPayment(new ConfirmApplicationPaymentCommand(
                attempt.getSourceApplicationId(),
                attempt.getProviderCode(),
                providerTransactionReference,
                attempt.getTransactionAmount(),
                attempt.getTransactionCurrencyCode(),
                paidAt,
                providerEventFingerprint(attempt, verifiedStatus, paidAt)));
    }

    private Instant trustedPaidAt(CbzIveriLiteTransactionStatus verifiedStatus) {
        Instant providerPaidAt = verifiedStatus.paidAt();
        Instant confirmedAt = clock.instant();
        return providerPaidAt == null || providerPaidAt.isAfter(confirmedAt) ? confirmedAt : providerPaidAt;
    }

    private void validateVerifiedStatus(
            ApplicationPaymentProviderAttempt attempt,
            CbzIveriLiteTransactionStatus verifiedStatus) {
        if (verifiedStatus.amount() != null
                && verifiedStatus.amount().compareTo(attempt.getTransactionAmount()) != 0) {
            throw new IllegalStateException("The provider-confirmed amount does not match the application fee.");
        }
        if (verifiedStatus.merchantReference() != null
                && !attempt.getMerchantReference().equals(verifiedStatus.merchantReference())) {
            throw new IllegalStateException("The provider-confirmed reference does not match the application fee.");
        }
        if (verifiedStatus.approved()
                && (verifiedStatus.amount() == null
                    || verifiedStatus.providerTransactionReference() == null
                    || verifiedStatus.providerTransactionReference().isBlank()
                    || verifiedStatus.merchantReference() == null
                    || verifiedStatus.merchantReference().isBlank())) {
            throw new IllegalStateException("The approved provider transaction is missing settlement evidence.");
        }
    }

    private String providerEventFingerprint(
            ApplicationPaymentProviderAttempt attempt,
            CbzIveriLiteTransactionStatus verifiedStatus,
            Instant paidAt) {
        return CbzIveriLiteCheckoutService.sha256(String.join("|",
                attempt.getProviderCode(),
                attempt.getMerchantTrace(),
                verifiedStatus.providerTransactionReference(),
                attempt.getTransactionAmount().toPlainString(),
                attempt.getTransactionCurrencyCode(),
                paidAt.toString()));
    }

    private void requireNonce(ApplicationPaymentProviderAttempt attempt, String nonce) {
        if (nonce == null || nonce.isBlank()
                || !MessageDigest.isEqual(
                        attempt.getReturnNonceHash().getBytes(StandardCharsets.UTF_8),
                        CbzIveriLiteCheckoutService.sha256(nonce).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Payment return could not be verified.");
        }
    }

    private String first(MultiValueMap<String, String> parameters, String name) {
        String value = parameters.getFirst(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}

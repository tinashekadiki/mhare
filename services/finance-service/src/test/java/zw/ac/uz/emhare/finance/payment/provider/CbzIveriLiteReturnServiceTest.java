package zw.ac.uz.emhare.finance.payment.provider;

import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.ApplicationPaymentProviderAttemptRepository;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.ApplicationPaymentProviderAttempt;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.CbzIveriLiteTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import zw.ac.uz.emhare.finance.payment.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.finance.payment.FinanceApplicationPaymentService;

/** @author Tinashe K */
class CbzIveriLiteReturnServiceTest {

    private final ApplicationPaymentProviderAttemptRepository attemptRepository =
            mock(ApplicationPaymentProviderAttemptRepository.class);
    private final CbzIveriLiteTransactionStatusClient transactionStatusClient =
            mock(CbzIveriLiteTransactionStatusClient.class);
    private final FinanceApplicationPaymentService financeApplicationPaymentService =
            mock(FinanceApplicationPaymentService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T08:00:00Z"), ZoneOffset.UTC);
    private final CbzIveriLiteReturnService service =
            new CbzIveriLiteReturnService(
                    attemptRepository, transactionStatusClient, financeApplicationPaymentService, clock);

    @Test
    void confirmsAnApprovedPaymentUsingTheServerVerifiedProviderStatus() {
        UUID attemptId = UUID.randomUUID();
        String nonce = "return-nonce";
        ApplicationPaymentProviderAttempt attempt = attempt(nonce);
        when(attemptRepository.findByIdAndDeletedAtIsNull(attemptId)).thenReturn(Optional.of(attempt));
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("Lite_Merchant_Trace", attempt.getMerchantTrace());
        parameters.add("Lite_Payment_Card_Status", "0");
        parameters.add("Lite_TransactionIndex", "transaction-index");
        parameters.add("Lite_Result_Description", "Approved");
        when(transactionStatusClient.query(attempt.getMerchantTrace())).thenReturn(
                new CbzIveriLiteTransactionStatus(
                        attempt.getMerchantTrace(), "0", "Approved", "transaction-index", null,
                        attempt.getMerchantReference(), new BigDecimal("25.00"),
                        Instant.parse("2026-08-10T07:59:00Z")));

        var result = service.processReturn(attemptId, nonce, "successful", parameters);

        assertEquals("VERIFIED_SUCCESS", attempt.getStatus());
        assertEquals("0", result.get("Lite_Payment_Card_Status"));
        verify(attemptRepository).saveAndFlush(attempt);
        verify(financeApplicationPaymentService).confirmReconciledPayment(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAReturnWithTheWrongNonce() {
        UUID attemptId = UUID.randomUUID();
        ApplicationPaymentProviderAttempt attempt = attempt("expected-nonce");
        when(attemptRepository.findByIdAndDeletedAtIsNull(attemptId)).thenReturn(Optional.of(attempt));
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("Lite_Merchant_Trace", attempt.getMerchantTrace());
        parameters.add("Lite_Payment_Card_Status", "0");

        assertThrows(IllegalArgumentException.class,
                () -> service.processReturn(attemptId, "wrong-nonce", "successful", parameters));
    }

    private ApplicationPaymentProviderAttempt attempt(String nonce) {
        ApplicationPaymentReference paymentReference = mock(ApplicationPaymentReference.class);
        when(paymentReference.getSourceApplicationId()).thenReturn(UUID.randomUUID());
        when(paymentReference.getReference()).thenReturn("EMH-PAY-0000000041");
        ApplicationPaymentProviderAttempt attempt = new ApplicationPaymentProviderAttempt(
                paymentReference,
                "CBZ_IVERI_LITE",
                "merchant-trace",
                "EMH-PAY-0000000041",
                CbzIveriLiteCheckoutService.sha256(nonce),
                "USD",
                new BigDecimal("25.00"),
                "https://portal.host.iveri.com/Lite/Authorise.aspx",
                Instant.parse("2026-08-10T08:20:00Z"));
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        return attempt;
    }
}

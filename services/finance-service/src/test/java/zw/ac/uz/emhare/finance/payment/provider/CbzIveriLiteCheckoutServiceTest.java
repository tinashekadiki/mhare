package zw.ac.uz.emhare.finance.payment.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReference;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReferenceRepository;

/** @author Tinashe K */
class CbzIveriLiteCheckoutServiceTest {

    @Test
    void generatesTheOfficialIveriTransactionTokenExample() {
        String token = CbzIveriLiteCheckoutService.transactionToken(
                "AFcWxV2NG9W4",
                1471358394L,
                "{435407B0-8A28-4152-9649-A932423F72EB}",
                "4130",
                "joedoe@mail.com");

        assertEquals(
                "1471358394:be3350759bb0e41d1ac4a0b906ff9e1063bebbfc014753b713f0d3c49609ce44",
                token);
    }

    @Test
    void keepsOnlinePaymentUnavailableUntilEveryMerchantSettingIsPresent() {
        CbzIveriLiteCheckoutService service = new CbzIveriLiteCheckoutService(
                Mockito.mock(ApplicationPaymentReferenceRepository.class),
                Mockito.mock(ApplicationPaymentProviderAttemptRepository.class),
                new CbzIveriLiteProperties(
                        false,
                        "https://portal.host.iveri.com/Lite/Authorise.aspx",
                        "",
                        "",
                        "USD",
                        "",
                        "",
                        "",
                        ""),
                Clock.systemUTC());

        assertFalse(service.paymentOptions().onlinePayment().available());
        assertEquals("Online card payment is not yet available.",
                service.paymentOptions().onlinePayment().availabilityMessage());
    }

    @Test
    void derivesTheContainedCheckoutUrlFromTheConfiguredGateway() {
        assertEquals(
                "https://portal.host.iveri.com/Lite/LiteBox",
                CbzIveriLiteCheckoutService.embeddedCheckoutUrl(
                        "https://portal.host.iveri.com/Lite/Authorise.aspx"));
    }

    @Test
    void allowsCheckoutWithoutATransactionTokenWhenBackofficeVerificationIsDisabled() {
        CbzIveriLiteProperties properties = new CbzIveriLiteProperties(
                true,
                "https://portal.host.iveri.com/Lite/Authorise.aspx",
                "02ac7a05-da50-430a-bdcf-38318efd4651",
                "",
                "USD",
                "https://apply.uz.ac.zw/api/finance/application-payment-returns/successful",
                "https://apply.uz.ac.zw/api/finance/application-payment-returns/failed",
                "https://apply.uz.ac.zw/api/finance/application-payment-returns/try-later",
                "https://apply.uz.ac.zw/api/finance/application-payment-returns/error");

        assertTrue(properties.ready());
        assertFalse(properties.transactionTokenEnabled());
    }

    @Test
    void derivesTheTrustedReturnMessageOriginFromTheConfiguredCallback() {
        assertEquals(
                "https://apply.uz.ac.zw",
                CbzIveriLiteCheckoutService.returnMessageOrigin(
                        "https://apply.uz.ac.zw/api/finance/application-payment-returns/successful"));
    }

    @Test
    void includesEveryMandatoryLiveLiteBoxField() {
        UUID applicationId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();
        ApplicationPaymentReference paymentReference = mock(ApplicationPaymentReference.class);
        when(paymentReference.getSourceApplicationId()).thenReturn(applicationId);
        when(paymentReference.getAmountDue()).thenReturn(new BigDecimal("25.00"));
        when(paymentReference.getCurrencyCode()).thenReturn("USD");
        when(paymentReference.getReference()).thenReturn("EMH-PAY-0000000041");
        when(paymentReference.getStatusCode()).thenReturn("PENDING");
        when(paymentReference.isOwnedByKeycloakUser(keycloakUserId)).thenReturn(true);
        ApplicationPaymentReferenceRepository paymentReferenceRepository =
                mock(ApplicationPaymentReferenceRepository.class);
        when(paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(paymentReference));
        ApplicationPaymentProviderAttemptRepository attemptRepository =
                mock(ApplicationPaymentProviderAttemptRepository.class);
        when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ApplicationPaymentProviderAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
            return attempt;
        });
        CbzIveriLiteCheckoutService service = new CbzIveriLiteCheckoutService(
                paymentReferenceRepository,
                attemptRepository,
                configuredProperties(),
                Clock.fixed(Instant.parse("2026-08-10T08:00:00Z"), ZoneOffset.UTC));

        var checkout = service.startCheckout(applicationId, keycloakUserId, "applicant@example.com");

        assertEquals("EMH", checkout.formParameters().get("Lite_ConsumerOrderID_PreFix"));
        assertEquals("2500", checkout.formParameters().get("Lite_Order_Amount"));
        assertEquals("applicant@example.com", checkout.formParameters().get("Ecom_BillTo_Online_Email"));
    }

    private CbzIveriLiteProperties configuredProperties() {
        return new CbzIveriLiteProperties(
                true,
                "https://portal.host.iveri.com/Lite/Authorise.aspx",
                "02ac7a05-da50-430a-bdcf-38318efd4651",
                "",
                "USD",
                "http://localhost:8080/api/finance/application-payment-returns/successful",
                "http://localhost:8080/api/finance/application-payment-returns/failed",
                "http://localhost:8080/api/finance/application-payment-returns/try-later",
                "http://localhost:8080/api/finance/application-payment-returns/error");
    }
}

package zw.ac.uz.emhare.finance.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.finance.integration.FinanceIntegrationOutboxService;

@ExtendWith(MockitoExtension.class)
class FinanceApplicationPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2027-02-01T10:00:00Z");

    @Mock
    private ApplicationPaymentReferenceRepository paymentReferenceRepository;

    @Mock
    private ApplicationPaymentRepository applicationPaymentRepository;

    @Mock
    private FinanceReceiptRepository financeReceiptRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private FinanceReferenceGenerator financeReferenceGenerator;

    @Mock
    private FinanceIntegrationOutboxService integrationOutboxService;

    private FinanceApplicationPaymentService financeApplicationPaymentService;

    @BeforeEach
    void setUp() {
        financeApplicationPaymentService = new FinanceApplicationPaymentService(
                paymentReferenceRepository,
                applicationPaymentRepository,
                financeReceiptRepository,
                exchangeRateRepository,
                financeReferenceGenerator,
                integrationOutboxService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ensurePaymentReference_shouldRateUsdAtFaceValueWithoutExchangeRate() {
        UUID applicationId = UUID.randomUUID();
        CreateApplicationPaymentReferenceCommand command = command(applicationId, "USD", new BigDecimal("25.00"));
        when(paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.empty());
        when(financeReferenceGenerator.nextPaymentReference()).thenReturn("EMH-PAY-0000000001");
        when(paymentReferenceRepository.saveAndFlush(any(ApplicationPaymentReference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationPaymentReferenceSummary summary = financeApplicationPaymentService.ensurePaymentReference(command);

        assertEquals("EMH-PAY-0000000001", summary.reference());
        assertEquals(new BigDecimal("25.00"), summary.baseAmountDue());
        assertEquals("RATED", summary.ratingStatus());
        assertEquals("PENDING", summary.status());
        assertFalse(summary.workflowCleared());
        verify(exchangeRateRepository, never()).findEffectiveRates(any(), any());
        verify(integrationOutboxService).enqueuePaymentReferenceUpdated(any(ApplicationPaymentReference.class));
    }

    @Test
    void ensurePaymentReference_shouldLeaveZwgUnratedWhenNoEffectiveRateExists() {
        UUID applicationId = UUID.randomUUID();
        when(paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findEffectiveRates("ZWG", NOW)).thenReturn(List.of());
        when(financeReferenceGenerator.nextPaymentReference()).thenReturn("EMH-PAY-0000000002");
        when(paymentReferenceRepository.saveAndFlush(any(ApplicationPaymentReference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationPaymentReferenceSummary summary = financeApplicationPaymentService.ensurePaymentReference(
                command(applicationId, "ZWG", new BigDecimal("700.00")));

        assertNull(summary.baseAmountDue());
        assertEquals("UNRATED", summary.ratingStatus());
        assertFalse(summary.workflowCleared());
    }

    @Test
    void ensurePaymentReference_shouldReturnSameReferenceForMatchingRetry() {
        UUID applicationId = UUID.randomUUID();
        CreateApplicationPaymentReferenceCommand command = command(applicationId, "USD", new BigDecimal("25.00"));
        ApplicationPaymentReference existingReference = reference(command, "EMH-PAY-0000000003");
        when(paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(existingReference));

        ApplicationPaymentReferenceSummary summary = financeApplicationPaymentService.ensurePaymentReference(command);

        assertEquals("EMH-PAY-0000000003", summary.reference());
        verify(paymentReferenceRepository, never()).save(any());
    }

    @Test
    void findPaymentReferences_shouldReturnBulkOperationalProjection() {
        UUID firstApplicationId = UUID.randomUUID();
        UUID secondApplicationId = UUID.randomUUID();
        ApplicationPaymentReference firstReference = reference(
                command(firstApplicationId, "USD", new BigDecimal("25.00")),
                "EMH-PAY-0000000101");
        ApplicationPaymentReference secondReference = reference(
                command(secondApplicationId, "USD", new BigDecimal("30.00")),
                "EMH-PAY-0000000102");
        when(paymentReferenceRepository.findBySourceApplicationIdInAndDeletedAtIsNull(
                List.of(firstApplicationId, secondApplicationId)))
                .thenReturn(List.of(firstReference, secondReference));

        List<ApplicationPaymentReferenceSummary> summaries = financeApplicationPaymentService
                .findPaymentReferences(List.of(firstApplicationId, secondApplicationId));

        assertEquals(
                List.of("EMH-PAY-0000000101", "EMH-PAY-0000000102"),
                summaries.stream().map(ApplicationPaymentReferenceSummary::reference).toList());
    }

    @Test
    void ensurePaymentReference_shouldRejectRetryWithDifferentImmutableAmount() {
        UUID applicationId = UUID.randomUUID();
        CreateApplicationPaymentReferenceCommand original = command(applicationId, "USD", new BigDecimal("25.00"));
        when(paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(reference(original, "EMH-PAY-0000000004")));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> financeApplicationPaymentService.ensurePaymentReference(
                        command(applicationId, "USD", new BigDecimal("30.00"))));

        assertEquals(
                "Application payment reference already exists with different immutable details.",
                exception.getMessage());
    }

    @Test
    void confirmReconciledPayment_shouldApplyRateAtPaymentTimeAndCreateReceipt() {
        UUID applicationId = UUID.randomUUID();
        CreateApplicationPaymentReferenceCommand createCommand = command(
                applicationId, "ZWG", new BigDecimal("700.00"));
        ApplicationPaymentReference paymentReference = new ApplicationPaymentReference(
                applicationId,
                createCommand.applicantUserId(),
                createCommand.applicantKeycloakUserId(),
                "EMH-PAY-0000000005",
                createCommand.amountDue(),
                "ZWG",
                null,
                null,
                MoneyRatingStatus.UNRATED,
                true,
                NOW.plusSeconds(86400));
        ExchangeRate effectiveRate = new ExchangeRate(
                "ZWG",
                new BigDecimal("0.03500000"),
                NOW.minusSeconds(3600),
                null,
                "Reserve Bank published rate",
                "RBZ-2027-032",
                UUID.randomUUID());
        effectiveRate.approve(UUID.randomUUID(), NOW.minusSeconds(1800), "Treasury verification completed.");
        when(applicationPaymentRepository.findByProviderCodeAndProviderTransactionReference("PAYNOW", "TX-100"))
                .thenReturn(Optional.empty());
        when(paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(paymentReference));
        when(exchangeRateRepository.findEffectiveRates("ZWG", NOW.minusSeconds(60)))
                .thenReturn(List.of(effectiveRate));
        when(applicationPaymentRepository.save(any(ApplicationPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentReferenceRepository.saveAndFlush(paymentReference)).thenReturn(paymentReference);
        when(financeReferenceGenerator.nextReceiptNumber()).thenReturn("EMH-RCT-0000000001");

        ApplicationPaymentReferenceSummary summary = financeApplicationPaymentService.confirmReconciledPayment(
                new ConfirmApplicationPaymentCommand(
                        applicationId,
                        "paynow",
                        "TX-100",
                        new BigDecimal("700.00"),
                        "ZWG",
                        NOW.minusSeconds(60),
                        "sha256:event-100"));

        assertEquals("PAID", summary.status());
        assertEquals("RATED", summary.ratingStatus());
        assertEquals(new BigDecimal("24.50"), summary.baseAmountDue());
        assertTrue(summary.workflowCleared());
        verify(financeReceiptRepository).save(any(FinanceReceipt.class));
        verify(integrationOutboxService).enqueuePaymentReferenceUpdated(paymentReference);
    }

    @Test
    void confirmReconciledPayment_shouldRejectProviderReferenceReusedForAnotherApplication() {
        UUID originalApplicationId = UUID.randomUUID();
        ApplicationPaymentReference originalReference = reference(
                command(originalApplicationId, "USD", new BigDecimal("25.00")),
                "EMH-PAY-0000000006");
        ApplicationPayment existingPayment = new ApplicationPayment(
                originalReference,
                "PAYNOW",
                "TX-200",
                new BigDecimal("25.00"),
                "USD",
                null,
                new BigDecimal("25.00"),
                MoneyRatingStatus.RATED,
                NOW.minusSeconds(60),
                NOW,
                "sha256:event-200");
        when(applicationPaymentRepository.findByProviderCodeAndProviderTransactionReference("PAYNOW", "TX-200"))
                .thenReturn(Optional.of(existingPayment));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> financeApplicationPaymentService.confirmReconciledPayment(new ConfirmApplicationPaymentCommand(
                        UUID.randomUUID(),
                        "PAYNOW",
                        "TX-200",
                        new BigDecimal("25.00"),
                        "USD",
                        NOW.minusSeconds(60),
                        "sha256:event-200")));

        assertEquals(
                "Provider transaction reference was already used with different payment details.",
                exception.getMessage());
    }

    private CreateApplicationPaymentReferenceCommand command(
            UUID applicationId,
            String currencyCode,
            BigDecimal amountDue) {
        return new CreateApplicationPaymentReferenceCommand(
                applicationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                amountDue,
                currencyCode,
                true);
    }

    private ApplicationPaymentReference reference(
            CreateApplicationPaymentReferenceCommand command,
            String reference) {
        return new ApplicationPaymentReference(
                command.sourceApplicationId(),
                command.applicantUserId(),
                command.applicantKeycloakUserId(),
                reference,
                command.amountDue(),
                command.currencyCode(),
                null,
                command.amountDue(),
                MoneyRatingStatus.RATED,
                true,
                NOW.plusSeconds(86400));
    }
}

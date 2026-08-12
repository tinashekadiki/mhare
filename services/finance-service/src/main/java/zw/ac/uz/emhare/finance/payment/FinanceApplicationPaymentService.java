package zw.ac.uz.emhare.finance.payment;

import zw.ac.uz.emhare.finance.payment.domain.model.ApplicationPayment;
import zw.ac.uz.emhare.finance.payment.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.payment.domain.model.FinanceReceipt;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ApplicationPaymentReferenceRepository;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ApplicationPaymentRepository;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ExchangeRateRepository;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.FinanceReceiptRepository;

import zw.ac.uz.emhare.finance.payment.application.command.*;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.finance.integration.FinanceIntegrationOutboxService;
import zw.ac.uz.emhare.finance.payment.domain.model.MoneyRatingStatus;

@Service
public class FinanceApplicationPaymentService {

    private static final Duration PAYMENT_REFERENCE_LIFETIME = Duration.ofDays(14);

    private final ApplicationPaymentReferenceRepository paymentReferenceRepository;
    private final ApplicationPaymentRepository applicationPaymentRepository;
    private final FinanceReceiptRepository financeReceiptRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final FinanceReferenceGenerator financeReferenceGenerator;
    private final FinanceIntegrationOutboxService integrationOutboxService;
    private final Clock clock;

    public FinanceApplicationPaymentService(
            ApplicationPaymentReferenceRepository paymentReferenceRepository,
            ApplicationPaymentRepository applicationPaymentRepository,
            FinanceReceiptRepository financeReceiptRepository,
            ExchangeRateRepository exchangeRateRepository,
            FinanceReferenceGenerator financeReferenceGenerator,
            FinanceIntegrationOutboxService integrationOutboxService,
            Clock clock) {
        this.paymentReferenceRepository = paymentReferenceRepository;
        this.applicationPaymentRepository = applicationPaymentRepository;
        this.financeReceiptRepository = financeReceiptRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.financeReferenceGenerator = financeReferenceGenerator;
        this.integrationOutboxService = integrationOutboxService;
        this.clock = clock;
    }

    @Transactional
    public ApplicationPaymentReferenceSummary ensurePaymentReference(CreateApplicationPaymentReferenceCommand command) {
        validateCreateCommand(command);
        String currencyCode = ExchangeRate.normalizeCurrencyCode(command.currencyCode());
        return paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(command.sourceApplicationId())
                .map(existingReference -> existingSummaryOrConflict(existingReference, command, currencyCode))
                .orElseGet(() -> createPaymentReference(command, currencyCode));
    }

    public ApplicationPaymentReferenceSummary findApplicantPaymentReference(UUID sourceApplicationId, UUID keycloakUserId) {
        ApplicationPaymentReference paymentReference = requirePaymentReference(sourceApplicationId);
        if (!paymentReference.isOwnedByKeycloakUser(keycloakUserId)) {
            throw new IllegalArgumentException("Payment reference not found.");
        }
        return ApplicationPaymentReferenceSummary.from(paymentReference);
    }

    public ApplicationPaymentReferenceSummary findPaymentReference(UUID sourceApplicationId) {
        return ApplicationPaymentReferenceSummary.from(requirePaymentReference(sourceApplicationId));
    }

    public List<ApplicationPaymentReferenceSummary> findPaymentReferences(List<UUID> sourceApplicationIds) {
        if (sourceApplicationIds == null || sourceApplicationIds.isEmpty()) {
            return List.of();
        }
        return paymentReferenceRepository
                .findBySourceApplicationIdInAndDeletedAtIsNull(sourceApplicationIds)
                .stream()
                .map(ApplicationPaymentReferenceSummary::from)
                .toList();
    }

    @Transactional
    public ApplicationPaymentReferenceSummary confirmReconciledPayment(ConfirmApplicationPaymentCommand command) {
        validateConfirmationCommand(command);
        String providerCode = command.providerCode().trim().toUpperCase(Locale.ROOT);
        String providerTransactionReference = command.providerTransactionReference().trim();
        String currencyCode = ExchangeRate.normalizeCurrencyCode(command.currencyCode());

        ApplicationPayment existingPayment = applicationPaymentRepository
                .findByProviderCodeAndProviderTransactionReference(providerCode, providerTransactionReference)
                .orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.matches(
                    command.sourceApplicationId(),
                    command.amount(),
                    currencyCode,
                    command.providerEventFingerprint())) {
                throw new IllegalStateException("Provider transaction reference was already used with different payment details.");
            }
            return findPaymentReference(command.sourceApplicationId());
        }

        ApplicationPaymentReference paymentReference = requirePaymentReference(command.sourceApplicationId());
        if (paymentReference.getAmountDue().compareTo(command.amount()) != 0
                || !paymentReference.getCurrencyCode().equals(currencyCode)) {
            throw new IllegalStateException("Payment amount and currency must match the application payment reference.");
        }

        RatedMoney ratedPayment = rate(command.amount(), currencyCode, command.paidAt());
        ApplicationPayment applicationPayment = applicationPaymentRepository.save(new ApplicationPayment(
                paymentReference,
                providerCode,
                providerTransactionReference,
                command.amount(),
                currencyCode,
                ratedPayment.exchangeRate(),
                ratedPayment.baseAmount(),
                ratedPayment.ratingStatus(),
                command.paidAt(),
                clock.instant(),
                command.providerEventFingerprint().trim()));

        paymentReference.markPaid(
                command.paidAt(),
                ratedPayment.exchangeRate(),
                ratedPayment.baseAmount(),
                ratedPayment.ratingStatus());
        paymentReferenceRepository.saveAndFlush(paymentReference);
        financeReceiptRepository.save(new FinanceReceipt(
                applicationPayment,
                financeReferenceGenerator.nextReceiptNumber()));
        integrationOutboxService.enqueuePaymentReferenceUpdated(paymentReference);
        return ApplicationPaymentReferenceSummary.from(paymentReference);
    }

    private ApplicationPaymentReferenceSummary createPaymentReference(
            CreateApplicationPaymentReferenceCommand command,
            String currencyCode) {
        Instant now = clock.instant();
        RatedMoney ratedAmount = rate(command.amountDue(), currencyCode, now);
        ApplicationPaymentReference paymentReference = paymentReferenceRepository.saveAndFlush(new ApplicationPaymentReference(
                command.sourceApplicationId(),
                command.applicantUserId(),
                command.applicantKeycloakUserId(),
                financeReferenceGenerator.nextPaymentReference(),
                command.amountDue(),
                currencyCode,
                ratedAmount.exchangeRate(),
                ratedAmount.baseAmount(),
                ratedAmount.ratingStatus(),
                command.requiredForSubmission(),
                now.plus(PAYMENT_REFERENCE_LIFETIME)));
        integrationOutboxService.enqueuePaymentReferenceUpdated(paymentReference);
        return ApplicationPaymentReferenceSummary.from(paymentReference);
    }

    private ApplicationPaymentReferenceSummary existingSummaryOrConflict(
            ApplicationPaymentReference existingReference,
            CreateApplicationPaymentReferenceCommand command,
            String currencyCode) {
        if (!existingReference.matches(command.applicantUserId(), command.amountDue(), currencyCode)) {
            throw new IllegalStateException("Application payment reference already exists with different immutable details.");
        }
        return ApplicationPaymentReferenceSummary.from(existingReference);
    }

    private RatedMoney rate(BigDecimal amount, String currencyCode, Instant effectiveAt) {
        if ("USD".equals(currencyCode)) {
            return new RatedMoney(null, amount, MoneyRatingStatus.RATED);
        }
        List<ExchangeRate> effectiveRates = exchangeRateRepository.findEffectiveRates(currencyCode, effectiveAt);
        if (effectiveRates.size() > 1) {
            throw new IllegalStateException("Multiple effective exchange rates require finance configuration correction.");
        }
        if (effectiveRates.isEmpty()) {
            return new RatedMoney(null, null, MoneyRatingStatus.UNRATED);
        }
        ExchangeRate exchangeRate = effectiveRates.getFirst();
        return new RatedMoney(
                exchangeRate,
                amount.multiply(exchangeRate.getRateToBase()).setScale(2, RoundingMode.HALF_UP),
                MoneyRatingStatus.RATED);
    }

    private ApplicationPaymentReference requirePaymentReference(UUID sourceApplicationId) {
        return paymentReferenceRepository.findBySourceApplicationIdAndDeletedAtIsNull(sourceApplicationId)
                .orElseThrow(() -> new IllegalArgumentException("Payment reference not found."));
    }

    private void validateCreateCommand(CreateApplicationPaymentReferenceCommand command) {
        if (command == null
                || command.sourceApplicationId() == null
                || command.applicantUserId() == null
                || command.applicantKeycloakUserId() == null) {
            throw new IllegalArgumentException("Application and applicant identifiers are required.");
        }
        if (!command.requiredForSubmission()) {
            throw new IllegalArgumentException("A payment reference is only created for a required application fee.");
        }
        if (command.amountDue() == null || command.amountDue().signum() <= 0) {
            throw new IllegalArgumentException("Application fee amount must be greater than zero.");
        }
    }

    private void validateConfirmationCommand(ConfirmApplicationPaymentCommand command) {
        if (command == null || command.sourceApplicationId() == null) {
            throw new IllegalArgumentException("Application identifier is required.");
        }
        if (command.providerCode() == null || command.providerCode().isBlank()
                || command.providerTransactionReference() == null || command.providerTransactionReference().isBlank()) {
            throw new IllegalArgumentException("Payment provider and transaction reference are required.");
        }
        if (command.amount() == null || command.amount().signum() <= 0 || command.paidAt() == null) {
            throw new IllegalArgumentException("Payment amount and paid timestamp are required.");
        }
        if (command.providerEventFingerprint() == null || command.providerEventFingerprint().isBlank()) {
            throw new IllegalArgumentException("Provider event fingerprint is required for idempotency verification.");
        }
    }

    private record RatedMoney(
            ExchangeRate exchangeRate,
            BigDecimal baseAmount,
            MoneyRatingStatus ratingStatus) {
    }
}

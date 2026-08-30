package zw.ac.uz.emhare.finance.collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.billing.infrastructure.persistence.*;
import zw.ac.uz.emhare.finance.collections.api.model.FinanceCollectionsApiModels.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.collections.infrastructure.persistence.*;
import zw.ac.uz.emhare.finance.payment.FinanceReferenceGenerator;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ExchangeRateRepository;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;
import zw.ac.uz.emhare.finance.student.infrastructure.persistence.StudentFinanceAccountRepository;

/**
 * @author Tinashe K
 */
class GovernedFinanceCollectionsServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID ACCOUNT = new UUID(0, 1),
      PAYMENT = new UUID(0, 2),
      INVOICE = new UUID(0, 3),
      RATE = new UUID(0, 4),
      ALLOCATION = new UUID(0, 5),
      CREDIT = new UUID(0, 6),
      LINE = new UUID(0, 7),
      CAPTURER = new UUID(0, 10),
      REVIEWER = new UUID(0, 11);
  private static final ControlledDecision DECISION = new ControlledDecision("Evidence checked", 0);
  private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
  private final StudentAccountPaymentRepository payments =
      mock(StudentAccountPaymentRepository.class);
  private final StudentPaymentSuspenseResolutionRepository suspense =
      mock(StudentPaymentSuspenseResolutionRepository.class);
  private final StudentPaymentReceiptRepository receipts =
      mock(StudentPaymentReceiptRepository.class);
  private final StudentPaymentAllocationRepository allocations =
      mock(StudentPaymentAllocationRepository.class);
  private final StudentPaymentAllocationReversalRepository allocationReversals =
      mock(StudentPaymentAllocationReversalRepository.class);
  private final StudentPaymentReversalRepository paymentReversals =
      mock(StudentPaymentReversalRepository.class);
  private final FinanceCreditNoteRepository credits = mock(FinanceCreditNoteRepository.class);
  private final FinanceCreditNoteLineRepository creditLines =
      mock(FinanceCreditNoteLineRepository.class);
  private final StudentFinanceAccountRepository accounts =
      mock(StudentFinanceAccountRepository.class);
  private final FinanceInvoiceRepository invoices = mock(FinanceInvoiceRepository.class);
  private final FinanceInvoiceLineRepository invoiceLines =
      mock(FinanceInvoiceLineRepository.class);
  private final FinanceReferenceGenerator references = mock(FinanceReferenceGenerator.class);
  private final GovernedFinanceCollectionsService service =
      new GovernedFinanceCollectionsService(
          rates,
          payments,
          suspense,
          receipts,
          allocations,
          allocationReversals,
          paymentReversals,
          credits,
          creditLines,
          accounts,
          invoices,
          invoiceLines,
          references,
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final StudentFinanceAccount account = mock(StudentFinanceAccount.class);
  private final FinanceInvoice invoice = mock(FinanceInvoice.class);
  private final FinanceInvoiceLine invoiceLine = mock(FinanceInvoiceLine.class);
  private final List<StudentAccountPayment> storedPayments = new ArrayList<>();
  private final List<StudentPaymentReceipt> storedReceipts = new ArrayList<>();
  private final List<StudentPaymentAllocation> storedAllocations = new ArrayList<>();
  private final List<StudentPaymentAllocationReversal> storedAllocationReversals =
      new ArrayList<>();
  private final List<FinanceCreditNoteLine> storedCreditLines = new ArrayList<>();
  private FinanceCreditNote credit;
  private ExchangeRate exchangeRate;

  @BeforeEach
  void setUp() {
    when(account.getId()).thenReturn(ACCOUNT);
    when(account.getStatus()).thenReturn("ACTIVE");
    when(account.getStudentNumber()).thenReturn("R260001");
    when(account.getAccountNumber()).thenReturn("ACC-001");
    when(accounts.findByIdAndDeletedAtIsNull(ACCOUNT)).thenReturn(Optional.of(account));
    when(accounts.findAllByDeletedAtIsNullOrderByStudentNumberAsc()).thenReturn(List.of(account));
    when(invoice.getId()).thenReturn(INVOICE);
    when(invoice.getStudentFinanceAccount()).thenReturn(account);
    when(invoice.getInvoiceNumber()).thenReturn("INV-001");
    when(invoice.getTransactionCurrencyCode()).thenReturn("USD");
    when(invoice.getGrossTransactionAmount()).thenReturn(new BigDecimal("100"));
    when(invoice.getGrossBaseAmount()).thenReturn(new BigDecimal("100"));
    when(invoice.getPostedAt()).thenReturn(NOW.minusSeconds(60));
    when(invoices.findLockedByIdAndDeletedAtIsNull(INVOICE)).thenReturn(Optional.of(invoice));
    when(invoices.findAllByDeletedAtIsNullOrderByPostedAtDescInvoiceNumberDesc())
        .thenReturn(List.of(invoice));
    when(invoiceLine.getId()).thenReturn(LINE);
    when(invoiceLine.getInvoice()).thenReturn(invoice);
    when(invoiceLines.findByIdAndDeletedAtIsNull(LINE)).thenReturn(Optional.of(invoiceLine));
    when(references.nextStudentPaymentNumber()).thenReturn("PAY-001");
    when(references.nextStudentReceiptNumber()).thenReturn("REC-001");
    when(references.nextAllocationNumber()).thenReturn("ALLOC-001");
    when(references.nextReversalNumber()).thenReturn("REV-001");
    when(references.nextCreditNoteNumber()).thenReturn("CN-001");
    when(payments.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentAccountPayment value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value, PAYMENT);
                storedPayments.add(value);
              }
              return value;
            });
    when(payments.findLockedById(PAYMENT)).thenAnswer(inv -> storedPayments.stream().findFirst());
    when(payments.findAllByDeletedAtIsNullOrderByPaidAtDescPaymentNumberDesc())
        .thenReturn(storedPayments);
    when(receipts.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentPaymentReceipt value = inv.getArgument(0);
              storedReceipts.add(value);
              return value;
            });
    when(receipts.findByPaymentIdAndDeletedAtIsNull(PAYMENT))
        .thenAnswer(inv -> storedReceipts.stream().findFirst());
    when(receipts.findAllByDeletedAtIsNullOrderByIssuedAtDescReceiptNumberDesc())
        .thenReturn(storedReceipts);
    when(allocations.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentPaymentAllocation value = identify(inv.getArgument(0), ALLOCATION);
              storedAllocations.add(value);
              return value;
            });
    when(allocations.findAllByDeletedAtIsNullOrderByAllocatedAtDescAllocationNumberDesc())
        .thenReturn(storedAllocations);
    when(allocations.findLockedById(ALLOCATION))
        .thenAnswer(inv -> storedAllocations.stream().findFirst());
    when(allocationReversals.findAllByDeletedAtIsNullOrderByReversedAtDesc())
        .thenReturn(storedAllocationReversals);
    when(allocationReversals.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentPaymentAllocationReversal value = inv.getArgument(0);
              storedAllocationReversals.add(value);
              return value;
            });
    when(allocationReversals.findByAllocationIdAndDeletedAtIsNull(ALLOCATION))
        .thenAnswer(inv -> storedAllocationReversals.stream().findFirst());
    when(credits.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              credit = identify(inv.getArgument(0), CREDIT);
              return credit;
            });
    when(credits.findLockedById(CREDIT)).thenAnswer(inv -> Optional.ofNullable(credit));
    when(credits.findAllByDeletedAtIsNullOrderByPreparedAtDescCreditNoteNumberDesc())
        .thenAnswer(inv -> credit == null ? List.of() : List.of(credit));
    when(creditLines.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<FinanceCreditNoteLine> added = inv.getArgument(0);
              storedCreditLines.addAll(added);
              return added;
            });
    when(creditLines.findAllByCreditNoteIdAndDeletedAtIsNullOrderByLineNumberAsc(CREDIT))
        .thenReturn(storedCreditLines);
    exchangeRate =
        identify(
            new ExchangeRate(
                "ZWG",
                new BigDecimal("0.04"),
                NOW.minusSeconds(600),
                null,
                "Approved source",
                "FX-1",
                CAPTURER),
            RATE);
    when(rates.saveAndFlush(any())).thenAnswer(inv -> identify(inv.getArgument(0), RATE));
    when(rates.findLockedByIdAndDeletedAtIsNull(RATE)).thenReturn(Optional.of(exchangeRate));
  }

  @Test
  void capturesUsdDirectlyAndRetainsProviderEvidenceWithoutLookingUpFx() {
    var captured = service.capture(command(ACCOUNT, " usd "), CAPTURER);
    assertEquals("USD", captured.transactionCurrencyCode());
    assertEquals("USD", captured.baseCurrencyCode());
    assertEquals(new BigDecimal("100"), captured.baseAmount());
    assertEquals(StudentAccountPayment.RatingStatus.RATED, captured.ratingStatus());
    assertEquals("BANK", captured.providerCode());
    assertEquals("provider-1", captured.providerTransactionReference());
    assertEquals(
        StudentAccountPayment.ReconciliationStatus.PENDING, captured.reconciliationStatus());
    assertNull(captured.exchangeRateId());
    assertFalse(captured.inSuspense());
    assertNull(captured.receiptNumber());
    verify(rates, never()).findEffectiveRates(any(), any());
  }

  @Test
  void missingZwgRateRemainsUnratedUntilEffectiveEvidenceIsAvailable() {
    var unrated = service.capture(command(ACCOUNT, "ZWG"), CAPTURER);
    assertEquals(StudentAccountPayment.RatingStatus.UNRATED, unrated.ratingStatus());
    assertNull(unrated.baseAmount());
    assertThrows(IllegalStateException.class, () -> service.applyRate(PAYMENT, 0, REVIEWER));
    assertThrows(
        IllegalStateException.class,
        () -> service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER));
    exchangeRate.approve(REVIEWER, NOW, "Approved");
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(exchangeRate));
    var rated = service.applyRate(PAYMENT, 0, REVIEWER);
    assertEquals(new BigDecimal("4.00"), rated.baseAmount());
    assertEquals(RATE, rated.exchangeRateId());
    assertThrows(IllegalStateException.class, () -> service.applyRate(PAYMENT, 0, REVIEWER));
    assertEquals(
        "REC-001", service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER).receiptNumber());
  }

  @Test
  void ratesForeignPaymentsAtCaptureAndRejectsOverlappingRateEvidence() {
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(exchangeRate));
    assertEquals(
        new BigDecimal("4.00"), service.capture(command(ACCOUNT, "ZWG"), CAPTURER).baseAmount());
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(exchangeRate, exchangeRate));
    assertThrows(
        IllegalStateException.class, () -> service.capture(command(ACCOUNT, "ZWG"), CAPTURER));
  }

  @Test
  void providerRetriesReturnTheOriginalPaymentButConflictingEvidenceFailsClosed() {
    service.capture(command(ACCOUNT, "USD"), CAPTURER);
    StudentAccountPayment captured = storedPayments.getFirst();
    when(payments.findByProviderCodeAndProviderTransactionReferenceAndDeletedAtIsNull(
            "BANK", "provider-1"))
        .thenReturn(Optional.of(captured));
    assertEquals(PAYMENT, service.capture(command(ACCOUNT, "USD"), CAPTURER).id());
    verify(payments).saveAndFlush(any());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.capture(
                new CapturePayment(
                    ACCOUNT,
                    "Payer",
                    "bank",
                    "provider-1",
                    StudentAccountPayment.PaymentChannel.BANK_TRANSFER,
                    "USD",
                    new BigDecimal("99"),
                    NOW,
                    "fingerprint"),
                CAPTURER));
    when(payments.findByProviderCodeAndProviderTransactionReferenceAndDeletedAtIsNull(any(), any()))
        .thenReturn(Optional.empty());
    when(payments.findByProviderEventFingerprintAndDeletedAtIsNull("fingerprint"))
        .thenReturn(Optional.of(captured));
    assertThrows(
        IllegalStateException.class, () -> service.capture(command(ACCOUNT, "USD"), CAPTURER));
  }

  @Test
  void databaseUniquenessRaceSurfacesAsAControlledEvidenceConflict() {
    doThrow(new DataIntegrityViolationException("duplicate")).when(payments).saveAndFlush(any());
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () -> service.capture(command(ACCOUNT, "USD"), CAPTURER))
            .getMessage()
            .contains("conflicts"));
  }

  @Test
  void inactiveAndMissingAccountsCannotReceivePaymentEvidence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.capture(command(new UUID(0, 99), "USD"), CAPTURER));
    when(account.getStatus()).thenReturn("INACTIVE");
    assertThrows(
        IllegalStateException.class, () -> service.capture(command(ACCOUNT, "USD"), CAPTURER));
    verify(payments, never()).saveAndFlush(any());
  }

  @Test
  void reconciliationRequiresIndependentOperatorAndIssuesOneReceipt() {
    captureUsd();
    assertThrows(
        IllegalStateException.class,
        () -> service.decidePayment(PAYMENT, "reconcile", DECISION, CAPTURER));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.decidePayment(
                PAYMENT, "reconcile", new ControlledDecision("Stale", 1), REVIEWER));
    var reconciled = service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    assertEquals(
        StudentAccountPayment.ReconciliationStatus.RECONCILED, reconciled.reconciliationStatus());
    assertEquals(REVIEWER, reconciled.reconciledByUserId());
    assertEquals(NOW, reconciled.reconciledAt());
    assertEquals("REC-001", reconciled.receiptNumber());
    assertThrows(
        IllegalStateException.class,
        () -> service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER));
    assertThrows(
        IllegalStateException.class,
        () -> service.decidePayment(PAYMENT, "reject", DECISION, REVIEWER));
    verify(receipts).saveAndFlush(any());
  }

  @Test
  void rejectionRequiresIndependentOperatorAndNeverIssuesAReceipt() {
    captureUsd();
    assertThrows(
        IllegalStateException.class,
        () -> service.decidePayment(PAYMENT, "reject", DECISION, CAPTURER));
    assertEquals(
        StudentAccountPayment.ReconciliationStatus.REJECTED,
        service.decidePayment(PAYMENT, "reject", DECISION, REVIEWER).reconciliationStatus());
    verify(receipts, never()).saveAndFlush(any());
  }

  @Test
  void resolvesReconciledSuspenseWithSeparateEvidenceAndAnAccountReceipt() {
    service.capture(command(null, "USD"), CAPTURER);
    ResolveSuspense resolution = new ResolveSuspense(ACCOUNT, "Payer identified", 0);
    assertThrows(
        IllegalStateException.class, () -> service.resolveSuspense(PAYMENT, resolution, REVIEWER));
    var reconciled = service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    assertTrue(reconciled.inSuspense());
    assertNull(reconciled.receiptNumber());
    assertThrows(
        IllegalStateException.class, () -> service.resolveSuspense(PAYMENT, resolution, CAPTURER));
    when(suspense.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentPaymentSuspenseResolution value = inv.getArgument(0);
              when(suspense.findByPaymentIdAndDeletedAtIsNull(PAYMENT))
                  .thenReturn(Optional.of(value));
              return value;
            });
    var resolved = service.resolveSuspense(PAYMENT, resolution, REVIEWER);
    assertFalse(resolved.inSuspense());
    assertEquals(ACCOUNT, resolved.studentFinanceAccountId());
    assertNull(storedPayments.getFirst().getStudentFinanceAccount());
    assertEquals("REC-001", resolved.receiptNumber());
    assertThrows(
        IllegalStateException.class, () -> service.resolveSuspense(PAYMENT, resolution, REVIEWER));
  }

  @Test
  void cannotResolveAccountBoundPaymentOrStaleSuspense() {
    captureUsd();
    assertThrows(
        IllegalStateException.class,
        () ->
            service.resolveSuspense(
                PAYMENT, new ResolveSuspense(ACCOUNT, "Not suspense", 0), REVIEWER));
    assertThrows(
        IllegalStateException.class,
        () -> service.resolveSuspense(PAYMENT, new ResolveSuspense(ACCOUNT, "Stale", 1), REVIEWER));
  }

  @Test
  void allocatesPartialPaymentsAndFinalResidualUsingSeparateFxBases() {
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(exchangeRate));
    service.capture(command(ACCOUNT, "ZWG"), CAPTURER);
    service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    when(invoice.getTransactionCurrencyCode()).thenReturn("ZWG");
    when(invoice.getGrossBaseAmount()).thenReturn(new BigDecimal("5.00"));
    var first =
        service.allocate(
            PAYMENT,
            new AllocatePayment(INVOICE, new BigDecimal("33.33"), "First instalment", 0),
            CAPTURER);
    assertEquals(new BigDecimal("1.33"), first.paymentBaseAmount());
    assertEquals(new BigDecimal("1.67"), first.invoiceBaseAmount());
    assertEquals(new BigDecimal("-0.34"), first.realisedExchangeDifference());
    var finalPart =
        service.allocate(
            PAYMENT,
            new AllocatePayment(INVOICE, new BigDecimal("66.67"), "Final instalment", 0),
            CAPTURER);
    assertEquals(new BigDecimal("2.67"), finalPart.paymentBaseAmount());
    assertEquals(new BigDecimal("3.33"), finalPart.invoiceBaseAmount());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.allocate(
                PAYMENT, new AllocatePayment(INVOICE, BigDecimal.ONE, "Overpaid", 0), CAPTURER));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "unreconciled",
        "suspense",
        "account",
        "currency",
        "reversed",
        "missing-invoice",
        "stale"
      })
  void allocationRejectsIncompatibleEvidence(String mismatch) {
    service.capture(command(mismatch.equals("suspense") ? null : ACCOUNT, "USD"), CAPTURER);
    if (!mismatch.equals("unreconciled"))
      service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    if (mismatch.equals("account")) {
      StudentFinanceAccount another = mock(StudentFinanceAccount.class);
      when(another.getId()).thenReturn(new UUID(0, 99));
      when(invoice.getStudentFinanceAccount()).thenReturn(another);
    }
    if (mismatch.equals("currency")) when(invoice.getTransactionCurrencyCode()).thenReturn("ZWG");
    if (mismatch.equals("reversed"))
      when(paymentReversals.findByPaymentIdAndDeletedAtIsNull(PAYMENT))
          .thenReturn(Optional.of(mock(StudentPaymentReversal.class)));
    if (mismatch.equals("missing-invoice"))
      when(invoices.findLockedByIdAndDeletedAtIsNull(INVOICE)).thenReturn(Optional.empty());
    assertThrows(
        RuntimeException.class,
        () ->
            service.allocate(
                PAYMENT,
                new AllocatePayment(
                    INVOICE, BigDecimal.TEN, "Allocate", mismatch.equals("stale") ? 1 : 0),
                CAPTURER));
    verify(allocations, never()).saveAndFlush(any());
  }

  @Test
  void reversalsPreserveEvidenceAndReleaseOnlyReversedAllocations() {
    captureUsd();
    service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    service.allocate(
        PAYMENT, new AllocatePayment(INVOICE, BigDecimal.TEN, "Allocate", 0), CAPTURER);
    assertThrows(
        IllegalStateException.class, () -> service.reversePayment(PAYMENT, DECISION, REVIEWER));
    assertThrows(
        IllegalStateException.class,
        () -> service.reverseAllocation(ALLOCATION, DECISION, CAPTURER));
    assertThrows(
        IllegalStateException.class,
        () -> service.reverseAllocation(ALLOCATION, new ControlledDecision("Stale", 1), REVIEWER));
    var reversal = service.reverseAllocation(ALLOCATION, DECISION, REVIEWER);
    assertTrue(reversal.reversed());
    assertEquals("REV-001", reversal.reversalNumber());
    assertThrows(
        IllegalStateException.class,
        () -> service.reverseAllocation(ALLOCATION, DECISION, REVIEWER));
    when(paymentReversals.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentPaymentReversal value = inv.getArgument(0);
              when(paymentReversals.findByPaymentIdAndDeletedAtIsNull(PAYMENT))
                  .thenReturn(Optional.of(value));
              return value;
            });
    assertTrue(service.reversePayment(PAYMENT, DECISION, REVIEWER).reversed());
    assertThrows(
        IllegalStateException.class, () -> service.reversePayment(PAYMENT, DECISION, REVIEWER));
    assertEquals(
        StudentAccountPayment.ReconciliationStatus.RECONCILED,
        storedPayments.getFirst().getReconciliationStatus());
    assertEquals(1, storedReceipts.size());
  }

  @Test
  void creditNotesPreserveLineEvidenceAndRequireIndependentPosting() {
    var draft = service.createCreditNote(creditCommand(), CAPTURER);
    assertEquals(FinanceCreditNote.Status.DRAFT, draft.status());
    assertEquals(BigDecimal.TEN, draft.transactionAmount());
    assertEquals(LINE, draft.lines().getFirst().invoiceLineId());
    assertEquals("Line corrected", draft.lines().getFirst().reason());
    assertThrows(
        IllegalStateException.class, () -> service.postCreditNote(CREDIT, DECISION, CAPTURER));
    assertThrows(
        IllegalStateException.class,
        () -> service.postCreditNote(CREDIT, new ControlledDecision("Stale", 1), REVIEWER));
    var posted = service.postCreditNote(CREDIT, DECISION, REVIEWER);
    assertEquals(FinanceCreditNote.Status.POSTED, posted.status());
    assertEquals(REVIEWER, posted.postedByUserId());
    assertEquals(NOW, posted.postedAt());
    assertThrows(
        IllegalStateException.class, () -> service.postCreditNote(CREDIT, DECISION, REVIEWER));
    captureUsd();
    service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.allocate(
                PAYMENT,
                new AllocatePayment(INVOICE, new BigDecimal("91"), "Exceeds credited invoice", 0),
                CAPTURER));
    assertEquals(
        new BigDecimal("90"),
        service
            .allocate(
                PAYMENT,
                new AllocatePayment(INVOICE, new BigDecimal("90"), "Remainder", 0),
                CAPTURER)
            .invoiceBaseAmount());
  }

  @ParameterizedTest
  @ValueSource(strings = {"duplicate", "missing", "other-invoice"})
  void creditNoteLinesMustBelongToTheSelectedInvoiceExactlyOnce(String mismatch) {
    CreateCreditNote command = creditCommand();
    if (mismatch.equals("duplicate"))
      command =
          new CreateCreditNote(
              INVOICE,
              LocalDate.of(2026, 8, 30),
              "Correction",
              List.of(command.lines().getFirst(), command.lines().getFirst()));
    if (mismatch.equals("missing"))
      when(invoiceLines.findByIdAndDeletedAtIsNull(LINE)).thenReturn(Optional.empty());
    if (mismatch.equals("other-invoice")) {
      FinanceInvoice another = mock(FinanceInvoice.class);
      when(another.getId()).thenReturn(new UUID(0, 99));
      when(invoiceLine.getInvoice()).thenReturn(another);
    }
    CreateCreditNote invalid = command;
    assertThrows(IllegalArgumentException.class, () -> service.createCreditNote(invalid, CAPTURER));
    verify(credits, never()).saveAndFlush(any());
  }

  @Test
  void statementBalancesOnlyPostedCreditsAndReconciledPaymentsInChronologicalOrder() {
    captureUsd();
    service.createCreditNote(creditCommand(), CAPTURER);
    assertEquals(new BigDecimal("100"), service.statement(ACCOUNT).account().baseBalance());
    service.postCreditNote(CREDIT, DECISION, REVIEWER);
    service.decidePayment(PAYMENT, "reconcile", DECISION, REVIEWER);
    var statement = service.statement(ACCOUNT);
    assertEquals(
        List.of("INVOICE", "CREDIT_NOTE", "PAYMENT"),
        statement.lines().stream().map(StatementLine::lineType).toList());
    assertEquals(new BigDecimal("-10"), statement.account().baseBalance());
    assertEquals(new BigDecimal("-10"), service.accounts().getFirst().baseBalance());
    StudentPaymentReversal reversed =
        new StudentPaymentReversal(
            "REV-002",
            storedPayments.getFirst(),
            REVIEWER,
            NOW.plusSeconds(60),
            "Reversed transfer");
    when(paymentReversals.findByPaymentIdAndDeletedAtIsNull(PAYMENT))
        .thenReturn(Optional.of(reversed));
    assertEquals(new BigDecimal("90"), service.statement(ACCOUNT).account().baseBalance());
    var register = service.register();
    assertEquals(1, register.payments().size());
    assertEquals(1, register.receipts().size());
    assertEquals(1, register.creditNotes().size());
    assertEquals("REC-001", register.receipts().getFirst().receiptNumber());
  }

  @Test
  void exchangeRateWorkflowRequiresIndependentApprovalAndValidVersions() {
    var draft =
        service.createRate(
            new CreateExchangeRate(" zwg ", new BigDecimal("0.04"), NOW, null, "Source", null),
            CAPTURER);
    assertEquals("DRAFT", draft.status());
    assertEquals("USD", draft.baseCurrencyCode());
    assertThrows(
        IllegalStateException.class, () -> service.moveRate(RATE, "approve", DECISION, CAPTURER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRate(RATE, "approve", new ControlledDecision("Stale", 1), REVIEWER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRate(RATE, "unknown", DECISION, REVIEWER));
    assertEquals("ACTIVE", service.moveRate(RATE, "approve", DECISION, REVIEWER).status());
    assertEquals("RETIRED", service.moveRate(RATE, "retire", DECISION, REVIEWER).status());
    when(rates.findAllByDeletedAtIsNullOrderByEffectiveFromDescSourceCurrencyCodeAsc())
        .thenReturn(List.of(exchangeRate));
    assertEquals(REVIEWER, service.register().exchangeRates().getFirst().retiredByUserId());
  }

  @Test
  void missingRecordsAndUnknownActionsNeverProduceFinancialEvidence() {
    UUID missing = new UUID(0, 99);
    assertThrows(IllegalArgumentException.class, () -> service.statement(missing));
    assertThrows(IllegalArgumentException.class, () -> service.applyRate(missing, 0, REVIEWER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRate(missing, "approve", DECISION, REVIEWER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.reverseAllocation(missing, DECISION, REVIEWER));
    assertThrows(
        IllegalArgumentException.class, () -> service.postCreditNote(missing, DECISION, REVIEWER));
    captureUsd();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.decidePayment(PAYMENT, "unknown", DECISION, REVIEWER));
    assertThrows(IllegalStateException.class, () -> service.applyRate(PAYMENT, 0, REVIEWER));
  }

  private void captureUsd() {
    service.capture(command(ACCOUNT, "USD"), CAPTURER);
  }

  private static CapturePayment command(UUID account, String currency) {
    return new CapturePayment(
        account,
        " Student Payer ",
        " bank ",
        " provider-1 ",
        StudentAccountPayment.PaymentChannel.BANK_TRANSFER,
        currency,
        new BigDecimal("100"),
        NOW,
        " fingerprint ");
  }

  private static CreateCreditNote creditCommand() {
    return new CreateCreditNote(
        INVOICE,
        LocalDate.of(2026, 8, 30),
        "Correction",
        List.of(new CreditNoteLineInput(LINE, BigDecimal.TEN, BigDecimal.TEN, " Line corrected ")));
  }

  private static <T extends AuditableEntity> T identify(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}

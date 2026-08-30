package zw.ac.uz.emhare.finance.billing;

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
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.billing.api.model.FinanceBillingApiModels.*;
import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.billing.infrastructure.persistence.*;
import zw.ac.uz.emhare.finance.catalogue.*;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceStudentDiscountApiModels.ResolveDiscount;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.payment.FinanceReferenceGenerator;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;
import zw.ac.uz.emhare.finance.student.infrastructure.persistence.StudentFinanceAccountRepository;

/**
 * @author Tinashe K
 */
class GovernedFinanceBillingServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(), APPROVER = UUID.randomUUID();
  private static final UUID PROGRAMME = UUID.randomUUID(),
      UNIT = UUID.randomUUID(),
      LEVEL = UUID.randomUUID();
  private final FinanceBillingEventRepository events = mock(FinanceBillingEventRepository.class);
  private final FinanceBillingEventScopeRepository scopes =
      mock(FinanceBillingEventScopeRepository.class);
  private final FinanceInvoiceRepository invoices = mock(FinanceInvoiceRepository.class);
  private final FinanceInvoiceLineRepository lines = mock(FinanceInvoiceLineRepository.class);
  private final FinanceBillingPolicyRepository policies =
      mock(FinanceBillingPolicyRepository.class);
  private final StudentFinanceAccountRepository accounts =
      mock(StudentFinanceAccountRepository.class);
  private final FinanceFeePricingResolver pricing = mock(FinanceFeePricingResolver.class);
  private final GovernedFinanceStudentDiscountService discounts =
      mock(GovernedFinanceStudentDiscountService.class);
  private final FinanceReferenceGenerator references = mock(FinanceReferenceGenerator.class);
  private final List<FinanceBillingEvent> storedEvents = new ArrayList<>();
  private final List<FinanceInvoice> storedInvoices = new ArrayList<>();
  private final List<FinanceInvoiceLine> storedLines = new ArrayList<>();
  private final List<FinanceBillingEventScope> storedScopes = new ArrayList<>();
  private final Map<UUID, List<FinanceBillingEventScope>> scopesByBillingEvent = new HashMap<>();
  private final List<FinanceBillingPolicy> storedPolicies = new ArrayList<>();
  private GovernedFinanceBillingService service;
  private StudentFinanceAccount account;
  private FinanceFeeCatalogue catalogue;
  private FinanceFeeRule rule;

  @BeforeEach
  void configureAuthoritativeStores() {
    service =
        new GovernedFinanceBillingService(
            events,
            scopes,
            invoices,
            lines,
            policies,
            accounts,
            pricing,
            discounts,
            references,
            Clock.fixed(NOW, ZoneOffset.UTC));
    account = account("R260001A");
    catalogue =
        identify(
            new FinanceFeeCatalogue(
                "TUITION",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "TUITION-REV",
                null,
                PREPARER));
    catalogue.activate(APPROVER, NOW, "Independent approval", 0);
    rule = approvedRule("USD", null);
    when(accounts.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));
    when(pricing.resolve(eq(catalogue.getId()), any(), any()))
        .thenReturn(new FinanceFeePricingResolver.ResolvedPrice(catalogue, rule));
    when(pricing.requireActiveCatalogue(catalogue.getId())).thenReturn(catalogue);
    when(references.nextBillingEventNumber())
        .thenAnswer(inv -> "BILL-" + (storedEvents.size() + 1));
    when(references.nextInvoiceNumber()).thenReturn("INV-1");
    when(events.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceBillingEvent event = inv.getArgument(0);
              if (event.getId() == null) {
                identify(event);
                storedEvents.add(event);
              }
              return event;
            });
    when(events.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                storedEvents.stream()
                    .filter(event -> event.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(events.findAllByDeletedAtIsNullOrderBySubmittedAtDescEventNumberDesc())
        .thenReturn(storedEvents);
    when(scopes.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<FinanceBillingEventScope> added = inv.getArgument(0);
              storedScopes.addAll(added);
              scopesByBillingEvent.put(storedEvents.getLast().getId(), added);
              return added;
            });
    when(scopes.findAllByBillingEventIdAndDeletedAtIsNullOrderByScopeDimensionAsc(any()))
        .thenAnswer(inv -> scopesByBillingEvent.getOrDefault(inv.getArgument(0), List.of()));
    when(invoices.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceInvoice invoice = identify(inv.getArgument(0));
              storedInvoices.add(invoice);
              return invoice;
            });
    when(invoices.findAllByDeletedAtIsNullOrderByPostedAtDescInvoiceNumberDesc())
        .thenReturn(storedInvoices);
    when(lines.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<FinanceInvoiceLine> added = inv.getArgument(0);
              storedLines.addAll(added);
              return added;
            });
    when(lines.findAllByInvoiceIdAndDeletedAtIsNullOrderByLineNumberAsc(any()))
        .thenAnswer(inv -> storedLines);
    when(policies.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceBillingPolicy policy = inv.getArgument(0);
              if (policy.getId() == null) {
                identify(policy);
                storedPolicies.add(policy);
              }
              return policy;
            });
    when(policies.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                storedPolicies.stream()
                    .filter(policy -> policy.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(policies.findAllByDeletedAtIsNullOrderByCodeAscPolicyVersionDesc())
        .thenReturn(storedPolicies);
  }

  @Test
  void createsAuditableUsdChargeWithoutDiscountWhenScopeIsIncomplete() {
    var result =
        service.create(
            command(List.of(scope(FinanceFeeRuleScope.Dimension.INSTITUTION, null, "INSTITUTION"))),
            PREPARER);
    assertEquals("R260001A", result.accountNumber());
    assertEquals(new BigDecimal("200.00"), result.transactionAmount());
    assertEquals(new BigDecimal("200.00"), result.baseAmount());
    assertEquals("USD", result.baseCurrencyCode());
    assertNull(result.exchangeRateId());
    assertNull(result.discountRuleId());
    assertEquals(FinanceBillingEvent.Status.PENDING_APPROVAL, result.status());
    assertEquals(PREPARER, result.preparedByUserId());
    assertEquals(NOW, result.submittedAt());
    assertEquals("INSTITUTION", result.scopes().getFirst().referenceCode());
    verifyNoInteractions(discounts);
  }

  @Test
  void snapshotsDiscountAndEffectiveForeignCurrencyEvidence() {
    ExchangeRate rate =
        identify(
            new ExchangeRate(
                "ZWG",
                new BigDecimal("0.04"),
                NOW.minusSeconds(1),
                null,
                "Central bank",
                "FX-1",
                PREPARER));
    FinanceFeeRule zwgRule = approvedRule("ZWG", rate);
    when(pricing.resolve(any(), any(), any()))
        .thenReturn(new FinanceFeePricingResolver.ResolvedPrice(catalogue, zwgRule));
    UUID discountId = UUID.randomUUID();
    when(discounts.resolve(any()))
        .thenReturn(
            Optional.of(
                new AppliedStudentDiscount(discountId, "UG-SUPPORT", new BigDecimal("12.5"))));
    var result = service.create(command(fullScope("3")), PREPARER);
    assertEquals(new BigDecimal("200.00"), result.grossTransactionAmount());
    assertEquals(new BigDecimal("25.00"), result.transactionDiscountAmount());
    assertEquals(new BigDecimal("175.00"), result.transactionAmount());
    assertEquals(new BigDecimal("7.00"), result.baseAmount());
    assertEquals(new BigDecimal("1.00"), result.baseDiscountAmount());
    assertEquals(rate.getId(), result.exchangeRateId());
    assertEquals(discountId, result.discountRuleId());
    verify(discounts)
        .resolve(new ResolveDiscount(catalogue.getId(), PROGRAMME, UNIT, LEVEL, "UG", "2.1", NOW));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "invalid"})
  void rejectsInvalidProgrammePeriodScopeBeforeSaving(String period) {
    assertThrows(
        IllegalArgumentException.class, () -> service.create(command(fullScope(period)), PREPARER));
    verify(events, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void doesNotInferDiscountApplicabilityWhenOneRequiredScopeIsAbsent(int omittedIndex) {
    List<BillingScopeInput> partial = new ArrayList<>(fullScope("2"));
    partial.remove(omittedIndex);
    assertNull(service.create(command(partial), PREPARER).discountRuleId());
    verifyNoInteractions(discounts);
  }

  @Test
  void refusesDuplicateAuthoritativeSourceLineAndDatabaseUniquenessRaces() {
    var command = command(fullScope("2"));
    var saved = service.create(command, PREPARER);
    when(events.findBySourceServiceAndSourceEventIdAndSourceLineReferenceAndDeletedAtIsNull(
            command.sourceService(), command.sourceEventId(), command.sourceLineReference()))
        .thenReturn(Optional.of(storedEvents.getFirst()));
    assertTrue(
        assertThrows(IllegalStateException.class, () -> service.create(command, PREPARER))
            .getMessage()
            .contains(saved.eventNumber()));
    when(events.findBySourceServiceAndSourceEventIdAndSourceLineReferenceAndDeletedAtIsNull(
            any(), any(), any()))
        .thenReturn(Optional.empty());
    doThrow(new DataIntegrityViolationException("source unique")).when(events).saveAndFlush(any());
    assertInstanceOf(
        DataIntegrityViolationException.class,
        assertThrows(IllegalStateException.class, () -> service.create(command, PREPARER))
            .getCause());
  }

  @Test
  void missingAccountAndMissingEventFailWithoutPosting() {
    when(accounts.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
    assertThrows(
        IllegalArgumentException.class, () -> service.create(command(fullScope("1")), PREPARER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.decide(UUID.randomUUID(), true, decision(0), APPROVER));
    verify(invoices, never()).saveAndFlush(any());
  }

  @Test
  void approvalRequiresFreshVersionIndependentOperatorAndReason() {
    var created = service.create(command(fullScope("1")), PREPARER);
    assertThrows(
        IllegalStateException.class,
        () -> service.decide(created.id(), true, decision(1), APPROVER));
    assertThrows(
        IllegalStateException.class,
        () -> service.decide(created.id(), true, decision(0), PREPARER));
    assertThrows(
        IllegalStateException.class, () -> service.decide(created.id(), true, decision(0), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.decide(created.id(), true, new BillingDecision(" ", 0), APPROVER));
    var approved = service.decide(created.id(), true, decision(0), APPROVER);
    assertEquals(APPROVER, approved.approvedByUserId());
    assertEquals(NOW, approved.approvedAt());
    assertThrows(
        IllegalStateException.class,
        () -> service.decide(created.id(), false, decision(0), APPROVER));
  }

  @Test
  void rejectionCannotBeInvoicedOrDecidedAgain() {
    var created = service.create(command(fullScope("1")), PREPARER);
    assertEquals(
        FinanceBillingEvent.Status.REJECTED,
        service.decide(created.id(), false, decision(0), APPROVER).status());
    assertThrows(
        IllegalStateException.class, () -> service.post(post(List.of(created.id())), APPROVER));
    assertThrows(IllegalStateException.class, () -> storedEvents.getFirst().markInvoiced(NOW));
    assertThrows(
        IllegalStateException.class,
        () -> service.decide(created.id(), true, decision(0), APPROVER));
  }

  @Test
  void postsOneImmutableInvoiceWithOrderedLinesAndAuditableDiscountTotals() {
    when(discounts.resolve(any()))
        .thenReturn(
            Optional.of(
                new AppliedStudentDiscount(UUID.randomUUID(), "SUPPORT", new BigDecimal("10"))));
    var first = service.create(command(fullScope("1")), PREPARER);
    var second = service.create(command(fullScope("2")), PREPARER);
    service.decide(first.id(), true, decision(0), APPROVER);
    service.decide(second.id(), true, decision(0), APPROVER);
    var invoice = service.post(post(List.of(second.id(), first.id())), APPROVER);
    assertEquals("POSTED", invoice.status());
    assertEquals(new BigDecimal("400.00"), invoice.grossTransactionAmount());
    assertEquals(new BigDecimal("40.00"), invoice.transactionDiscountAmount());
    assertEquals(new BigDecimal("360.00"), invoice.netTransactionAmount());
    assertEquals(invoice.netTransactionAmount(), invoice.netBaseAmount());
    assertEquals(
        List.of(1, 2), invoice.lines().stream().map(InvoiceLineSummary::lineNumber).toList());
    assertEquals(
        List.of(first.id(), second.id()).stream().sorted().toList(),
        invoice.lines().stream().map(InvoiceLineSummary::billingEventId).toList());
    assertTrue(
        storedEvents.stream()
            .allMatch(
                event ->
                    event.getStatus() == FinanceBillingEvent.Status.INVOICED
                        && NOW.equals(event.getInvoicedAt())));
    assertEquals("TUITION-REV", invoice.lines().getFirst().revenueAccountCode());
    assertEquals("SUPPORT", invoice.lines().getFirst().discountRuleCode());
    assertEquals(2, service.register().billingEvents().size());
    assertEquals(invoice, service.register().invoices().getFirst());
    assertThrows(
        IllegalStateException.class, () -> service.post(post(List.of(first.id())), APPROVER));
    assertEquals(1, storedInvoices.size());
  }

  @Test
  void invoiceRejectsDuplicateSourceIdsAndUnapprovedCharge() {
    var created = service.create(command(fullScope("1")), PREPARER);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.post(post(List.of(created.id(), created.id())), APPROVER));
    assertThrows(
        IllegalStateException.class, () -> service.post(post(List.of(created.id())), APPROVER));
    assertTrue(storedInvoices.isEmpty());
  }

  @Test
  void invoiceRejectsMixedStudentsAndCurrencies() {
    var created = service.create(command(fullScope("1")), PREPARER);
    service.decide(created.id(), true, decision(0), APPROVER);
    FinanceBillingEvent otherStudent = billingEvent(account("R260002B"), rule);
    storedEvents.add(otherStudent);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.post(post(List.of(created.id(), otherStudent.getId())), APPROVER));
    ExchangeRate rate =
        identify(
            new ExchangeRate(
                "ZWG",
                new BigDecimal("0.04"),
                NOW.minusSeconds(1),
                null,
                "Central bank",
                "FX-1",
                PREPARER));
    FinanceBillingEvent otherCurrency = billingEvent(account, approvedRule("ZWG", rate));
    storedEvents.add(otherCurrency);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.post(post(List.of(created.id(), otherCurrency.getId())), APPROVER));
    assertTrue(storedInvoices.isEmpty());
  }

  @Test
  void invoiceRequiresChronologicalDatesAndPostingReason() {
    var created = service.create(command(fullScope("1")), PREPARER);
    service.decide(created.id(), true, decision(0), APPROVER);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.post(
                new PostInvoice(
                    List.of(created.id()),
                    LocalDate.of(2026, 8, 30),
                    LocalDate.of(2026, 8, 29),
                    "Post"),
                APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.post(
                new PostInvoice(
                    List.of(created.id()),
                    LocalDate.of(2026, 8, 30),
                    LocalDate.of(2026, 9, 30),
                    " "),
                APPROVER));
    assertEquals(FinanceBillingEvent.Status.APPROVED, storedEvents.getFirst().getStatus());
  }

  @Test
  void createsVersionedPoliciesAndRequiresIndependentActivationThenRetirement() {
    var first =
        service.createPolicy(
            policyCommand(
                FinanceBillingPolicy.LineBasis.REGISTRATION,
                FinanceBillingPolicy.QuantityBasis.FIXED),
            PREPARER);
    assertEquals(1, first.policyVersion());
    when(policies.findFirstByCodeIgnoreCaseAndDeletedAtIsNullOrderByPolicyVersionDesc(
            "REG-TUITION"))
        .thenReturn(Optional.of(storedPolicies.getFirst()));
    assertEquals(
        2,
        service
            .createPolicy(
                policyCommand(
                    FinanceBillingPolicy.LineBasis.REGISTRATION,
                    FinanceBillingPolicy.QuantityBasis.FIXED),
                PREPARER)
            .policyVersion());
    assertThrows(
        IllegalStateException.class,
        () -> service.movePolicy(first.id(), "activate", decision(0), PREPARER));
    assertThrows(
        IllegalStateException.class,
        () -> service.movePolicy(first.id(), "activate", decision(1), APPROVER));
    assertEquals(
        FinanceBillingPolicy.Status.ACTIVE,
        service.movePolicy(first.id(), "activate", decision(0), APPROVER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.movePolicy(first.id(), "activate", decision(0), APPROVER));
    assertEquals(
        FinanceBillingPolicy.Status.RETIRED,
        service.movePolicy(first.id(), "retire", decision(0), APPROVER).status());
    assertEquals(2, service.register().billingPolicies().size());
    assertThrows(
        IllegalStateException.class,
        () -> service.movePolicy(first.id(), "retire", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.movePolicy(first.id(), "delete", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.movePolicy(UUID.randomUUID(), "activate", decision(0), APPROVER));
  }

  @Test
  void importsRegistrationAndPerModulePoliciesWithSourceAndAcademicScopeSnapshots() {
    var registrationPolicy =
        service.createPolicy(
            policyCommand(
                FinanceBillingPolicy.LineBasis.REGISTRATION,
                FinanceBillingPolicy.QuantityBasis.FIXED),
            PREPARER);
    var modulePolicy =
        service.createPolicy(
            policyCommand(
                FinanceBillingPolicy.LineBasis.REGISTERED_MODULE,
                FinanceBillingPolicy.QuantityBasis.MODULE_CREDIT_VALUE),
            PREPARER);
    service.movePolicy(registrationPolicy.id(), "activate", decision(0), APPROVER);
    service.movePolicy(modulePolicy.id(), "activate", decision(0), APPROVER);
    when(policies.findActivePolicies(any(), eq(NOW))).thenReturn(storedPolicies);
    when(accounts.findByStudentIdAndDeletedAtIsNull(account.getStudentId()))
        .thenReturn(Optional.of(account));
    var registration = registration(2);
    var imported = service.importConfirmedRegistration(registration, PREPARER);
    assertEquals(2, imported.size());
    assertEquals(new BigDecimal("1.0000"), imported.getFirst().quantity());
    assertEquals(new BigDecimal("15.0000"), imported.get(1).quantity());
    assertEquals(registration.eventId(), imported.getFirst().sourceEventId());
    assertEquals("REG-TUITION:V1:REGISTRATION", imported.getFirst().sourceLineReference());
    assertTrue(imported.get(1).description().contains("CSC101"));
    assertTrue(
        storedScopes.stream()
            .anyMatch(
                scope ->
                    scope.getScopeDimension() == FinanceFeeRuleScope.Dimension.MODULE
                        && "CSC101".equals(scope.getReferenceCode())));
    verify(discounts, times(2))
        .resolve(new ResolveDiscount(catalogue.getId(), PROGRAMME, UNIT, LEVEL, "UG", "1.2", NOW));
  }

  @Test
  void importRejectsUnsupportedSchemaAbsentPolicyAndMissingFinanceAccount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.importConfirmedRegistration(registration(1), PREPARER));
    assertThrows(
        IllegalStateException.class,
        () -> service.importConfirmedRegistration(registration(2), PREPARER));
    service.createPolicy(
        policyCommand(
            FinanceBillingPolicy.LineBasis.REGISTRATION, FinanceBillingPolicy.QuantityBasis.FIXED),
        PREPARER);
    when(policies.findActivePolicies(any(), any())).thenReturn(storedPolicies);
    assertThrows(
        IllegalStateException.class,
        () -> service.importConfirmedRegistration(registration(2), PREPARER));
    assertTrue(storedEvents.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void billingRejectsNonPositiveQuantities(String quantity) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FinanceBillingEvent(
                "BILL-X",
                "source",
                "type",
                UUID.randomUUID(),
                "AGGREGATE",
                UUID.randomUUID(),
                "line",
                account,
                catalogue,
                rule,
                "Tuition",
                new BigDecimal(quantity),
                NOW,
                PREPARER,
                NOW));
  }

  private CreateBillingEvent command(List<BillingScopeInput> requestedScopes) {
    return new CreateBillingEvent(
        "student-records-service",
        "registration-confirmed",
        UUID.randomUUID(),
        "REGISTRATION",
        UUID.randomUUID(),
        "tuition",
        account.getId(),
        catalogue.getId(),
        "Tuition charge",
        new BigDecimal("2"),
        NOW,
        requestedScopes);
  }

  private BillingScopeInput scope(FinanceFeeRuleScope.Dimension dimension, UUID id, String code) {
    return new BillingScopeInput(dimension, id, code, "Scope " + code);
  }

  private List<BillingScopeInput> fullScope(String period) {
    return List.of(
        scope(FinanceFeeRuleScope.Dimension.PROGRAMME, PROGRAMME, "CSC"),
        scope(FinanceFeeRuleScope.Dimension.ACADEMIC_UNIT, UNIT, "SCI"),
        scope(FinanceFeeRuleScope.Dimension.PROGRAMME_LEVEL, LEVEL, "UG"),
        scope(FinanceFeeRuleScope.Dimension.PROGRAMME_PERIOD, null, period));
  }

  private BillingDecision decision(long version) {
    return new BillingDecision("Independent evidence reviewed", version);
  }

  private PostInvoice post(List<UUID> eventIds) {
    return new PostInvoice(
        eventIds, LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 30), "Approved charges posted");
  }

  private CreateBillingPolicy policyCommand(
      FinanceBillingPolicy.LineBasis basis, FinanceBillingPolicy.QuantityBasis quantityBasis) {
    return new CreateBillingPolicy(
        "REG-TUITION",
        "Registration tuition",
        EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,
        catalogue.getId(),
        basis,
        quantityBasis,
        quantityBasis == FinanceBillingPolicy.QuantityBasis.FIXED ? BigDecimal.ONE : null,
        NOW,
        null);
  }

  private FinanceFeeRule approvedRule(String currency, ExchangeRate rate) {
    FinanceFeeRule result =
        identify(
            new FinanceFeeRule(
                catalogue,
                1,
                currency,
                new BigDecimal("100.00"),
                rate,
                rate == null ? new BigDecimal("100.00") : new BigDecimal("4.00"),
                NOW,
                null,
                PREPARER));
    result.approve(APPROVER, NOW, "Independent price approval", "INSTITUTION", 0);
    return result;
  }

  private FinanceBillingEvent billingEvent(
      StudentFinanceAccount studentAccount, FinanceFeeRule feeRule) {
    FinanceBillingEvent result =
        identify(
            new FinanceBillingEvent(
                "BILL-OTHER",
                "source",
                "event",
                UUID.randomUUID(),
                "REGISTRATION",
                UUID.randomUUID(),
                "line",
                studentAccount,
                catalogue,
                feeRule,
                "Tuition",
                BigDecimal.ONE,
                NOW,
                PREPARER,
                NOW));
    result.decide(true, APPROVER, NOW, "Approved evidence", 0);
    return result;
  }

  private StudentFinanceAccount account(String studentNumber) {
    return identify(
        new StudentFinanceAccount(
            new StudentFinanceAccountProvisioningRequestedEvent(
                UUID.randomUUID(),
                1,
                NOW,
                UUID.randomUUID(),
                UUID.randomUUID(),
                studentNumber,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student@example.test"),
            NOW));
  }

  private StudentRegistrationConfirmedEvent registration(int schemaVersion) {
    return new StudentRegistrationConfirmedEvent(
        UUID.randomUUID(),
        schemaVersion,
        NOW,
        UUID.randomUUID(),
        account.getStudentId(),
        account.getStudentNumber(),
        UUID.randomUUID(),
        PROGRAMME,
        UUID.randomUUID(),
        UNIT,
        "SCI",
        "Science",
        LEVEL,
        "UG",
        "Undergraduate",
        UUID.randomUUID(),
        "2026-S2",
        "Semester two",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 12, 1),
        2,
        List.of(
            new StudentRegistrationConfirmedEvent.RegisteredModule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CSC101",
                "Computing",
                "COMPULSORY",
                new BigDecimal("15"),
                new BigDecimal("50"))));
  }

  private static <T extends AuditableEntity> T identify(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

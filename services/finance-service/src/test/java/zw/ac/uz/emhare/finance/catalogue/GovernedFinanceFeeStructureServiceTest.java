package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.*;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ExchangeRateRepository;

/**
 * @author Tinashe K
 */
class GovernedFinanceFeeStructureServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(),
      APPROVER = UUID.randomUUID(),
      LEVEL = UUID.randomUUID();
  private final FinanceFeeStructureRepository structures =
      mock(FinanceFeeStructureRepository.class);
  private final FinanceFeeCatalogueRepository catalogues =
      mock(FinanceFeeCatalogueRepository.class);
  private final FinanceFeeRuleRepository rules = mock(FinanceFeeRuleRepository.class);
  private final FinanceFeeRuleScopeRepository scopes = mock(FinanceFeeRuleScopeRepository.class);
  private final FinanceFeeStructureAttachmentRepository attachments =
      mock(FinanceFeeStructureAttachmentRepository.class);
  private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
  private final List<FinanceFeeStructure> storedStructures = new ArrayList<>();
  private final List<FinanceFeeCatalogue> storedCatalogues = new ArrayList<>();
  private final List<FinanceFeeRule> storedRules = new ArrayList<>();
  private final List<FinanceFeeRuleScope> storedScopes = new ArrayList<>();
  private GovernedFinanceFeeStructureService service;

  @BeforeEach
  void configureStores() {
    service =
        new GovernedFinanceFeeStructureService(
            structures,
            catalogues,
            rules,
            scopes,
            attachments,
            rates,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(structures.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceFeeStructure value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value);
                storedStructures.add(value);
              }
              return value;
            });
    when(structures.findById(any()))
        .thenAnswer(
            inv ->
                storedStructures.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(structures.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                storedStructures.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(structures.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(storedStructures);
    when(structures.findAllByStatusAndDeletedAtIsNull(FinanceFeeStructure.Status.ACTIVE))
        .thenAnswer(
            inv ->
                storedStructures.stream()
                    .filter(value -> value.getStatus() == FinanceFeeStructure.Status.ACTIVE)
                    .toList());
    when(catalogues.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceFeeCatalogue value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value);
                storedCatalogues.add(value);
              }
              return value;
            });
    when(catalogues.findById(any()))
        .thenAnswer(
            inv ->
                storedCatalogues.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(rules.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceFeeRule value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value);
                storedRules.add(value);
              }
              return value;
            });
    when(rules.findAllByFeeStructureIdAndDeletedAtIsNullOrderByStructureLineNumberAsc(any()))
        .thenAnswer(
            inv ->
                storedRules.stream()
                    .filter(value -> value.getFeeStructure().getId().equals(inv.getArgument(0)))
                    .toList());
    when(scopes.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<FinanceFeeRuleScope> values = inv.getArgument(0);
              storedScopes.addAll(values);
              return values;
            });
    when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(any()))
        .thenAnswer(
            inv ->
                storedScopes.stream()
                    .filter(value -> value.getFeeRule().getId().equals(inv.getArgument(0)))
                    .toList());
  }

  @Test
  void createsWholeUsdStructureWithOrderedLinesAndCanonicalApplicability() {
    var created =
        service.create(
            command(
                "USD",
                List.of(line("TUIT", "Tuition", null), line("LIB", "Library", "Library access"))),
            PREPARER);
    assertEquals(FinanceFeeStructure.Status.DRAFT, created.status());
    assertEquals(
        List.of(1, 2), created.lines().stream().map(StructureLineSummary::lineNumber).toList());
    assertEquals("Tuition", created.lines().getFirst().description());
    assertEquals("Library access", created.lines().get(1).description());
    assertEquals(new BigDecimal("100.00"), created.lines().getFirst().baseAmount());
    assertNull(created.lines().getFirst().exchangeRateId());
    assertTrue(
        storedScopes.stream()
            .anyMatch(value -> "INSTITUTION:INSTITUTION".equals(value.canonicalPart())));
    assertTrue(
        storedScopes.stream()
            .anyMatch(value -> ("PROGRAMME_LEVEL:" + LEVEL).equals(value.canonicalPart())));
    assertEquals(new BigDecimal("200.00"), service.pricing(created.id()).totalTransactionAmount());
    assertEquals(created, service.register().structures().getFirst());
    verifyNoInteractions(rates);
  }

  @Test
  void reusesExistingDefinitionAndVersionsItsPriceWithoutMutatingCatalogue() {
    var original = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    FinanceFeeCatalogue catalogue = storedCatalogues.getFirst();
    when(rules.findFirstByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogue.getId()))
        .thenReturn(Optional.of(storedRules.getFirst()));
    var reused =
        service.create(
            command(
                "USD",
                List.of(
                    new LineInput(
                        catalogue.getId(),
                        null,
                        null,
                        " ",
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("125.00")))),
            PREPARER);
    assertEquals(
        original.lines().getFirst().feeCatalogueId(), reused.lines().getFirst().feeCatalogueId());
    assertEquals("Tuition", reused.lines().getFirst().description());
    assertEquals(2, storedRules.get(1).getRuleVersion());
    assertEquals(1, storedCatalogues.size());
  }

  @Test
  void keepsZwgUnratedUntilAnEffectiveRateExistsAndBlocksActivation() {
    var created = service.create(command("ZWG", List.of(line("TUIT", "Tuition", null))), PREPARER);
    assertNull(created.lines().getFirst().baseAmount());
    assertNull(created.lines().getFirst().exchangeRateId());
    assertEquals(FinanceFeeRule.RatingStatus.UNRATED, created.lines().getFirst().ratingStatus());
    assertEquals(FinanceFeeRule.Status.PENDING_RATE, created.lines().getFirst().status());
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () -> service.move(created.id(), "activate", decision(0), APPROVER))
            .getMessage()
            .contains("exchange-rate evidence"));
    assertEquals(FinanceFeeStructure.Status.DRAFT, storedStructures.getFirst().getStatus());
  }

  @Test
  void retainsEffectiveZwgRateEvidenceAndRoundsBasePriceToCents() {
    ExchangeRate rate =
        identify(
            new ExchangeRate(
                "ZWG",
                new BigDecimal("0.045678"),
                NOW.minusSeconds(1),
                null,
                "Approved bank",
                "FX-1",
                PREPARER));
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(rate));
    var created = service.create(command("ZWG", List.of(line("TUIT", "Tuition", null))), PREPARER);
    assertEquals(new BigDecimal("4.57"), created.lines().getFirst().baseAmount());
    assertEquals(rate.getId(), created.lines().getFirst().exchangeRateId());
    assertEquals(rate.getRateToBase(), created.lines().getFirst().exchangeRateToBase());
    assertEquals(FinanceFeeRule.RatingStatus.RATED, created.lines().getFirst().ratingStatus());
    assertEquals("USD", created.lines().getFirst().baseCurrencyCode());
  }

  @Test
  void rejectsAmbiguousRateEvidenceInsteadOfChoosingOne() {
    ExchangeRate rate =
        new ExchangeRate("ZWG", new BigDecimal("0.04"), NOW, null, "Bank", "FX", PREPARER);
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(rate, rate));
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () ->
                    service.create(
                        command("ZWG", List.of(line("TUIT", "Tuition", null))), PREPARER))
            .getMessage()
            .contains("Multiple effective"));
    assertTrue(storedRules.isEmpty());
  }

  @Test
  void independentActivationApprovesEveryLineAndRetirementPreservesHistoricalPrice() {
    var created =
        service.create(
            command("USD", List.of(line("TUIT", "Tuition", null), line("LIB", "Library", null))),
            PREPARER);
    var active = service.move(created.id(), "activate", decision(0), APPROVER);
    assertEquals(FinanceFeeStructure.Status.ACTIVE, active.status());
    assertEquals(APPROVER, active.activatedByUserId());
    assertEquals(NOW, active.activatedAt());
    assertTrue(
        active.lines().stream()
            .allMatch(value -> value.status() == FinanceFeeRule.Status.APPROVED));
    assertTrue(
        storedRules.stream()
            .allMatch(value -> value.getScopeSignature().contains("INSTITUTION:INSTITUTION")));
    assertEquals(
        active.id(),
        service
            .resolve(
                resolve(
                    FinanceFeeStructure.FeeContext.ACADEMIC,
                    NOW,
                    LEVEL,
                    "UG",
                    null,
                    List.of(),
                    null))
            .id());
    var retired = service.move(created.id(), "retire", decision(0), APPROVER);
    assertTrue(
        retired.lines().stream()
            .allMatch(value -> value.status() == FinanceFeeRule.Status.RETIRED));
    assertEquals(FinanceFeeStructure.Status.RETIRED, retired.status());
    assertEquals(new BigDecimal("200.00"), service.pricing(created.id()).totalTransactionAmount());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.resolve(
                resolve(
                    FinanceFeeStructure.FeeContext.ACADEMIC,
                    NOW,
                    LEVEL,
                    "UG",
                    null,
                    List.of(),
                    null)));
  }

  @Test
  void activationRequiresIndependentOperatorAndCompleteScopeEvidence() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "activate", decision(0), PREPARER));
    storedScopes.clear();
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () -> service.move(created.id(), "activate", decision(0), APPROVER))
            .getMessage()
            .contains("scope evidence"));
  }

  @Test
  void retiredDefinitionsCannotBeReactivatedThroughStructure() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    var catalogue = storedCatalogues.getFirst();
    catalogue.activate(APPROVER, NOW, "Independent approval", 0);
    catalogue.retire(APPROVER, NOW, "Retired definition", 0);
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () -> service.move(created.id(), "activate", decision(0), APPROVER))
            .getMessage()
            .contains("Retired line-item"));
  }

  @Test
  void activationReusesActiveDefinitionsAndRetirementSkipsAlreadyRetiredLines() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    storedCatalogues.getFirst().activate(APPROVER, NOW, "Independent approval", 0);
    service.move(created.id(), "activate", decision(0), APPROVER);
    storedRules.getFirst().retire(APPROVER, NOW, "Superseded pricing", 0);
    assertEquals(
        FinanceFeeStructure.Status.RETIRED,
        service.move(created.id(), "retire", decision(0), APPROVER).status());
  }

  @Test
  void transitionRejectsEmptyStructureMissingRecordAndUnknownAction() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.move(created.id(), "delete", decision(0), APPROVER));
    storedRules.clear();
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "activate", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.move(UUID.randomUUID(), "activate", decision(0), APPROVER));
    assertThrows(IllegalArgumentException.class, () -> service.pricing(UUID.randomUUID()));
  }

  @Test
  void rejectsDuplicateStructureCodesAndLineIdentities() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    when(structures.findByCodeIgnoreCaseAndDeletedAtIsNull("SCHEDULE"))
        .thenReturn(Optional.of(storedStructures.getFirst()));
    assertThrows(
        IllegalStateException.class,
        () -> service.create(command("USD", List.of(line("NEW", "New", null))), PREPARER));
    when(structures.findByCodeIgnoreCaseAndDeletedAtIsNull("SCHEDULE"))
        .thenReturn(Optional.empty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command(
                    "USD", List.of(line("TUIT", "Tuition", null), line(" tuit ", "Tuition", null))),
                PREPARER));
    UUID feeId = created.lines().getFirst().feeCatalogueId();
    LineInput existing =
        new LineInput(feeId, null, null, null, null, null, null, null, BigDecimal.ONE);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(command("USD", List.of(existing, existing)), PREPARER));
  }

  @Test
  void rejectsMissingDeletedOrDuplicatedCatalogueDefinitions() {
    LineInput missing =
        new LineInput(UUID.randomUUID(), null, null, null, null, null, null, null, BigDecimal.ONE);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(command("USD", List.of(missing)), PREPARER));
    service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    var catalogue = storedCatalogues.getFirst();
    catalogue.markDeleted(PREPARER);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command(
                    "USD",
                    List.of(
                        new LineInput(
                            catalogue.getId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            BigDecimal.ONE))),
                PREPARER));
    when(catalogues.findByCodeIgnoreCaseAndDeletedAtIsNull("TUIT"))
        .thenReturn(Optional.of(catalogue));
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () ->
                    service.create(
                        command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER))
            .getMessage()
            .contains("Select the existing"));
  }

  @ParameterizedTest
  @MethodSource("incompleteFeeDefinitions")
  void rejectsIncompleteNewDefinitionBeforeCreatingPricing(LineInput incomplete) {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(command("USD", List.of(incomplete)), PREPARER));
    assertTrue(storedRules.isEmpty());
  }

  static Stream<Arguments> incompleteFeeDefinitions() {
    return Stream.of(
        Arguments.of(
            new LineInput(
                null,
                null,
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                " ",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                "TUIT",
                null,
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                "TUIT",
                " ",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(null, "TUIT", "Tuition", null, null, "AR", "REV", null, BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                "TUIT",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                null,
                "REV",
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                "TUIT",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                " ",
                "REV",
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                "TUIT",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                null,
                null,
                BigDecimal.ONE)),
        Arguments.of(
            new LineInput(
                null,
                "TUIT",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                " ",
                null,
                BigDecimal.ONE)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACADEMIC_UNIT", "PROGRAMME", "APPLICATION", "ACCOMMODATION"})
  void persistsContextSpecificScopeAndResolvesIt(String scenario) {
    UUID reference = "APPLICATION".equals(scenario) ? LEVEL : UUID.randomUUID();
    FinanceFeeStructure.FeeContext context =
        "APPLICATION".equals(scenario)
            ? FinanceFeeStructure.FeeContext.APPLICATION
            : "ACCOMMODATION".equals(scenario)
                ? FinanceFeeStructure.FeeContext.ACCOMMODATION
                : FinanceFeeStructure.FeeContext.ACADEMIC;
    FinanceFeeStructure.ScopeType scopeType =
        "APPLICATION".equals(scenario)
            ? FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL
            : "ACCOMMODATION".equals(scenario)
                ? FinanceFeeStructure.ScopeType.GLOBAL
                : FinanceFeeStructure.ScopeType.valueOf(scenario);
    boolean global = scopeType == FinanceFeeStructure.ScopeType.GLOBAL;
    String code = "APPLICATION".equals(scenario) ? "UG" : "SCI";
    String name = "APPLICATION".equals(scenario) ? "Undergraduate" : "Science";
    var command =
        new CreateStructure(
            "SCHEDULE",
            "Schedule",
            null,
            context,
            scopeType,
            global ? null : reference,
            global ? null : code,
            global ? null : name,
            LEVEL,
            "UG",
            "Undergraduate",
            null,
            null,
            null,
            null,
            "APPLICATION".equals(scenario) ? "LOCAL" : null,
            "USD",
            NOW,
            null,
            List.of(line("TUIT", "Tuition", null)),
            null);
    var created = service.create(command, PREPARER);
    service.move(created.id(), "activate", decision(0), APPROVER);
    var resolution =
        service.resolve(
            resolve(
                context,
                NOW,
                LEVEL,
                "UG",
                reference,
                List.of(new AcademicUnitPathItem(reference, code, name)),
                "LOCAL"));
    assertEquals(created.id(), resolution.id());
    assertTrue(
        storedScopes.stream()
            .anyMatch(
                value ->
                    value.getScopeDimension().name().equals(global ? "GLOBAL" : scopeType.name())));
    if (context == FinanceFeeStructure.FeeContext.APPLICATION)
      assertTrue(
          storedScopes.stream()
              .anyMatch(
                  value ->
                      value.getScopeDimension()
                          == FinanceFeeRuleScope.Dimension.APPLICANT_CATEGORY));
  }

  @Test
  void resolvesOnlyCorrectEffectiveContextAndRejectsEqualPrecedence() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    service.move(created.id(), "activate", decision(0), APPROVER);
    assertThrows(
        IllegalStateException.class,
        () ->
            service.resolve(
                resolve(
                    FinanceFeeStructure.FeeContext.ACADEMIC,
                    NOW.minusSeconds(1),
                    LEVEL,
                    "UG",
                    null,
                    null,
                    null)));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.resolve(
                resolve(
                    FinanceFeeStructure.FeeContext.APPLICATION,
                    NOW,
                    LEVEL,
                    "UG",
                    null,
                    null,
                    null)));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.resolve(
                resolve(
                    FinanceFeeStructure.FeeContext.ACADEMIC,
                    NOW,
                    UUID.randomUUID(),
                    "PG",
                    null,
                    null,
                    null)));
    assertEquals(
        created.id(),
        service
            .resolve(
                resolve(
                    FinanceFeeStructure.FeeContext.ACADEMIC,
                    NOW,
                    UUID.randomUUID(),
                    "ug",
                    null,
                    null,
                    null))
            .id());
    var duplicate = service.create(command("USD", List.of(line("LIB", "Library", null))), PREPARER);
    service.move(duplicate.id(), "activate", decision(0), APPROVER);
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () ->
                    service.resolve(
                        resolve(
                            FinanceFeeStructure.FeeContext.ACADEMIC,
                            NOW,
                            LEVEL,
                            "UG",
                            null,
                            null,
                            null)))
            .getMessage()
            .contains("equal precedence"));
  }

  @Test
  void refusesLegacyAttachmentDiscountCapture() {
    var base = command("USD", List.of(line("TUIT", "Tuition", null)));
    var command =
        new CreateStructure(
            base.code(),
            base.name(),
            null,
            base.feeContext(),
            base.scopeType(),
            null,
            null,
            null,
            LEVEL,
            "UG",
            "Undergraduate",
            null,
            null,
            null,
            null,
            null,
            "USD",
            NOW,
            null,
            base.lines(),
            List.of(
                new AttachmentInput(
                    UUID.randomUUID(),
                    "CSC",
                    "Computing",
                    UUID.randomUUID(),
                    "S1",
                    "Semester",
                    1,
                    FinanceFeeStructureAttachment.DiscountType.PERCENTAGE,
                    BigDecimal.TEN,
                    "Authority")));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> service.create(command, PREPARER))
            .getMessage()
            .contains("standalone student-discount"));
    assertTrue(storedStructures.isEmpty());
  }

  @Test
  void historicalAttachmentIsReadableWithoutRestoringRetiredPricingBehaviour() {
    var created = service.create(command("USD", List.of(line("TUIT", "Tuition", null))), PREPARER);
    var evidence =
        identify(
            new FinanceFeeStructureAttachment(
                storedStructures.getFirst(),
                UUID.randomUUID(),
                "CSC",
                "Computing",
                UUID.randomUUID(),
                "2026-S1",
                "Semester one",
                1,
                FinanceFeeStructureAttachment.DiscountType.PERCENTAGE,
                BigDecimal.TEN,
                "Historical Council authority"));
    when(attachments.findAllByFeeStructureIdAndDeletedAtIsNullOrderByCreatedAtAsc(created.id()))
        .thenReturn(List.of(evidence));
    var historical = service.register().structures().getFirst().attachments().getFirst();
    assertEquals(evidence.getId(), historical.id());
    assertEquals("CSC", historical.programmeCode());
    assertEquals("Computing", historical.programmeName());
    assertEquals("2026-S1", historical.academicPeriodCode());
    assertEquals(new BigDecimal("10.00"), historical.discountAmount());
    assertEquals(new BigDecimal("90.00"), historical.discountedTotal());
    assertEquals("Historical Council authority", historical.discountReason());
    service.move(created.id(), "activate", decision(0), APPROVER);
    var resolved =
        service.resolve(
            resolve(
                FinanceFeeStructure.FeeContext.ACADEMIC,
                NOW,
                LEVEL,
                "UG",
                evidence.getProgrammeId(),
                List.of(),
                null));
    assertNull(resolved.selectedAttachment());
    assertEquals(new BigDecimal("100.00"), service.pricing(created.id()).totalTransactionAmount());
  }

  private CreateStructure command(String currency, List<LineInput> lines) {
    return new CreateStructure(
        "SCHEDULE",
        "Undergraduate fees",
        "Approved fee schedule",
        FinanceFeeStructure.FeeContext.ACADEMIC,
        FinanceFeeStructure.ScopeType.INSTITUTION,
        null,
        null,
        null,
        LEVEL,
        "UG",
        "Undergraduate",
        null,
        null,
        null,
        null,
        null,
        currency,
        NOW,
        null,
        lines,
        List.of());
  }

  private LineInput line(String code, String name, String description) {
    return new LineInput(
        null,
        code,
        name,
        description,
        FinanceFeeCatalogue.ChargeType.PROGRAMME,
        "AR",
        "REV",
        null,
        new BigDecimal("100.00"));
  }

  private StructureDecision decision(long version) {
    return new StructureDecision("Independent evidence approval", version);
  }

  private ResolveStructure resolve(
      FinanceFeeStructure.FeeContext context,
      Instant effectiveAt,
      UUID level,
      String levelCode,
      UUID programme,
      List<AcademicUnitPathItem> path,
      String category) {
    return new ResolveStructure(
        context, effectiveAt, null, programme, path, level, levelCode, null, category, null);
  }

  private static <T extends AuditableEntity> T identify(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

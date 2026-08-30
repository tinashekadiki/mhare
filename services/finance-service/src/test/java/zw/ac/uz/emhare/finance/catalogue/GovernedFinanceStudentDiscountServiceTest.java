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
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceStudentDiscountApiModels.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class GovernedFinanceStudentDiscountServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(),
      APPROVER = UUID.randomUUID(),
      LEVEL = UUID.randomUUID(),
      PROGRAMME = UUID.randomUUID(),
      UNIT = UUID.randomUUID();
  private final FinanceStudentDiscountRuleRepository rules =
      mock(FinanceStudentDiscountRuleRepository.class);
  private final FinanceFeeCatalogueRepository catalogues =
      mock(FinanceFeeCatalogueRepository.class);
  private final List<FinanceStudentDiscountRule> storedRules = new ArrayList<>();
  private final Map<UUID, FinanceFeeCatalogue> storedCatalogues = new HashMap<>();
  private GovernedFinanceStudentDiscountService service;
  private FinanceFeeCatalogue tuition;
  private FinanceFeeCatalogue library;

  @BeforeEach
  void configureStores() {
    service =
        new GovernedFinanceStudentDiscountService(
            rules, catalogues, Clock.fixed(NOW, ZoneOffset.UTC));
    tuition = catalogue("TUIT");
    library = catalogue("LIB");
    when(catalogues.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedCatalogues.get(inv.getArgument(0))));
    when(rules.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceStudentDiscountRule rule = inv.getArgument(0);
              if (rule.getId() == null) {
                identify(rule);
                storedRules.add(rule);
              }
              return rule;
            });
    when(rules.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                storedRules.stream()
                    .filter(rule -> rule.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(rules.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(storedRules);
    when(rules.findAllByStatusAndDeletedAtIsNull(FinanceStudentDiscountRule.Status.ACTIVE))
        .thenAnswer(
            inv ->
                storedRules.stream()
                    .filter(rule -> rule.getStatus() == FinanceStudentDiscountRule.Status.ACTIVE)
                    .toList());
  }

  @Test
  void createsAuditableInstitutionDiscountAndRequiresIndependentActivation() {
    var created =
        service.create(
            command(" support ", null, null, null, LEVEL, "ug", "1.1", NOW, null, "12.5"),
            PREPARER);
    assertEquals("SUPPORT", created.code());
    assertEquals("UG", created.programmeLevelCode());
    assertEquals(FinanceStudentDiscountRule.ScopeType.INSTITUTION, created.scopeType());
    assertEquals(0, created.academicUnitDepth());
    assertNull(created.feeCatalogueId());
    assertNull(created.feeCode());
    assertEquals(PREPARER, created.preparedByUserId());
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "activate", decision(0), PREPARER));
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "activate", decision(0), null));
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "activate", decision(1), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.move(created.id(), "activate", new DiscountDecision(" ", 0), APPROVER));
    var active = service.move(created.id(), "activate", decision(0), APPROVER);
    assertEquals(FinanceStudentDiscountRule.Status.ACTIVE, active.status());
    assertEquals(APPROVER, active.activatedByUserId());
    assertEquals(NOW, active.activatedAt());
    assertEquals(active, service.register().discounts().getFirst());
    assertEquals(
        new BigDecimal("12.5"),
        service
            .resolve(resolve(tuition.getId(), PROGRAMME, UNIT, LEVEL, "UG", "1.1", NOW))
            .orElseThrow()
            .percentage());
  }

  @Test
  void retirementRemovesDiscountFromPricingWithoutDeletingItsEvidence() {
    var created =
        createActive("SUPPORT", UNIT, PROGRAMME, tuition.getId(), LEVEL, "UG", "1.1", NOW, null);
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "activate", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.move(created.id(), "delete", decision(0), APPROVER));
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "retire", decision(1), APPROVER));
    assertThrows(
        NullPointerException.class, () -> service.move(created.id(), "retire", decision(0), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.move(created.id(), "retire", new DiscountDecision("", 0), APPROVER));
    var retired = service.move(created.id(), "retire", decision(0), APPROVER);
    assertEquals(FinanceStudentDiscountRule.Status.RETIRED, retired.status());
    assertEquals(tuition.getCode(), retired.feeCode());
    assertEquals("SCI", retired.academicUnitCode());
    assertEquals("CSC", retired.programmeCode());
    assertEquals(100, retired.academicUnitDepth());
    assertTrue(
        service
            .resolve(resolve(tuition.getId(), PROGRAMME, UNIT, LEVEL, "UG", "1.1", NOW))
            .isEmpty());
    assertEquals(1, service.register().discounts().size());
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "retire", decision(0), APPROVER));
  }

  @Test
  void draftCannotRetireAndUnknownDiscountFailsClearly() {
    var created =
        service.create(
            command("SUPPORT", null, null, null, LEVEL, "UG", "1.1", NOW, null, "10"), PREPARER);
    assertThrows(
        IllegalStateException.class,
        () -> service.move(created.id(), "retire", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.move(UUID.randomUUID(), "activate", decision(0), APPROVER));
  }

  @Test
  void rejectsDuplicateCodeAndMissingOrDeletedFeeDefinition() {
    var command = command("SUPPORT", null, null, null, LEVEL, "UG", "1.1", NOW, null, "10");
    service.create(command, PREPARER);
    when(rules.findByCodeIgnoreCaseAndDeletedAtIsNull("SUPPORT"))
        .thenReturn(Optional.of(storedRules.getFirst()));
    assertThrows(IllegalStateException.class, () -> service.create(command, PREPARER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command(
                    "MISSING", null, null, UUID.randomUUID(), LEVEL, "UG", "1.1", NOW, null, "10"),
                PREPARER));
    tuition.markDeleted(PREPARER);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command(
                    "DELETED", null, null, tuition.getId(), LEVEL, "UG", "1.1", NOW, null, "10"),
                PREPARER));
    var feeLineWithoutId =
        new CreateDiscount(
            "INCOMPLETE",
            "Support",
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            LEVEL,
            "UG",
            "Undergraduate",
            "1.1",
            FinanceStudentDiscountRule.TargetType.FEE_LINE,
            null,
            BigDecimal.TEN,
            "AUTH",
            NOW,
            null);
    assertThrows(IllegalArgumentException.class, () -> service.create(feeLineWithoutId, PREPARER));
  }

  @Test
  void explicitProgrammeAndFeeSpecificDiscountWinsOverBroaderScopes() {
    createActive("INSTITUTION", null, null, null, LEVEL, "UG", "1.1", NOW, null);
    createActive("UNIT", UNIT, null, null, LEVEL, "UG", "1.1", NOW, null);
    createActive("PROGRAMME", null, PROGRAMME, null, LEVEL, "UG", "1.1", NOW, null);
    var specific =
        createActive("TUITION", UNIT, PROGRAMME, tuition.getId(), LEVEL, "UG", "1.1", NOW, null);
    assertEquals(
        specific.id(),
        service
            .resolve(resolve(tuition.getId(), PROGRAMME, UNIT, LEVEL, "ug", "1.1", NOW))
            .orElseThrow()
            .id());
    assertEquals(
        "PROGRAMME",
        service
            .resolve(resolve(library.getId(), PROGRAMME, UNIT, LEVEL, "UG", "1.1", NOW))
            .orElseThrow()
            .code());
    assertEquals(
        "UNIT",
        service
            .resolve(resolve(tuition.getId(), UUID.randomUUID(), UNIT, LEVEL, "UG", "1.1", NOW))
            .orElseThrow()
            .code());
    assertEquals(
        "INSTITUTION",
        service
            .resolve(
                resolve(
                    tuition.getId(), UUID.randomUUID(), UUID.randomUUID(), LEVEL, "UG", "1.1", NOW))
            .orElseThrow()
            .code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "before",
        "at-end",
        "different-level-id",
        "different-level-code",
        "different-study-level",
        "different-programme",
        "different-unit",
        "different-fee"
      })
  void scopedDiscountCannotLeakOutsideItsApplicability(String mismatch) {
    createActive(
        "SCOPED", UNIT, PROGRAMME, tuition.getId(), LEVEL, "UG", "1.1", NOW, NOW.plusSeconds(60));
    var request =
        resolve(
            "different-fee".equals(mismatch) ? library.getId() : tuition.getId(),
            "different-programme".equals(mismatch) ? UUID.randomUUID() : PROGRAMME,
            "different-unit".equals(mismatch) ? UUID.randomUUID() : UNIT,
            "different-level-id".equals(mismatch) ? UUID.randomUUID() : LEVEL,
            "different-level-code".equals(mismatch) ? "PG" : "UG",
            "different-study-level".equals(mismatch) ? "1.2" : "1.1",
            "before".equals(mismatch)
                ? NOW.minusSeconds(1)
                : "at-end".equals(mismatch) ? NOW.plusSeconds(60) : NOW);
    assertTrue(service.resolve(request).isEmpty());
  }

  @Test
  void activationAndResolutionBothRejectEqualPriorityOverlaps() {
    createActive("FIRST", null, null, null, LEVEL, "UG", "1.1", NOW, null);
    var candidate =
        service.create(
            command("SECOND", null, null, null, LEVEL, "UG", "1.1", NOW, null, "10"), PREPARER);
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () -> service.move(candidate.id(), "activate", decision(0), APPROVER))
            .getMessage()
            .contains("equal priority"));
    // A legacy conflicting active row must fail closed during billing too.
    storedRules.get(1).activate(APPROVER, NOW, "Historical approval", 0);
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () ->
                    service.resolve(
                        resolve(tuition.getId(), PROGRAMME, UNIT, LEVEL, "UG", "1.1", NOW)))
            .getMessage()
            .contains("equal priority"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "later-window",
        "earlier-window",
        "other-level-id",
        "other-level-code",
        "other-study-level",
        "other-fee",
        "fee-versus-all",
        "different-priority",
        "other-programme",
        "other-unit",
        "same-programme-other-unit"
      })
  void activationAllowsNonOverlappingOrDifferentPriorityDiscounts(String distinction) {
    UUID firstUnit =
        "other-unit".equals(distinction) || "same-programme-other-unit".equals(distinction)
            ? UNIT
            : null;
    UUID firstProgramme =
        "other-programme".equals(distinction) || "same-programme-other-unit".equals(distinction)
            ? PROGRAMME
            : null;
    UUID firstFee =
        "other-fee".equals(distinction) || "fee-versus-all".equals(distinction)
            ? tuition.getId()
            : null;
    createActive(
        "FIRST", firstUnit, firstProgramme, firstFee, LEVEL, "UG", "1.1", NOW, NOW.plusSeconds(60));
    UUID nextUnit =
        "other-unit".equals(distinction) || "same-programme-other-unit".equals(distinction)
            ? UUID.randomUUID()
            : firstUnit;
    UUID nextProgramme =
        "other-programme".equals(distinction) || "different-priority".equals(distinction)
            ? UUID.randomUUID()
            : firstProgramme;
    UUID nextFee =
        "other-fee".equals(distinction)
            ? library.getId()
            : "fee-versus-all".equals(distinction) ? null : firstFee;
    Instant from =
        "later-window".equals(distinction)
            ? NOW.plusSeconds(60)
            : "earlier-window".equals(distinction) ? NOW.minusSeconds(60) : NOW;
    Instant until = "earlier-window".equals(distinction) ? NOW : null;
    var candidate =
        service.create(
            command(
                "SECOND",
                nextUnit,
                nextProgramme,
                nextFee,
                "other-level-id".equals(distinction) ? UUID.randomUUID() : LEVEL,
                "other-level-code".equals(distinction) ? "PG" : "UG",
                "other-study-level".equals(distinction) ? "1.2" : "1.1",
                from,
                until,
                "10"),
            PREPARER);
    assertEquals(
        FinanceStudentDiscountRule.Status.ACTIVE,
        service.move(candidate.id(), "activate", decision(0), APPROVER).status());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "same-programme",
        "programme-wildcard-unit",
        "candidate-wildcard-unit",
        "same-unit"
      })
  void scopedEqualPriorityOverlapsAreRejected(String scope) {
    UUID firstUnit = "programme-wildcard-unit".equals(scope) ? null : UNIT;
    UUID firstProgramme = "same-unit".equals(scope) ? null : PROGRAMME;
    createActive(
        "FIRST", firstUnit, firstProgramme, tuition.getId(), LEVEL, "UG", "1.1", NOW, null);
    UUID candidateUnit = "candidate-wildcard-unit".equals(scope) ? null : UNIT;
    var candidate =
        service.create(
            command(
                "SECOND",
                candidateUnit,
                firstProgramme,
                tuition.getId(),
                LEVEL,
                "UG",
                "1.1",
                NOW,
                null,
                "10"),
            PREPARER);
    assertThrows(
        IllegalStateException.class,
        () -> service.move(candidate.id(), "activate", decision(0), APPROVER));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "100", "100.01"})
  void rejectsPercentageOutsideStrictZeroToHundredRange(String percentage) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command("INVALID", null, null, null, LEVEL, "UG", "1.1", NOW, null, percentage),
                PREPARER));
    assertTrue(storedRules.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.1", "1.0", "1", "1-1", "invalid"})
  void rejectsMalformedStudyLevels(String studyLevel) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command("INVALID", null, null, null, LEVEL, "UG", studyLevel, NOW, null, "10"),
                PREPARER));
  }

  @Test
  void rejectsUnsupportedProgrammeLevelAndNonIncreasingEffectiveWindow() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command("INVALID", null, null, null, LEVEL, "DIPLOMA", "1.1", NOW, null, "10"),
                PREPARER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command("INVALID", null, null, null, LEVEL, "UG", "1.1", NOW, NOW, "10"),
                PREPARER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                command(
                    "INVALID",
                    null,
                    null,
                    null,
                    LEVEL,
                    "UG",
                    "1.1",
                    NOW,
                    NOW.minusSeconds(1),
                    "10"),
                PREPARER));
  }

  @ParameterizedTest
  @MethodSource("incompleteAcademicSnapshots")
  void refusesPartialAcademicSnapshots(
      UUID unitId,
      String unitCode,
      String unitName,
      UUID programmeId,
      String programmeCode,
      String programmeName,
      int depth) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FinanceStudentDiscountRule(
                "INVALID",
                "Support",
                unitId,
                unitCode,
                unitName,
                depth,
                programmeId,
                programmeCode,
                programmeName,
                LEVEL,
                "UG",
                "Undergraduate",
                "1.1",
                FinanceStudentDiscountRule.TargetType.ALL_FEES,
                null,
                BigDecimal.TEN,
                "AUTH",
                NOW,
                null,
                PREPARER));
  }

  static Stream<Arguments> incompleteAcademicSnapshots() {
    return Stream.of(
        Arguments.of(UNIT, null, "Science", null, null, null, 1),
        Arguments.of(UNIT, "SCI", null, null, null, null, 1),
        Arguments.of(null, "SCI", "Science", null, null, null, 1),
        Arguments.of(UNIT, "SCI", "Science", null, null, null, 0),
        Arguments.of(null, null, null, PROGRAMME, null, "Computing", 0),
        Arguments.of(null, null, null, PROGRAMME, "CSC", null, 0),
        Arguments.of(null, null, null, null, "CSC", "Computing", 0));
  }

  private DiscountSummary createActive(
      String code,
      UUID unit,
      UUID programme,
      UUID fee,
      UUID level,
      String levelCode,
      String studyLevel,
      Instant from,
      Instant until) {
    var created =
        service.create(
            command(code, unit, programme, fee, level, levelCode, studyLevel, from, until, "10"),
            PREPARER);
    return service.move(created.id(), "activate", decision(0), APPROVER);
  }

  private CreateDiscount command(
      String code,
      UUID unit,
      UUID programme,
      UUID fee,
      UUID level,
      String levelCode,
      String studyLevel,
      Instant from,
      Instant until,
      String percentage) {
    return new CreateDiscount(
        code,
        "Student support",
        unit,
        unit == null ? null : "sci",
        unit == null ? null : "Science",
        unit == null ? 0 : 1,
        programme,
        programme == null ? null : "csc",
        programme == null ? null : "Computing",
        level,
        levelCode,
        "Undergraduate",
        studyLevel,
        fee == null
            ? FinanceStudentDiscountRule.TargetType.ALL_FEES
            : FinanceStudentDiscountRule.TargetType.FEE_LINE,
        fee,
        new BigDecimal(percentage),
        "Council authority AUTH-1",
        from,
        until);
  }

  private ResolveDiscount resolve(
      UUID fee,
      UUID programme,
      UUID unit,
      UUID level,
      String levelCode,
      String studyLevel,
      Instant time) {
    return new ResolveDiscount(fee, programme, unit, level, levelCode, studyLevel, time);
  }

  private DiscountDecision decision(long version) {
    return new DiscountDecision("Independent authority reviewed", version);
  }

  private FinanceFeeCatalogue catalogue(String code) {
    FinanceFeeCatalogue catalogue =
        identify(
            new FinanceFeeCatalogue(
                code,
                code,
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                PREPARER));
    storedCatalogues.put(catalogue.getId(), catalogue);
    return catalogue;
  }

  private static <T extends AuditableEntity> T identify(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructure;

/**
 * @author Tinashe K
 */
class FinanceFeeStructureValidationTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID LEVEL = UUID.randomUUID(),
      PREPARER = UUID.randomUUID(),
      APPROVER = UUID.randomUUID();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "unsupported-scope",
        "academic-period-id",
        "academic-period-code",
        "academic-period-name",
        "programme-period",
        "applicant-category",
        "institution-reference-id",
        "institution-reference-code",
        "institution-reference-name",
        "missing-scoped-reference",
        "missing-scoped-name",
        "end-at-start",
        "end-before-start",
        "zero-period",
        "negative-period",
        "missing-context",
        "missing-scope",
        "missing-level",
        "missing-effective-start",
        "missing-preparer"
      })
  void academicStructureRejectsAmbiguousOrIncompleteScopeAndTimeEvidence(String scenario) {
    Fixture fixture = new Fixture();
    switch (scenario) {
      case "unsupported-scope" -> fixture.scope = FinanceFeeStructure.ScopeType.GLOBAL;
      case "academic-period-id" -> fixture.academicPeriodId = UUID.randomUUID();
      case "academic-period-code" -> fixture.academicPeriodCode = "2026-S1";
      case "academic-period-name" -> fixture.academicPeriodName = "Semester one";
      case "programme-period" -> fixture.programmePeriod = 1;
      case "applicant-category" -> fixture.category = "LOCAL";
      case "institution-reference-id" -> fixture.referenceId = UUID.randomUUID();
      case "institution-reference-code" -> fixture.referenceCode = "INST";
      case "institution-reference-name" -> fixture.referenceName = "Institution";
      case "missing-scoped-reference" -> {
        fixture.scope = FinanceFeeStructure.ScopeType.PROGRAMME;
        fixture.referenceName = "Computing";
      }
      case "missing-scoped-name" -> {
        fixture.scope = FinanceFeeStructure.ScopeType.PROGRAMME;
        fixture.referenceId = UUID.randomUUID();
      }
      case "end-at-start" -> fixture.until = NOW;
      case "end-before-start" -> fixture.until = NOW.minusSeconds(1);
      case "zero-period" -> fixture.programmePeriod = 0;
      case "negative-period" -> fixture.programmePeriod = -1;
      case "missing-context" -> fixture.context = null;
      case "missing-scope" -> fixture.scope = null;
      case "missing-level" -> fixture.level = null;
      case "missing-effective-start" -> fixture.from = null;
      case "missing-preparer" -> fixture.preparer = null;
      default -> fail("Unknown validation scenario");
    }
    assertThrows(IllegalArgumentException.class, fixture::create);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "different-level-id",
        "different-level-code",
        "different-level-name",
        "academic-period",
        "programme-period"
      })
  void applicationStructureCannotDisagreeWithItsProgrammeLevelSnapshot(String scenario) {
    Fixture fixture = new Fixture();
    fixture.context = FinanceFeeStructure.FeeContext.APPLICATION;
    fixture.scope = FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL;
    fixture.referenceId = LEVEL;
    fixture.referenceCode = "UG";
    fixture.referenceName = "Undergraduate";
    switch (scenario) {
      case "different-level-id" -> fixture.referenceId = UUID.randomUUID();
      case "different-level-code" -> fixture.referenceCode = "PG";
      case "different-level-name" -> fixture.referenceName = "Postgraduate";
      case "academic-period" -> fixture.academicPeriodId = UUID.randomUUID();
      case "programme-period" -> fixture.programmePeriod = 1;
      default -> fail("Unknown validation scenario");
    }
    assertThrows(IllegalArgumentException.class, fixture::create);
  }

  @ParameterizedTest
  @ValueSource(strings = {"academic-period", "programme-period", "applicant-category"})
  void accommodationGlobalStructureCannotCarryAcademicOrApplicantRestrictions(String scenario) {
    Fixture fixture = new Fixture();
    fixture.context = FinanceFeeStructure.FeeContext.ACCOMMODATION;
    fixture.scope = FinanceFeeStructure.ScopeType.GLOBAL;
    if ("academic-period".equals(scenario)) fixture.academicPeriodId = UUID.randomUUID();
    else if ("programme-period".equals(scenario)) fixture.programmePeriod = 1;
    else fixture.category = "LOCAL";
    assertThrows(IllegalArgumentException.class, fixture::create);
  }

  @Test
  void referencedAcademicScopeCanUseCodeWithoutUuidAndLifecycleIsVersionControlled() {
    Fixture fixture = new Fixture();
    fixture.scope = FinanceFeeStructure.ScopeType.ACADEMIC_UNIT;
    fixture.referenceCode = " sci ";
    fixture.referenceName = " Science ";
    var structure = fixture.create();
    assertEquals("SCI", structure.getScopeReferenceCode());
    assertEquals("Science", structure.getScopeReferenceName());
    assertThrows(
        IllegalStateException.class, () -> structure.activate(APPROVER, NOW, "Approval", 1));
    assertThrows(IllegalStateException.class, () -> structure.retire(APPROVER, NOW, "Retire", 0));
    assertThrows(
        IllegalStateException.class, () -> structure.activate(PREPARER, NOW, "Approval", 0));
    assertThrows(IllegalArgumentException.class, () -> structure.activate(APPROVER, NOW, " ", 0));
    structure.activate(APPROVER, NOW, "Independent approval", 0);
    assertThrows(
        IllegalStateException.class, () -> structure.activate(APPROVER, NOW, "Duplicate", 0));
    assertThrows(IllegalStateException.class, () -> structure.retire(APPROVER, NOW, "Retire", 1));
    assertThrows(IllegalArgumentException.class, () -> structure.retire(APPROVER, NOW, null, 0));
    structure.retire(APPROVER, NOW, "Superseded fee schedule", 0);
    assertEquals(FinanceFeeStructure.Status.RETIRED, structure.getStatus());
  }

  private static final class Fixture {
    FinanceFeeStructure.FeeContext context = FinanceFeeStructure.FeeContext.ACADEMIC;
    FinanceFeeStructure.ScopeType scope = FinanceFeeStructure.ScopeType.INSTITUTION;
    UUID referenceId;
    String referenceCode;
    String referenceName;
    UUID level = LEVEL;
    UUID academicPeriodId;
    String academicPeriodCode;
    String academicPeriodName;
    Integer programmePeriod;
    String category;
    Instant from = NOW;
    Instant until;
    UUID preparer = PREPARER;

    FinanceFeeStructure create() {
      return new FinanceFeeStructure(
          "SCHEDULE",
          "Fee schedule",
          null,
          context,
          scope,
          referenceId,
          referenceCode,
          referenceName,
          level,
          "UG",
          "Undergraduate",
          academicPeriodId,
          academicPeriodCode,
          academicPeriodName,
          programmePeriod,
          category,
          "USD",
          from,
          until,
          preparer);
    }
  }
}

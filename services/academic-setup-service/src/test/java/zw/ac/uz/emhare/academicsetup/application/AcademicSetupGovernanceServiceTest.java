package zw.ac.uz.emhare.academicsetup.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.*;
import zw.ac.uz.emhare.academicsetup.domain.model.*;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;

/**
 * @author Tinashe K
 */
class AcademicSetupGovernanceServiceTest extends AcademicServiceTestFixture {
  @Test
  void createsHierarchyLevelsInSequenceAndKeepsParentAndLegacySnapshots() {
    when(unitTypes.existsByLevelOrder(1)).thenReturn(true);
    var departmentType =
        service.createAcademicUnitType(new CreateAcademicUnitType("dept", "Department", 2, true));
    var department =
        service.createAcademicUnit(
            new CreateAcademicUnit(
                departmentType.id(),
                owner.getId(),
                "comp",
                "Computing Department",
                " F1 ",
                " D2 "));
    assertEquals("COMP", department.code());
    assertEquals(owner.getId(), department.parentId());
    assertEquals("F1", department.legacyFacultyCode());
    assertEquals("D2", department.legacyDepartmentCode());
    assertEquals(2, departmentType.levelOrder());
    var renamed =
        service.updateAcademicUnit(
            department.id(), new UpdateAcademicUnit("Computer Science", " ", " D3 ", 0));
    assertEquals("Computer Science", renamed.name());
    assertNull(renamed.legacyFacultyCode());
    assertEquals("D3", renamed.legacyDepartmentCode());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.updateAcademicUnit(
                department.id(), new UpdateAcademicUnit("Stale", null, null, 1)));
  }

  @Test
  void rootLevelMayBeCreatedWithoutPreviousLevel() {
    var root =
        service.createAcademicUnitType(new CreateAcademicUnitType("COLLEGE", "College", 1, false));
    var unit =
        service.createAcademicUnit(
            new CreateAcademicUnit(root.id(), null, "LAW", "Law", null, null));
    assertNull(unit.parentId());
    verify(unitTypes, never()).existsByLevelOrder(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"duplicate-code", "duplicate-level", "missing-previous"})
  void invalidHierarchyLevelDoesNotPersist(String invalid) {
    if ("duplicate-code".equals(invalid))
      when(unitTypes.existsByCodeIgnoreCase("DEPT")).thenReturn(true);
    if ("duplicate-level".equals(invalid)) when(unitTypes.existsByLevelOrder(2)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createAcademicUnitType(
                new CreateAcademicUnitType("DEPT", "Department", 2, true)));
    verify(unitTypes, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "non-root-without-parent",
        "skipped-parent-level",
        "duplicate-unit",
        "parent-owns-programme",
        "parent-owns-module"
      })
  void hierarchyCreationProtectsParentDepthAndLeafOwnership(String invalid) {
    var childType =
        save(
            new AcademicUnitType(
                "DEPT", "Department", "skipped-parent-level".equals(invalid) ? 3 : 2, true),
            storedUnitTypes);
    if ("duplicate-unit".equals(invalid))
      when(units.existsByCodeIgnoreCase("COMP")).thenReturn(true);
    if ("parent-owns-programme".equals(invalid))
      when(programmes.existsByOwningAcademicUnitId(owner.getId())).thenReturn(true);
    if ("parent-owns-module".equals(invalid))
      when(modules.existsByOwningAcademicUnitId(owner.getId())).thenReturn(true);
    var command =
        new CreateAcademicUnit(
            childType.getId(),
            "non-root-without-parent".equals(invalid) ? null : owner.getId(),
            "COMP",
            "Computing",
            null,
            null);
    Class<? extends RuntimeException> expected =
        invalid.startsWith("parent-owns")
            ? IllegalStateException.class
            : IllegalArgumentException.class;
    assertThrows(expected, () -> service.createAcademicUnit(command));
    verify(units, never()).saveAndFlush(any());
  }

  @Test
  void programmeReferenceCataloguesRetainStableCodesAndRejectOrderingConflicts() {
    var postgraduate =
        service.createProgrammeLevel(new CreateProgrammeLevel("PG", "Postgraduate", 2));
    assertEquals("PG", postgraduate.code());
    assertEquals(
        "Postgraduate awards",
        service
            .updateProgrammeLevel(
                postgraduate.id(), new UpdateProgrammeLevel("Postgraduate awards", 3, 0))
            .name());
    when(levels.existsBySortOrderAndIdNot(1, postgraduate.id())).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.updateProgrammeLevel(
                postgraduate.id(), new UpdateProgrammeLevel("Duplicate order", 1, 0)));
    var diploma = service.createProgrammeType(new CreateProgrammeType("DIPLOMA", "Diploma"));
    assertEquals(
        "Diploma award",
        service
            .updateProgrammeType(diploma.id(), new UpdateProgrammeType("Diploma award", 0))
            .name());
    assertThrows(
        IllegalStateException.class,
        () -> service.updateProgrammeType(diploma.id(), new UpdateProgrammeType("Stale", 1)));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.updateProgrammeLevel(
                postgraduate.id(), new UpdateProgrammeLevel("Stale", 4, 1)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"level-code", "level-order", "type-code"})
  void duplicateProgrammeReferenceDataFailsBeforePersistence(String duplicate) {
    if ("level-code".equals(duplicate)) when(levels.existsByCodeIgnoreCase("PG")).thenReturn(true);
    if ("level-order".equals(duplicate)) when(levels.existsBySortOrder(2)).thenReturn(true);
    if ("type-code".equals(duplicate)) {
      when(types.existsByCodeIgnoreCase("DIPLOMA")).thenReturn(true);
      assertThrows(
          IllegalArgumentException.class,
          () -> service.createProgrammeType(new CreateProgrammeType("DIPLOMA", "Diploma")));
    } else
      assertThrows(
          IllegalArgumentException.class,
          () -> service.createProgrammeLevel(new CreateProgrammeLevel("PG", "Postgraduate", 2)));
  }

  @Test
  void createsAndUpdatesProgrammeBeforeCurriculumApprovalControlsActivation() {
    var created = service.createProgramme(programmeCommand(owner.getId(), 6, 8));
    assertEquals(AcademicOfferingStatus.DRAFT, created.status());
    assertEquals("BIO", created.code());
    assertThrows(IllegalStateException.class, () -> service.activateProgramme(created.id(), 0));
    var updated =
        service.updateProgramme(
            created.id(),
            new UpdateProgramme(
                owner.getId(),
                type.getId(),
                level.getId(),
                "BIOSC",
                "Biological Sciences",
                "BSc Honours",
                6,
                10,
                " BIO-OLD ",
                "Approved descriptive correction",
                0));
    assertEquals("BIOSC", updated.code());
    assertEquals(10, updated.maximumDurationPeriods());
    var version =
        service.createProgrammeVersion(created.id(), new CreateProgrammeVersion("V1", START, END));
    var module = activeModule("BIO101");
    service.addCurriculumModule(
        version.id(), add(module.getId(), 1, 1, "Committee approved curriculum"));
    var approved = service.approveProgrammeVersion(version.id(), 0);
    assertEquals(ACTOR, approved.approvedByUserId());
    assertEquals(NOW, approved.approvedAt());
    assertEquals(new BigDecimal("15"), approved.totalCredits());
    assertEquals(1, approved.curriculumModuleCount());
    assertEquals(
        AcademicOfferingStatus.ACTIVE, service.activateProgramme(created.id(), 0).status());
    assertThrows(IllegalStateException.class, () -> service.activateProgramme(created.id(), 0));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.updateProgramme(
                created.id(),
                new UpdateProgramme(
                    owner.getId(),
                    type.getId(),
                    level.getId(),
                    "NEW",
                    "Changed identity",
                    "BSc",
                    6,
                    10,
                    null,
                    "Incorrect identity change",
                    0)));
    assertEquals(
        owner.getId(), service.resolveProgrammeHierarchy(created.id()).highestAcademicUnit().id());
  }

  @ParameterizedTest
  @ValueSource(strings = {"duplicate-code", "duration", "parent-owner", "not-leaf-owner"})
  void programmeCreationRequiresUniqueCodeValidDurationAndTrueLeafOwner(String invalid) {
    if ("duplicate-code".equals(invalid))
      when(programmes.existsByCodeIgnoreCase("BIO")).thenReturn(true);
    if ("parent-owner".equals(invalid))
      when(units.existsByParentId(owner.getId())).thenReturn(true);
    UUID ownerId = owner.getId();
    if ("not-leaf-owner".equals(invalid)) {
      var rootType = save(new AcademicUnitType("ROOT", "Root", 1, false), storedUnitTypes);
      ownerId =
          save(new AcademicUnit(rootType, null, "ROOT", "Root", null, null), storedUnits).getId();
    }
    var command = programmeCommand(ownerId, 6, "duration".equals(invalid) ? 5 : 8);
    assertThrows(IllegalArgumentException.class, () -> service.createProgramme(command));
    verify(programmes, never()).saveAndFlush(any());
  }

  @Test
  void programmeUpdateRequiresDurationAndFreshVersionAndHierarchyRequiresActiveProgramme() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.updateProgramme(
                programme.getId(),
                new UpdateProgramme(
                    owner.getId(),
                    type.getId(),
                    level.getId(),
                    "CSC",
                    "Computing",
                    "BSc",
                    8,
                    6,
                    null,
                    "Invalid duration change",
                    0)));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.updateProgramme(
                programme.getId(),
                new UpdateProgramme(
                    owner.getId(),
                    type.getId(),
                    level.getId(),
                    "CSC",
                    "Computing",
                    "BSc",
                    6,
                    8,
                    null,
                    "Stale descriptive change",
                    1)));
    assertThrows(
        IllegalStateException.class, () -> service.resolveProgrammeHierarchy(programme.getId()));
  }

  @Test
  void moduleLifecycleRetainsOwningUnitAndAllowsVersionedDescriptiveCorrection() {
    var created =
        service.createAcademicModule(
            new CreateAcademicModule(
                owner.getId(),
                " csc101 ",
                "Computing",
                "Introduction to computing",
                new BigDecimal("15"),
                1,
                " C101 "));
    assertEquals("CSC101", created.code());
    assertEquals(AcademicOfferingStatus.DRAFT, created.status());
    var updated =
        service.updateAcademicModule(
            created.id(),
            new UpdateAcademicModule(
                owner.getId(),
                "CSC102",
                "Computing foundations",
                "Revised introduction",
                new BigDecimal("20"),
                2,
                null,
                0));
    assertEquals(new BigDecimal("20"), updated.creditValue());
    assertEquals(2, updated.academicLevel());
    assertEquals(
        AcademicOfferingStatus.ACTIVE, service.activateAcademicModule(created.id(), 0).status());
    assertThrows(
        IllegalStateException.class, () -> service.activateAcademicModule(created.id(), 0));
    assertThrows(
        IllegalStateException.class, () -> service.activateAcademicModule(created.id(), 1));
    assertEquals(
        "Revised title",
        service
            .updateAcademicModule(
                created.id(),
                new UpdateAcademicModule(
                    owner.getId(),
                    "CSC102",
                    "Revised title",
                    "Revised description",
                    new BigDecimal("20"),
                    2,
                    " ",
                    0))
            .name());
  }

  @Test
  void moduleCreationAndUpdateRejectDuplicateCodes() {
    when(modules.existsByCodeIgnoreCase("CSC101")).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createAcademicModule(
                new CreateAcademicModule(
                    owner.getId(), "CSC101", "Computing", "Description", BigDecimal.TEN, 1, null)));
    var module = activeModule("CSC102");
    when(modules.existsByCodeIgnoreCaseAndIdNot("CSC103", module.getId())).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.updateAcademicModule(
                module.getId(),
                new UpdateAcademicModule(
                    owner.getId(),
                    "CSC103",
                    "Computing",
                    "Description",
                    BigDecimal.TEN,
                    1,
                    null,
                    0)));
  }

  @Test
  void curriculumCapturesAuditReasonResetsRequestContextAndTotalsCredits() {
    var version = draftVersion(programme);
    var module = activeModule("CSC101");
    AtomicReference<String> capturedReason = new AtomicReference<>();
    AtomicReference<String> capturedCorrelation = new AtomicReference<>();
    doAnswer(
            inv -> {
              capturedReason.set(EmhareRevisionContext.getReason().orElse(null));
              capturedCorrelation.set(EmhareRevisionContext.getCorrelationId().orElse(null));
              return save(inv.getArgument(0), storedCurricula);
            })
        .when(curricula)
        .saveAndFlush(any());
    EmhareRevisionContext.setRequestMetadata("request-1", null);
    var added =
        service.addCurriculumModule(
            version.getId(), add(module.getId(), 1, 1, " Committee authority "));
    assertEquals("Committee authority", capturedReason.get());
    assertEquals("request-1", capturedCorrelation.get());
    assertTrue(EmhareRevisionContext.getReason().isEmpty());
    assertEquals(Optional.of("request-1"), EmhareRevisionContext.getCorrelationId());
    assertEquals(module.getId(), added.moduleId());
    var updated =
        service.updateCurriculumModule(
            version.getId(),
            added.id(),
            new UpdateCurriculumModule(
                2,
                CurriculumModuleType.ELECTIVE,
                new BigDecimal("20"),
                new BigDecimal("60"),
                2,
                " Approved placement amendment ",
                0));
    assertEquals(2, updated.periodNumber());
    assertEquals(CurriculumModuleType.ELECTIVE, updated.moduleType());
    assertEquals("Approved placement amendment", capturedReason.get());
    assertTrue(EmhareRevisionContext.getReason().isEmpty());
    assertEquals(
        new BigDecimal("20"),
        service.programmeVersions(programme.getId()).getFirst().totalCredits());
    assertEquals(updated, service.curriculum(version.getId()).getFirst());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void curriculumUsesExplicitDefaultAuditReasonWhenNoReasonSupplied(String reason) {
    var version = draftVersion(programme);
    var module = activeModule("CSC101");
    AtomicReference<String> captured = new AtomicReference<>();
    doAnswer(
            inv -> {
              captured.set(EmhareRevisionContext.getReason().orElse(null));
              return save(inv.getArgument(0), storedCurricula);
            })
        .when(curricula)
        .saveAndFlush(any());
    service.addCurriculumModule(version.getId(), add(module.getId(), 1, 1, reason));
    assertEquals("Module added to the governed curriculum.", captured.get());
  }

  @Test
  void curriculumAuditContextIsClearedWhenPersistenceFails() {
    var version = draftVersion(programme);
    var module = activeModule("CSC101");
    doThrow(new IllegalStateException("Persistence failed")).when(curricula).saveAndFlush(any());
    assertThrows(
        IllegalStateException.class,
        () -> service.addCurriculumModule(version.getId(), add(module.getId(), 1, 1, null)));
    assertTrue(EmhareRevisionContext.getReason().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "duplicate-module",
        "duplicate-position",
        "inactive-module",
        "excess-duration",
        "retired-version"
      })
  void curriculumAdditionRejectsInvalidGovernedPlacement(String invalid) {
    var version = draftVersion(programme);
    var module =
        "inactive-module".equals(invalid)
            ? save(
                new AcademicModule(
                    owner, "CSC101", "Computing", "Description", BigDecimal.TEN, 1, null),
                storedModules)
            : activeModule("CSC101");
    if ("duplicate-module".equals(invalid))
      when(curricula.existsByProgrammeVersionIdAndAcademicModuleId(version.getId(), module.getId()))
          .thenReturn(true);
    if ("duplicate-position".equals(invalid))
      when(curricula.existsByProgrammeVersionIdAndSortOrder(version.getId(), 1)).thenReturn(true);
    if ("retired-version".equals(invalid)) {
      version.approve(ACTOR, NOW, 0);
      version.retire(END, 0);
    }
    Class<? extends RuntimeException> expected =
        "inactive-module".equals(invalid) || "retired-version".equals(invalid)
            ? IllegalStateException.class
            : IllegalArgumentException.class;
    assertThrows(
        expected,
        () ->
            service.addCurriculumModule(
                version.getId(),
                add(module.getId(), "excess-duration".equals(invalid) ? 9 : 1, 1, null)));
    assertTrue(storedCurricula.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"wrong-version", "duplicate-position", "excess-duration", "stale-version"})
  void curriculumUpdateRejectsCrossVersionOrStalePlacement(String invalid) {
    var version = draftVersion(programme);
    var module = activeModule("CSC101");
    var row = service.addCurriculumModule(version.getId(), add(module.getId(), 1, 1, null));
    var selectedVersion = "wrong-version".equals(invalid) ? draftVersion(programme) : version;
    if ("duplicate-position".equals(invalid))
      when(curricula.existsByProgrammeVersionIdAndSortOrderAndIdNot(version.getId(), 2, row.id()))
          .thenReturn(true);
    var command =
        new UpdateCurriculumModule(
            "excess-duration".equals(invalid) ? 9 : 2,
            CurriculumModuleType.ELECTIVE,
            BigDecimal.TEN,
            null,
            2,
            "Governed placement correction",
            "stale-version".equals(invalid) ? 1 : 0);
    Class<? extends RuntimeException> expected =
        "stale-version".equals(invalid)
            ? IllegalStateException.class
            : IllegalArgumentException.class;
    assertThrows(
        expected, () -> service.updateCurriculumModule(selectedVersion.getId(), row.id(), command));
    assertEquals(1, storedCurricula.get(row.id()).getPeriodNumber());
    assertTrue(EmhareRevisionContext.getReason().isEmpty());
  }

  @Test
  void programmeVersionApprovalRequiresNonemptyActiveCurriculumAndRetirementPreservesHistory() {
    var version = draftVersion(programme);
    assertThrows(
        IllegalStateException.class, () -> service.approveProgrammeVersion(version.getId(), 0));
    var inactive =
        save(
            new AcademicModule(
                owner, "CSC101", "Computing", "Description", BigDecimal.TEN, 1, null),
            storedModules);
    save(
        new CurriculumModule(
            version, inactive, 1, CurriculumModuleType.COMPULSORY, BigDecimal.TEN, null, 1),
        storedCurricula);
    assertThrows(
        IllegalStateException.class, () -> service.approveProgrammeVersion(version.getId(), 0));
    inactive.activate(0);
    service.approveProgrammeVersion(version.getId(), 0);
    assertEquals(
        ProgrammeVersionStatus.RETIRED,
        service.retireProgrammeVersion(version.getId(), 0, END).status());
    assertEquals(1, service.curriculum(version.getId()).size());
  }

  @Test
  void programmeVersionCodeAndEffectiveDatesAreValidatedAndEntryOptionsAreGoverned() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createProgrammeVersion(
                programme.getId(), new CreateProgrammeVersion("V1", END, START)));
    when(versions.existsByProgrammeIdAndVersionCodeIgnoreCase(programme.getId(), "V1"))
        .thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createProgrammeVersion(
                programme.getId(), new CreateProgrammeVersion("V1", START, null)));
    var version =
        service.createProgrammeVersion(
            programme.getId(), new CreateProgrammeVersion("V2", START, null));
    service.configureProgrammeEntryOptions(
        version.id(),
        new ConfigureProgrammeEntryOptions(
            1,
            2,
            List.of(
                new ProgrammeEntryOptionInput("AI", "Artificial Intelligence", "AI stream", 2),
                new ProgrammeEntryOptionInput("SE", "Software Engineering", null, 1)),
            0));
    var domain = storedVersions.get(version.id());
    assertEquals(
        List.of("SE", "AI"),
        domain.getEntryOptions().stream().map(ProgrammeEntryOption::getCode).toList());
    assertEquals(1, domain.getMinimumEntryOptionSelections());
    assertEquals(2, domain.getMaximumEntryOptionSelections());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.configureProgrammeEntryOptions(
                version.id(), new ConfigureProgrammeEntryOptions(0, 0, List.of(), 1)));
  }

  private CreateProgramme programmeCommand(UUID ownerId, int minimum, int maximum) {
    return new CreateProgramme(
        ownerId, type.getId(), level.getId(), "BIO", "Biology", "BSc", minimum, maximum, null);
  }

  private AddCurriculumModule add(UUID moduleId, int period, int position, String reason) {
    return new AddCurriculumModule(
        moduleId,
        period,
        CurriculumModuleType.COMPULSORY,
        new BigDecimal("15"),
        new BigDecimal("50"),
        position,
        reason);
  }
}

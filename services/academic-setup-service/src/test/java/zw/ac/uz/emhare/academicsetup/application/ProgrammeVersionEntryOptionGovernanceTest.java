package zw.ac.uz.emhare.academicsetup.application;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeEntryOption;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion.EntryOptionDefinition;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersionStatus;

/**
 * @author Tinashe K
 */
class ProgrammeVersionEntryOptionGovernanceTest extends AcademicServiceTestFixture {

  @Test
  void replacementRebuildsDraftOptionsInChoiceOrderAndCanRemoveOptionalSpecializations() {
    ProgrammeVersion version = draftVersion(programme);
    version.configureEntryOptions(1, 2, List.of(option("AI", 2), option("SE", 1)), 0);
    version.configureEntryOptions(
        0, 1, List.of(new EntryOptionDefinition(" ds ", " Data Science ", " Analytics ", 1)), 0);

    assertEquals(0, version.getMinimumEntryOptionSelections());
    assertEquals(1, version.getMaximumEntryOptionSelections());
    assertEquals(1, version.getEntryOptions().size());
    ProgrammeEntryOption replacement = version.getEntryOptions().getFirst();
    assertAll(
        () -> assertEquals("DS", replacement.getCode()),
        () -> assertEquals("Data Science", replacement.getName()),
        () -> assertEquals("Analytics", replacement.getDescription()),
        () -> assertSame(version, replacement.getProgrammeVersion()),
        () -> assertTrue(replacement.isActive()));

    version.configureEntryOptions(0, 0, null, 0);
    assertTrue(version.getEntryOptions().isEmpty());
    assertEquals(0, version.getMaximumEntryOptionSelections());
  }

  @ParameterizedTest
  @CsvSource({"-1,1", "2,1", "0,3"})
  void inconsistentSelectionLimitsDoNotReplacePreviouslyGovernedOptions(int minimum, int maximum) {
    ProgrammeVersion version = draftVersion(programme);
    version.configureEntryOptions(1, 1, List.of(option("AI", 1)), 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            version.configureEntryOptions(
                minimum, maximum, List.of(option("SE", 1), option("DS", 2)), 0));
    assertEquals(
        List.of("AI"),
        version.getEntryOptions().stream().map(ProgrammeEntryOption::getCode).toList());
    assertEquals(1, version.getMinimumEntryOptionSelections());
    assertEquals(1, version.getMaximumEntryOptionSelections());
  }

  @ParameterizedTest
  @ValueSource(strings = {"duplicate-code", "duplicate-sort-order"})
  void duplicateNormalizedCodesOrDisplayPositionsCannotBePublishedAsChoices(String duplicate) {
    ProgrammeVersion version = draftVersion(programme);
    List<EntryOptionDefinition> definitions =
        "duplicate-code".equals(duplicate)
            ? List.of(option(" ai ", 1), option("AI", 2))
            : List.of(option("AI", 1), option("SE", 1));
    assertThrows(
        IllegalArgumentException.class, () -> version.configureEntryOptions(0, 1, definitions, 0));
    assertTrue(version.getEntryOptions().isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void entryOptionCodeIsRequired(String code) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProgrammeEntryOption(
                draftVersion(programme), code, "Artificial Intelligence", null, 1));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void entryOptionDisplayNameIsRequired(String name) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProgrammeEntryOption(draftVersion(programme), "AI", name, null, 1));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void absentOptionalDescriptionDoesNotCreateMeaninglessCatalogueText(String description) {
    ProgrammeVersion version = draftVersion(programme);
    version.configureEntryOptions(
        0,
        1,
        List.of(new EntryOptionDefinition("AI", "Artificial Intelligence", description, 1)),
        0);
    assertNull(version.getEntryOptions().getFirst().getDescription());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void choiceDisplayOrderMustBePositive(int order) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProgrammeEntryOption(
                draftVersion(programme), "AI", "Artificial Intelligence", null, order));
  }

  @Test
  void approvedOptionsAndApprovalEvidenceRemainFrozenUntilVersionRetirement() {
    ProgrammeVersion version = draftVersion(programme);
    version.configureEntryOptions(1, 1, List.of(option("AI", 1)), 0);
    version.approve(ACTOR, NOW, 0);
    assertThrows(IllegalStateException.class, () -> version.configureEntryOptions(0, 0, null, 0));
    assertThrows(IllegalStateException.class, () -> version.approve(ACTOR, NOW.plusSeconds(1), 0));
    assertThrows(IllegalArgumentException.class, () -> version.retire(START.minusDays(1), 0));
    assertEquals(ProgrammeVersionStatus.APPROVED, version.getStatus());
    assertNull(version.getEffectiveTo());
    version.retire(START, 0);
    assertEquals(ProgrammeVersionStatus.RETIRED, version.getStatus());
    assertEquals(START, version.getEffectiveTo());
    assertEquals(ACTOR, version.getApprovedByUserId());
    assertEquals(NOW, version.getApprovedAt());
    assertEquals("AI", version.getEntryOptions().getFirst().getCode());
    assertThrows(IllegalStateException.class, () -> version.retire(END, 0));
  }

  @Test
  void draftCannotBeRetiredBeforeItsCurriculumIsApproved() {
    ProgrammeVersion version = draftVersion(programme);
    assertThrows(IllegalStateException.class, () -> version.retire(END, 0));
    assertEquals(ProgrammeVersionStatus.DRAFT, version.getStatus());
    assertNull(version.getEffectiveTo());
  }

  @ParameterizedTest
  @ValueSource(strings = {"configure", "approve", "retire"})
  void optimisticVersionPreventsConcurrentProgrammeVersionChanges(String operation) {
    ProgrammeVersion version = draftVersion(programme);
    if ("retire".equals(operation)) version.approve(ACTOR, NOW, 0);
    ProgrammeVersionStatus initialStatus = version.getStatus();
    assertThrows(
        IllegalStateException.class,
        () -> {
          switch (operation) {
            case "configure" -> version.configureEntryOptions(0, 0, null, 1);
            case "approve" -> version.approve(ACTOR, NOW, 1);
            case "retire" -> version.retire(LocalDate.of(2026, 12, 31), 1);
            default -> fail("Unexpected operation");
          }
        });
    assertEquals(initialStatus, version.getStatus());
    assertTrue(version.getEntryOptions().isEmpty());
    assertNull(version.getEffectiveTo());
  }

  private static EntryOptionDefinition option(String code, int position) {
    return new EntryOptionDefinition(code, code.trim(), null, position);
  }
}

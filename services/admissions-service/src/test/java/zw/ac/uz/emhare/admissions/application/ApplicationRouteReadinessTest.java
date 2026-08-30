package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest.DocumentInput;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest.ProgrammeMappingInput;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest.SectionInput;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;

/**
 * Route activation and audited reconciliation retain the intended requirements. @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class ApplicationRouteReadinessTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private ApplicationTypeRepository types;
  @Mock private ApplicationTypeProgrammeMappingRepository mappings;
  @Mock private ApplicationTypeSectionRepository sections;
  @Mock private ApplicationTypeDocumentRequirementRepository documents;
  @Mock private ApplicationTypeDocumentRequirementCategoryRepository categories;
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  @InjectMocks private ApplicationRouteConfigurationService service;
  private ApplicationType route;
  private final UUID actor = UUID.randomUUID();
  private final List<ApplicationTypeProgrammeMapping> mappingRows = new ArrayList<>();
  private final List<ApplicationTypeSection> sectionRows = new ArrayList<>();
  private final List<ApplicationTypeDocumentRequirement> documentRows = new ArrayList<>();
  private final List<ApplicationTypeDocumentRequirementCategory> categoryRows = new ArrayList<>();

  @BeforeEach
  void setUp() {
    route = identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false, false));
    route.recordFeeFreeDecision(actor, "Authorised fee exemption", NOW);
    lenient()
        .when(types.findById(any()))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0).equals(route.getId())
                    ? Optional.of(route)
                    : Optional.empty());
    lenient().when(types.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    mappingRows.add(
        new ApplicationTypeProgrammeMapping(route, UUID.randomUUID(), "BSC", "Science"));
    List.of(
            "PERSONAL_DETAILS",
            "NEXT_OF_KIN",
            "QUALIFICATIONS",
            "PROGRAMME_CHOICES",
            "PAYMENT",
            "REVIEW_DECLARATION")
        .forEach(code -> sectionRows.add(section(code, true, 0)));
    documentRows.add(
        identified(
            new ApplicationTypeDocumentRequirement(
                route, "IDENTITY", "Identity evidence", true, 1)));
    lenient()
        .when(mappings.findAllByApplicationTypeIdAndDeletedAtIsNull(any()))
        .thenReturn(mappingRows);
    lenient()
        .when(
            mappings
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
                    any()))
        .thenAnswer(
            invocation ->
                mappingRows.stream().filter(ApplicationTypeProgrammeMapping::isActive).toList());
    lenient()
        .when(sections.findAllByApplicationTypeIdAndDeletedAtIsNull(any()))
        .thenReturn(sectionRows);
    lenient()
        .when(
            sections.findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
                any()))
        .thenAnswer(
            invocation -> sectionRows.stream().filter(ApplicationTypeSection::isActive).toList());
    lenient()
        .when(documents.findAllByApplicationTypeIdAndDeletedAtIsNull(any()))
        .thenReturn(documentRows);
    lenient()
        .when(
            documents
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                    any()))
        .thenAnswer(
            invocation ->
                documentRows.stream()
                    .filter(ApplicationTypeDocumentRequirement::isActive)
                    .toList());
    lenient()
        .when(categories.findAllByDocumentRequirementIdAndDeletedAtIsNull(any()))
        .thenReturn(categoryRows);
    lenient()
        .when(categories.findAllByDocumentRequirementIdInAndDeletedAtIsNull(any()))
        .thenAnswer(invocation -> categoryRows.stream().filter(row -> !row.isDeleted()).toList());
    lenient()
        .when(mappings.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              List<ApplicationTypeProgrammeMapping> updated = new ArrayList<>();
              ((Iterable<ApplicationTypeProgrammeMapping>) invocation.getArgument(0))
                  .forEach(updated::add);
              mappingRows.clear();
              mappingRows.addAll(updated);
              return updated;
            });
    lenient()
        .when(sections.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              List<ApplicationTypeSection> updated = new ArrayList<>();
              ((Iterable<ApplicationTypeSection>) invocation.getArgument(0)).forEach(updated::add);
              sectionRows.clear();
              sectionRows.addAll(updated);
              return updated;
            });
    lenient()
        .when(documents.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              List<ApplicationTypeDocumentRequirement> updated = new ArrayList<>();
              ((Iterable<ApplicationTypeDocumentRequirement>) invocation.getArgument(0))
                  .forEach(
                      row -> {
                        if (row.getId() == null) identified(row);
                        updated.add(row);
                      });
              documentRows.clear();
              documentRows.addAll(updated);
              return updated;
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PERSONAL_DETAILS",
        "NEXT_OF_KIN",
        "QUALIFICATIONS",
        "PROGRAMME_CHOICES",
        "PAYMENT",
        "REVIEW_DECLARATION"
      })
  void everyCommonSectionMustBeRequiredBeforeActivation(String omitted) {
    sectionRows.removeIf(section -> section.getSectionCode().equals(omitted));
    assertThat(service.configuration(route.getId()).readinessBlockers())
        .contains(omitted + " must be a required section");
  }

  @ParameterizedTest
  @CsvSource({"POSTGRAD,2", "MBA,3", "EDUCATION,3"})
  void postgraduateRoutesEnforceTheirExactReferenceThresholdAndEvidence(
      String code, int references) {
    route = identified(new ApplicationType(code, code + " route", true, true, false));
    route.recordFeeFreeDecision(actor, "Authorised fee exemption", NOW);
    assertThat(service.configuration(route.getId()).readinessBlockers())
        .contains(
            "REFEREES must require exactly " + references + " completed responses",
            "EMPLOYMENT_HISTORY must be required");
    sectionRows.add(section("REFEREES", true, references));
    sectionRows.add(section("EMPLOYMENT_HISTORY", true, 1));
    if (code.equals("MBA")) {
      assertThat(service.configuration(route.getId()).readinessBlockers())
          .contains(
              "PRIOR_UZ_STUDY must be required", "PROFESSIONAL_ACHIEVEMENTS must be required");
      sectionRows.add(section("PRIOR_UZ_STUDY", true, 1));
      sectionRows.add(section("PROFESSIONAL_ACHIEVEMENTS", true, 1));
    }
    assertThat(service.configuration(route.getId()).readyForActivation()).isTrue();
    sectionRows.stream()
        .filter(section -> section.getSectionCode().equals("REFEREES"))
        .findFirst()
        .orElseThrow()
        .configure("Referees", true, true, references + 1, 1);
    assertThat(service.configuration(route.getId()).readyForActivation()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"programme", "document", "optionalCommon", "feePolicy"})
  void routeReadinessReportsMissingManagedConfiguration(String missing) {
    if (missing.equals("programme")) mappingRows.clear();
    if (missing.equals("document")) documentRows.clear();
    if (missing.equals("optionalCommon"))
      sectionRows.get(0).configure("Personal details", false, false, 0, 1);
    if (missing.equals("feePolicy"))
      route = identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false, false));
    ApplicationRouteConfigurationSummary result = service.configuration(route.getId());
    assertThat(result.readyForActivation()).isFalse();
    assertThat(result.readinessBlockers()).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"programme", "section", "document", "unsupported"})
  void ambiguousOrUnsupportedRouteInputsAreRejectedBeforeRepositoryMutation(String invalid) {
    ConfigureApplicationRouteRequest valid = request(false);
    List<ProgrammeMappingInput> programmeInputs = new ArrayList<>(valid.programmes());
    List<SectionInput> sectionInputs = new ArrayList<>(valid.sections());
    List<DocumentInput> documentInputs = new ArrayList<>(valid.documents());
    if (invalid.equals("programme")) programmeInputs.add(programmeInputs.get(0));
    if (invalid.equals("section"))
      sectionInputs.add(new SectionInput(" personal_details ", "Duplicate", true, false, 0, 1));
    if (invalid.equals("document"))
      documentInputs.add(
          new DocumentInput(" identity ", "Duplicate", true, "SUPPORTING_DOCUMENTS", List.of(), 1));
    if (invalid.equals("unsupported"))
      sectionInputs.add(new SectionInput("CUSTOM_SCRIPT", "Unsupported", true, false, 0, 1));
    ConfigureApplicationRouteRequest bad =
        new ConfigureApplicationRouteRequest(
            programmeInputs,
            sectionInputs,
            documentInputs,
            false,
            null,
            false,
            "Configure route evidence",
            0);
    assertThatThrownBy(() -> service.configure(route.getId(), actor, bad))
        .isInstanceOf(IllegalArgumentException.class);
    verify(mappings, never()).saveAllAndFlush(any());
    verify(types, never()).saveAndFlush(any());
  }

  @Test
  void incompleteRouteCanBeSavedInactiveButActivationFails() {
    sectionRows.clear();
    assertThat(service.configure(route.getId(), actor, request(false)).active()).isFalse();
    assertThatThrownBy(() -> service.configure(route.getId(), actor, request(true)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not ready for activation");
    assertThat(route.isActive()).isFalse();
  }

  @Test
  void changingRouteDefinitionsDeactivatesRemovedRowsAndAddsNewManagedEvidence() {
    ApplicationTypeProgrammeMapping oldMapping = mappingRows.get(0);
    ApplicationTypeSection oldSection = sectionRows.get(0);
    ApplicationTypeDocumentRequirement oldDocument = documentRows.get(0);
    UUID newProgramme = UUID.randomUUID();
    ConfigureApplicationRouteRequest next =
        new ConfigureApplicationRouteRequest(
            List.of(new ProgrammeMappingInput(newProgramme, "BA", "Arts")),
            List.of(new SectionInput("qualifications", "Updated qualifications", true, true, 1, 5)),
            List.of(
                new DocumentInput(
                    "passport", "Passport", true, "SUPPORTING_DOCUMENTS", List.of(), 6)),
            false,
            null,
            false,
            "Replace inactive route configuration",
            0);
    ApplicationRouteConfigurationSummary summary = service.configure(route.getId(), actor, next);
    assertThat(oldMapping.isActive()).isFalse();
    assertThat(oldSection.isActive()).isFalse();
    assertThat(oldDocument.isActive()).isFalse();
    assertThat(summary.programmes())
        .extracting(ApplicationRouteConfigurationSummary.ProgrammeMappingSummary::programmeId)
        .containsExactly(newProgramme);
    assertThat(summary.sections()).hasSize(1);
    assertThat(summary.sections().get(0).name()).isEqualTo("Updated qualifications");
    assertThat(summary.documents()).hasSize(1);
    assertThat(summary.documents().get(0).code()).isEqualTo("PASSPORT");
  }

  @Test
  void
      categoryReconciliationPreservesRetainedScopesSoftDeletesRemovedScopesAndDeduplicatesNewOnes() {
    ApplicationTypeDocumentRequirement document = documentRows.get(0);
    ApplicationTypeDocumentRequirementCategory local =
        new ApplicationTypeDocumentRequirementCategory(document, "LOCAL");
    ApplicationTypeDocumentRequirementCategory sadc =
        new ApplicationTypeDocumentRequirementCategory(document, "SADC");
    categoryRows.add(local);
    categoryRows.add(sadc);
    ConfigureApplicationRouteRequest current = request(false);
    ConfigureApplicationRouteRequest next =
        new ConfigureApplicationRouteRequest(
            current.programmes(),
            current.sections(),
            List.of(
                new DocumentInput(
                    "IDENTITY",
                    "Identity evidence",
                    true,
                    "SUPPORTING_DOCUMENTS",
                    List.of("local", "INTERNATIONAL", "international"),
                    1)),
            false,
            null,
            false,
            "Change applicant category scope",
            0);
    ApplicationRouteConfigurationSummary summary = service.configure(route.getId(), actor, next);
    assertThat(local.isDeleted()).isFalse();
    assertThat(sadc.isDeleted()).isTrue();
    assertThat(summary.documents().get(0).applicantCategoryCodes())
        .containsExactly("LOCAL", "INTERNATIONAL");
    verify(categories).saveAll(categoryRows);
  }

  private ConfigureApplicationRouteRequest request(boolean activate) {
    return new ConfigureApplicationRouteRequest(
        mappingRows.stream()
            .map(
                row ->
                    new ProgrammeMappingInput(
                        row.getProgrammeId(), row.getProgrammeCode(), row.getProgrammeName()))
            .toList(),
        sectionRows.stream()
            .map(
                row ->
                    new SectionInput(
                        row.getSectionCode(),
                        row.getSectionName(),
                        row.isRequired(),
                        row.isRepeatable(),
                        row.getMinimumRecords(),
                        row.getSortOrder()))
            .toList(),
        documentRows.stream()
            .map(
                row ->
                    new DocumentInput(
                        row.getRequirementCode(),
                        row.getRequirementName(),
                        row.isRequired(),
                        "SUPPORTING_DOCUMENTS",
                        List.of(),
                        row.getSortOrder()))
            .toList(),
        false,
        null,
        activate,
        "Configure route evidence",
        0);
  }

  private ApplicationTypeSection section(String code, boolean required, int minimum) {
    return new ApplicationTypeSection(
        route, code, code.replace('_', ' '), required, minimum > 0, minimum, 1);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

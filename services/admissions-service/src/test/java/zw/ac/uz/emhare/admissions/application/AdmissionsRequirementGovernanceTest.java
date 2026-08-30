package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsSelectionOfferService.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;

/** Requirement capture, version supersession and API read-model contracts. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsRequirementGovernanceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private ApplicationTypeRepository types;
  @Mock private AdmissionRequirementSetRepository requirements;
  @Mock private AdmissionSubjectRepository subjects;
  @Mock private AdmissionSubjectRequirementRepository subjectRequirements;
  @Mock private AdmissionQualificationRequirementGroupRepository groups;
  @Mock private AdmissionQualificationRequirementItemRepository items;
  @Mock private AdvancedAdmissionRuleEvaluator advancedRules;
  @Mock private Clock clock;
  @Spy private ObjectMapper mapper = new ObjectMapper();
  @InjectMocks private AdmissionsSelectionOfferService service;
  private final UUID actor = UUID.randomUUID();
  private final UUID programme = UUID.randomUUID();
  private final UUID intake = UUID.randomUUID();
  private ApplicationType type;
  private List<AdmissionSubjectRequirement> savedSubjects;
  private List<AdmissionQualificationRequirementGroup> savedGroups;
  private List<AdmissionQualificationRequirementItem> savedItems;

  @BeforeEach
  void setUp() {
    type = identified(new ApplicationType("POSTGRAD", "Postgraduate", true, true));
    savedSubjects = new ArrayList<>();
    savedGroups = new ArrayList<>();
    savedItems = new ArrayList<>();
    lenient().when(types.findById(type.getId())).thenReturn(Optional.of(type));
    lenient().when(clock.instant()).thenReturn(NOW);
    lenient()
        .when(requirements.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              AdmissionRequirementSet requirement = invocation.getArgument(0);
              if (requirement.getId() == null) identified(requirement);
              return requirement;
            });
    lenient()
        .when(subjectRequirements.saveAll(any()))
        .thenAnswer(
            invocation -> {
              Iterable<AdmissionSubjectRequirement> captured = invocation.getArgument(0);
              captured.forEach(savedSubjects::add);
              return savedSubjects;
            });
    lenient()
        .when(groups.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              AdmissionQualificationRequirementGroup group = identified(invocation.getArgument(0));
              savedGroups.add(group);
              return group;
            });
    lenient()
        .when(items.saveAll(any()))
        .thenAnswer(
            invocation -> {
              Iterable<AdmissionQualificationRequirementItem> captured = invocation.getArgument(0);
              captured.forEach(savedItems::add);
              return savedItems;
            });
    lenient()
        .when(
            subjectRequirements.findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(
                any()))
        .thenAnswer(invocation -> savedSubjects);
    lenient()
        .when(groups.findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
        .thenAnswer(invocation -> savedGroups);
    lenient()
        .when(items.findAllByRequirementGroupIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
        .thenAnswer(invocation -> savedItems);
  }

  @Test
  void capturePersistsManagedSubjectAndQualificationAlternativesAsCompleteReadModel() {
    AdmissionSubject subject =
        identified(
            new AdmissionSubject("MATH", "Mathematics", SubjectLevel.A_LEVEL, "MATHEMATICS"));
    when(subjects.findByIdAndDeletedAtIsNull(subject.getId())).thenReturn(Optional.of(subject));
    var managed =
        new SubjectRequirementInput(
            " a_level ",
            subject.getId(),
            null,
            "COMPULSORY",
            " C ",
            BigDecimal.TEN,
            1,
            BigDecimal.ONE,
            1);
    var grouped =
        new SubjectRequirementInput("O_LEVEL", null, " ENGLISH ", "ANY_OF", null, null, 1, null, 2);
    var qualification =
        new QualificationRequirementGroupInput(
            " prior-study ",
            "Prior study",
            1,
            1,
            List.of(
                new QualificationRequirementItemInput(" degree ", 1, null, 36, 1),
                new QualificationRequirementItemInput("DIPLOMA", 2, BigDecimal.ZERO, null, 2)));
    var result = create(" 2026-v1 ", null, null, List.of(managed, grouped), List.of(qualification));
    assertThat(result.status()).isEqualTo("DRAFT");
    assertThat(result.versionCode()).isEqualTo("2026-v1");
    assertThat(result.programmeId()).isEqualTo(programme);
    assertThat(result.intakeId()).isEqualTo(intake);
    assertThat(result.subjectRequirements()).hasSize(2);
    assertThat(result.subjectRequirements().get(0).subjectId()).isEqualTo(subject.getId());
    assertThat(result.subjectRequirements().get(0).minimumGrade()).isEqualTo("C");
    assertThat(result.subjectRequirements().get(1).subjectGroupCode()).isEqualTo("ENGLISH");
    assertThat(result.qualificationGroups().get(0).code()).isEqualTo("PRIOR-STUDY");
    assertThat(result.qualificationGroups().get(0).items()).hasSize(2);
    assertThat(result.qualificationGroups().get(0).items().get(0).qualificationLevel())
        .isEqualTo("DEGREE");
    assertThat(result.qualificationGroups().get(0).items().get(0).minimumDurationMonths())
        .isEqualTo(36);
    assertThat(result.minimumTotalPoints()).isEqualByComparingTo("10");
    assertThat(result.requiresEnglish()).isTrue();
    assertThat(result.requiresMathematics()).isTrue();
    assertThat(result.requiresScience()).isFalse();
  }

  @Test
  void optionalCollectionsAndAdvancedRulesMayBeAbsent() {
    var result = create("v1", null, null, null, null);
    assertThat(result.subjectRequirements()).isEmpty();
    assertThat(result.qualificationGroups()).isEmpty();
    assertThat(result.advancedRulesVersion()).isNull();
    verifyNoInteractions(subjects, advancedRules);
  }

  @Test
  void advancedRuleConfigurationIsSerializedWithExplicitVersion() {
    var result = create("v1", Map.of("all", List.of()), " advanced_rules_v1 ", null, null);
    assertThat(result.advancedRulesVersion()).isEqualTo("advanced_rules_v1");
    verify(mapper).writeValueAsString(Map.of("all", List.of()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void requirementVersionCannotBeMissing(String version) {
    assertThatThrownBy(() -> create(version, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Requirement-set version");
    verify(requirements, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void advancedConfigurationRequiresVersion(String version) {
    assertThatThrownBy(() -> create("v1", Map.of("all", List.of()), version, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Advanced-rules version");
    verify(requirements, never()).saveAndFlush(any());
  }

  @Test
  void missingApplicationTypeFailsBeforeCreatingRequirements() {
    when(types.findById(type.getId())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> create("v1", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application type not found.");
    verifyNoInteractions(requirements);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missingSubject",
        "wrongLevel",
        "noSubjectOrGroup",
        "unknownLevel",
        "unknownRequirement"
      })
  void subjectCaptureRejectsInvalidCatalogueAndRuleInputs(String invalid) {
    UUID subjectId = null;
    String level = "A_LEVEL", group = "SCIENCE", rule = "COMPULSORY";
    if (invalid.equals("missingSubject")) subjectId = UUID.randomUUID();
    if (invalid.equals("wrongLevel")) {
      AdmissionSubject subject =
          identified(new AdmissionSubject("ENG", "English", SubjectLevel.O_LEVEL, "ENGLISH"));
      subjectId = subject.getId();
      when(subjects.findByIdAndDeletedAtIsNull(subjectId)).thenReturn(Optional.of(subject));
    }
    if (invalid.equals("noSubjectOrGroup")) group = null;
    if (invalid.equals("unknownLevel")) level = "UNKNOWN";
    if (invalid.equals("unknownRequirement")) rule = "UNKNOWN";
    var input = new SubjectRequirementInput(level, subjectId, group, rule, null, null, 1, null, 1);
    assertThatThrownBy(() -> create("v1", null, null, List.of(input), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(savedSubjects).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nullItems",
        "emptyItems",
        "impossibleMinimum",
        "zeroMinimum",
        "missingName",
        "missingCode"
      })
  void qualificationGroupRequiresNamedAchievablePositiveAlternatives(String invalid) {
    List<QualificationRequirementItemInput> alternatives = List.of(item("DEGREE", 1, null, null));
    if (invalid.equals("nullItems")) alternatives = null;
    if (invalid.equals("emptyItems")) alternatives = List.of();
    int minimum = invalid.equals("impossibleMinimum") ? 2 : invalid.equals("zeroMinimum") ? 0 : 1;
    var group =
        new QualificationRequirementGroupInput(
            invalid.equals("missingCode") ? " " : "PRIOR",
            invalid.equals("missingName") ? " " : "Prior study",
            minimum,
            1,
            alternatives);
    assertThatThrownBy(() -> create("v1", null, null, null, List.of(group)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void qualificationGroupCodesAreUniqueAfterTrimmingAndCaseNormalization() {
    var first =
        new QualificationRequirementGroupInput(
            "PRIOR", "Prior study", 1, 1, List.of(item("DEGREE", 1, null, null)));
    var second =
        new QualificationRequirementGroupInput(
            " prior ", "Duplicated prior study", 1, 2, first.items());
    assertThatThrownBy(() -> create("v1", null, null, null, List.of(first, second)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique");
  }

  @ParameterizedTest
  @ValueSource(strings = {"zeroCount", "negativePoints", "negativeDuration", "unknownLevel"})
  void qualificationAlternativeRejectsImpossibleThresholds(String invalid) {
    var alternative =
        item(
            invalid.equals("unknownLevel") ? "UNKNOWN" : "DEGREE",
            invalid.equals("zeroCount") ? 0 : 1,
            invalid.equals("negativePoints") ? BigDecimal.ONE.negate() : null,
            invalid.equals("negativeDuration") ? -1 : null);
    var group =
        new QualificationRequirementGroupInput("PRIOR", "Prior study", 1, 1, List.of(alternative));
    assertThatThrownBy(() -> create("v1", null, null, null, List.of(group)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(savedItems).isEmpty();
  }

  @Test
  void approvalRetiresOnlyOverlappingHistoricalVersionsAndKeepsNonOverlappingEvidence() {
    AdmissionRequirementSet current = requirement("current", LocalDate.of(2026, 1, 1), null, false);
    AdmissionRequirementSet overlapping =
        requirement("overlap", LocalDate.of(2025, 1, 1), null, false);
    overlapping.approve(actor, NOW.minusSeconds(100));
    AdmissionRequirementSet old =
        requirement("historical", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), false);
    old.approve(actor, NOW.minusSeconds(200));
    when(requirements.findById(current.getId())).thenReturn(Optional.of(current));
    when(requirements.findApprovedForRouteForUpdate(programme, type.getId(), intake))
        .thenReturn(List.of(current, overlapping, old));
    var result = service.approveRequirementSet(current.getId(), actor);
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(result.approvedAt()).isEqualTo(NOW);
    assertThat(overlapping.getStatus()).isEqualTo(RequirementSetStatus.RETIRED);
    assertThat(old.getStatus()).isEqualTo(RequirementSetStatus.APPROVED);
    verify(requirements).saveAllAndFlush(List.of(overlapping));
  }

  @Test
  void firstApprovalDoesNotWriteAnEmptyRetirementBatch() {
    AdmissionRequirementSet requirement =
        requirement("first", LocalDate.of(2026, 1, 1), null, false);
    when(requirements.findById(requirement.getId())).thenReturn(Optional.of(requirement));
    assertThat(service.approveRequirementSet(requirement.getId(), actor).status())
        .isEqualTo("APPROVED");
    verify(requirements, never()).saveAllAndFlush(any());
    verifyNoInteractions(advancedRules);
  }

  @Test
  void advancedRulesAreValidatedBeforeApprovingOrRetiringAnything() {
    AdmissionRequirementSet requirement =
        requirement("advanced", LocalDate.of(2026, 1, 1), null, true);
    when(requirements.findById(requirement.getId())).thenReturn(Optional.of(requirement));
    doThrow(new IllegalArgumentException("Unsupported rule fact"))
        .when(advancedRules)
        .validate("advanced_rules_v1", "{}");
    assertThatThrownBy(() -> service.approveRequirementSet(requirement.getId(), actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported rule fact");
    assertThat(requirement.getStatus()).isEqualTo(RequirementSetStatus.DRAFT);
    verify(requirements, never()).findApprovedForRouteForUpdate(any(), any(), any());
  }

  @Test
  void validAdvancedRulesCanBeApprovedAndReadFromTheRegister() {
    AdmissionRequirementSet requirement =
        requirement("advanced", LocalDate.of(2026, 1, 1), null, true);
    when(requirements.findById(requirement.getId())).thenReturn(Optional.of(requirement));
    service.approveRequirementSet(requirement.getId(), actor);
    when(requirements.findAllByDeletedAtIsNullOrderByEffectiveFromDesc())
        .thenReturn(List.of(requirement));
    var result = service.listRequirementSets();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).advancedRulesVersion()).isEqualTo("advanced_rules_v1");
    assertThat(result.get(0).status()).isEqualTo("APPROVED");
    verify(advancedRules).validate("advanced_rules_v1", "{}");
  }

  @Test
  void missingRequirementCannotBeApproved() {
    assertThatThrownBy(() -> service.approveRequirementSet(UUID.randomUUID(), actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Admission requirement set not found.");
  }

  private AdmissionRequirementSetSummary create(
      String version,
      Map<String, Object> advanced,
      String advancedVersion,
      List<SubjectRequirementInput> subjects,
      List<QualificationRequirementGroupInput> groups) {
    return service.createRequirementSet(
        programme,
        type.getId(),
        intake,
        version,
        LocalDate.of(2026, 1, 1),
        null,
        BigDecimal.TEN,
        new BigDecimal("12"),
        new BigDecimal("11"),
        true,
        true,
        false,
        false,
        advanced,
        advancedVersion,
        subjects,
        groups);
  }

  private QualificationRequirementItemInput item(
      String level, int count, BigDecimal points, Integer duration) {
    return new QualificationRequirementItemInput(level, count, points, duration, 1);
  }

  private AdmissionRequirementSet requirement(
      String version, LocalDate from, LocalDate to, boolean advanced) {
    return identified(
        new AdmissionRequirementSet(
            programme,
            type,
            intake,
            version,
            from,
            to,
            BigDecimal.TEN,
            null,
            null,
            false,
            false,
            advanced ? "{}" : null,
            advanced ? "advanced_rules_v1" : null));
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

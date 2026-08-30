package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;

/**
 * Eligibility rule evidence and grading boundaries using real applicant records. @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class QualificationEligibilityEvidenceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private ApplicantQualificationResultRepository results;
  @Mock private ApplicantQualificationSittingRepository sittings;
  @Mock private GradingScaleRepository scales;
  @Mock private GradingScaleValueRepository grades;
  @Mock private AdmissionSubjectRequirementRepository subjectRequirements;
  @Mock private ApplicantEmploymentHistoryRepository employment;
  @Mock private ApplicationProfessionalAchievementRepository achievements;
  @Mock private ApplicationPriorUzDeclarationRepository priorUz;
  @Mock private ApplicationProgrammeChoiceRepository choices;
  @Mock private AdmissionQualificationRequirementGroupRepository groups;
  @Mock private AdmissionQualificationRequirementItemRepository items;
  @Mock private AdvancedAdmissionRuleEvaluator advanced;
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  @InjectMocks private QualificationEligibilityService service;
  private Application application;
  private ApplicationType type;
  private List<ApplicantQualificationResult> qualificationResults;
  private List<ApplicantQualificationSitting> qualificationSittings;

  @BeforeEach
  void setUp() {
    type = identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false));
    application =
        identified(
            new Application(
                UUID.randomUUID(),
                "AUG26",
                "August intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                3,
                identified(
                    new Applicant(
                        UUID.randomUUID(),
                        "A000001",
                        "LOCAL",
                        "Tariro",
                        "Moyo",
                        "applicant@example.test")),
                type,
                "APP-1",
                false));
    qualificationResults = new ArrayList<>();
    qualificationSittings = new ArrayList<>();
    lenient()
        .when(results.findAllForApplication(application.getId()))
        .thenAnswer(invocation -> qualificationResults);
    lenient()
        .when(
            sittings.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(
                application.getId()))
        .thenAnswer(invocation -> qualificationSittings);
  }

  @ParameterizedTest
  @CsvSource({
    "M,8,12,10,12",
    "MALE,14,12,10,14",
    "F,8,12,10,10",
    "FEMALE,14,12,10,14",
    "OTHER,8,12,10,8",
    "MALE,,12,10,12",
    "FEMALE,8,12,,8"
  })
  void genderCutoffCanRaiseButNeverLowerGeneralMinimum(
      String gender, BigDecimal base, BigDecimal male, BigDecimal female, BigDecimal expected) {
    application
        .getApplicant()
        .correctProfile(
            new ApplicantProfileCorrection(
                "LOCAL",
                null,
                "Tariro",
                null,
                "Moyo",
                null,
                gender,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "applicant@example.test",
                null,
                null,
                null,
                0));
    AdmissionRequirementSet requirement = requirement(base, male, female, false);
    var result = service.evaluateRequirements(application, requirement);
    assertThat(result.ruleEvidence().get("minimumTotalPoints")).isEqualTo(expected);
    assertThat(result.missingRequirements()).contains("minimum total points");
  }

  @Test
  void requiredOLevelSubjectsProduceIndependentMissingEvidence() {
    AdmissionRequirementSet requirement =
        identified(
            new AdmissionRequirementSet(
                UUID.randomUUID(),
                type,
                application.getIntakeId(),
                "v1",
                LocalDate.of(2026, 1, 1),
                null,
                null,
                null,
                null,
                true,
                true,
                true,
                true,
                null,
                null));
    var result = service.evaluateRequirements(application, requirement);
    assertThat(result.missingRequirements())
        .containsExactly(
            "O Level English pass",
            "O Level Mathematics pass",
            "O Level Science pass",
            "O Level Mathematics or Science pass");
    assertThat(result.missingRequirementEvidence())
        .extracting(value -> value.get("code"))
        .containsExactly(
            "ENGLISH_PASS", "MATHEMATICS_PASS", "SCIENCE_PASS", "MATHEMATICS_OR_SCIENCE_PASS");
  }

  @Test
  void passingOLevelSubjectsMeetIndependentRequirementsWithoutAddingPoints() {
    AdmissionRequirementSet requirement =
        identified(
            new AdmissionRequirementSet(
                UUID.randomUUID(),
                type,
                application.getIntakeId(),
                "v1",
                LocalDate.of(2026, 1, 1),
                null,
                null,
                null,
                null,
                true,
                true,
                true,
                true,
                null,
                null));
    ApplicantQualificationSitting sitting =
        sitting(QualificationLevel.O_LEVEL, zimsec(), 2025, null);
    addResult(
        sitting,
        identified(
            new AdmissionSubject(
                "ENG", "English", SubjectLevel.O_LEVEL, "LANGUAGE", false, false, true)),
        "A");
    addResult(
        sitting,
        identified(
            new AdmissionSubject(
                "MAT", "Mathematics", SubjectLevel.O_LEVEL, "MATH", false, true, false)),
        "B");
    addResult(
        sitting,
        identified(
            new AdmissionSubject(
                "SCI", "Science", SubjectLevel.O_LEVEL, "SCIENCE", true, false, false)),
        "C");
    var result = service.evaluateRequirements(application, requirement);
    assertThat(result.missingRequirements()).isEmpty();
    assertThat(result.totalPoints()).isEqualByComparingTo("0");
    assertThat(result.ruleEvidence())
        .containsEntry("englishPass", true)
        .containsEntry("mathematicsPass", true)
        .containsEntry("sciencePass", true);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"U", "F", "UNKNOWN"})
  void ungradedZimsecResultsDoNotContributePoints(String grade) {
    addResult(sitting(QualificationLevel.A_LEVEL, zimsec(), 2025, null), null, grade);
    var result = service.evaluateApplication(application.getId());
    assertThat(result.totalPoints()).isEqualByComparingTo("0");
    assertThat(result.excludedResults()).hasSize(1);
    assertThat(result.excludedResults().get(0).reason())
        .isEqualTo(QualificationPointsCalculator.ExclusionReason.UNGRADED);
  }

  @Test
  void recalculationClearsObsoleteFailedOrOLevelPointsAndIsIdempotent() {
    ApplicantQualificationResult failed =
        addResult(sitting(QualificationLevel.A_LEVEL, zimsec(), 2025, null), null, "U");
    failed.applyCalculatedPoints(new BigDecimal("5"));
    ApplicantQualificationResult ordinary =
        addResult(sitting(QualificationLevel.O_LEVEL, zimsec(), 2025, null), null, "A");
    ordinary.applyCalculatedPoints(new BigDecimal("5"));
    ApplicantQualificationResult advanced =
        addResult(sitting(QualificationLevel.A_LEVEL, zimsec(), 2025, null), null, "A");
    assertThat(service.recalculateApplicationPoints(application.getId()).totalPoints())
        .isEqualByComparingTo("5");
    assertThat(failed.getPoints()).isNull();
    assertThat(ordinary.getPoints()).isNull();
    assertThat(advanced.getPoints()).isEqualByComparingTo("5");
    service.recalculateApplicationPoints(application.getId());
    verify(results).saveAll(List.of(failed, ordinary, advanced));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void configuredNonZimsecScaleUsesSittingYearOrCurrentDateAndPassFlag(boolean hasYear) {
    ApplicantQualificationSitting sitting =
        sitting(QualificationLevel.A_LEVEL, null, hasYear ? 2024 : null, null);
    addResult(sitting, null, " B ");
    GradingScale scale =
        identified(
            new GradingScale(
                "CAMB", "Cambridge", QualificationLevel.A_LEVEL, LocalDate.of(2020, 1, 1), null));
    LocalDate expectedDate = hasYear ? LocalDate.of(2024, 12, 31) : LocalDate.of(2026, 8, 12);
    when(scales.findApplicableScale(QualificationLevel.A_LEVEL, expectedDate))
        .thenReturn(Optional.of(scale));
    when(grades.findByGradingScaleIdAndGradeIgnoreCaseAndDeletedAtIsNull(scale.getId(), "B"))
        .thenReturn(
            Optional.of(new GradingScaleValue(scale, "B", new BigDecimal("4"), hasYear, 2)));
    var result = service.evaluateApplication(application.getId());
    assertThat(result.totalPoints()).isEqualByComparingTo(hasYear ? "4" : "0");
    if (!hasYear)
      assertThat(result.excludedResults().get(0).reason())
          .isEqualTo(QualificationPointsCalculator.ExclusionReason.FAILED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"noScale", "noGrade", "noExamCode"})
  void missingConfiguredScaleOrGradeLeavesExternalEvidenceUngraded(String missing) {
    ExamBody exam =
        new ExamBody(missing.equals("noExamCode") ? null : "CAMB", "External examinations", null);
    addResult(sitting(QualificationLevel.A_LEVEL, exam, 2024, null), null, "A");
    if (missing.equals("noGrade")) {
      GradingScale scale =
          identified(
              new GradingScale(
                  "CAMB", "Cambridge", QualificationLevel.A_LEVEL, LocalDate.of(2020, 1, 1), null));
      when(scales.findApplicableScale(QualificationLevel.A_LEVEL, LocalDate.of(2024, 12, 31)))
          .thenReturn(Optional.of(scale));
    }
    assertThat(service.evaluateApplication(application.getId()).excludedResults()).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({"ONE_OF,1,true", "ANY_OF,2,false"})
  void groupedSubjectAlternativesAreEvaluatedOnceAgainstRequiredMatchCount(
      SubjectRequirementType type, int minimum, boolean expected) {
    AdmissionRequirementSet requirement = requirement(null, null, null, false);
    AdmissionSubject physics =
        identified(new AdmissionSubject("PHY", "Physics", SubjectLevel.A_LEVEL, "SCIENCE", true));
    AdmissionSubject chemistry =
        identified(new AdmissionSubject("CHE", "Chemistry", SubjectLevel.A_LEVEL, "SCIENCE", true));
    addResult(sitting(QualificationLevel.A_LEVEL, zimsec(), 2025, null), physics, "B");
    when(subjectRequirements.findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(
            requirement.getId()))
        .thenReturn(
            List.of(
                new AdmissionSubjectRequirement(
                    requirement,
                    SubjectLevel.A_LEVEL,
                    physics,
                    "SCIENCE",
                    type,
                    null,
                    null,
                    minimum,
                    null,
                    1),
                new AdmissionSubjectRequirement(
                    requirement,
                    SubjectLevel.A_LEVEL,
                    chemistry,
                    "SCIENCE",
                    type,
                    null,
                    null,
                    minimum,
                    null,
                    2),
                new AdmissionSubjectRequirement(
                    requirement,
                    SubjectLevel.A_LEVEL,
                    physics,
                    "OTHER",
                    SubjectRequirementType.WEIGHTED,
                    null,
                    null,
                    1,
                    BigDecimal.ONE,
                    3)));
    var result = service.evaluateRequirements(application, requirement);
    List<Map<String, Object>> evidence = evidence(result, "subjectRequirements");
    assertThat(evidence).hasSize(2);
    assertThat(evidence.get(0))
        .containsEntry("matchedCount", 1L)
        .containsEntry("minimumCount", minimum)
        .containsEntry("satisfied", expected);
    assertThat(result.missingRequirements().isEmpty()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "passing",
        "excluded",
        "noSubject",
        "wrongSubject",
        "wrongGroup",
        "wrongLevel",
        "lowPoints",
        "noPoints",
        "lowGrade",
        "unknownMinimumGrade"
      })
  void subjectEvidenceSeparatesCatalogueLevelGradePointsAndExclusionRules(String scenario) {
    AdmissionRequirementSet requirement = requirement(null, null, null, false);
    boolean ordinary = scenario.equals("noPoints");
    SubjectLevel level = ordinary ? SubjectLevel.O_LEVEL : SubjectLevel.A_LEVEL;
    AdmissionSubject subject =
        identified(new AdmissionSubject("PHY", "Physics", level, "SCIENCE", true));
    ApplicantQualificationSitting sitting =
        sitting(
            ordinary ? QualificationLevel.O_LEVEL : QualificationLevel.A_LEVEL,
            zimsec(),
            2025,
            null);
    addResult(sitting, scenario.equals("noSubject") ? null : subject, "B");
    AdmissionSubject requestedSubject =
        scenario.equals("wrongSubject")
            ? identified(new AdmissionSubject("CHE", "Chemistry", level, "SCIENCE", true))
            : null;
    var rule =
        new AdmissionSubjectRequirement(
            requirement,
            scenario.equals("wrongLevel") ? SubjectLevel.O_LEVEL : level,
            requestedSubject,
            scenario.equals("wrongGroup") ? "LANGUAGE" : "science",
            scenario.equals("excluded")
                ? SubjectRequirementType.EXCLUDED
                : SubjectRequirementType.COMPULSORY,
            scenario.equals("lowGrade")
                ? "A"
                : scenario.equals("unknownMinimumGrade") ? "UNKNOWN" : "C",
            scenario.equals("lowPoints") || scenario.equals("noPoints")
                ? new BigDecimal("5")
                : null,
            null,
            null,
            1);
    when(subjectRequirements.findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(
            requirement.getId()))
        .thenReturn(List.of(rule));
    var result = service.evaluateRequirements(application, requirement);
    assertThat(evidence(result, "subjectRequirements").get(0).get("satisfied"))
        .isEqualTo(scenario.equals("passing"));
    assertThat(result.missingRequirements().isEmpty()).isEqualTo(scenario.equals("passing"));
  }

  @Test
  void oneOfManagedSubjectWithoutGroupUsesIndividualRuleAndExclusionCanPassWhenUnmatched() {
    AdmissionRequirementSet requirement = requirement(null, null, null, false);
    AdmissionSubject subject =
        identified(new AdmissionSubject("PHY", "Physics", SubjectLevel.A_LEVEL, "SCIENCE", true));
    when(subjectRequirements.findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(
            requirement.getId()))
        .thenReturn(
            List.of(
                new AdmissionSubjectRequirement(
                    requirement,
                    SubjectLevel.A_LEVEL,
                    subject,
                    null,
                    SubjectRequirementType.ONE_OF,
                    null,
                    null,
                    null,
                    null,
                    1),
                new AdmissionSubjectRequirement(
                    requirement,
                    SubjectLevel.A_LEVEL,
                    null,
                    "ENGLISH",
                    SubjectRequirementType.EXCLUDED,
                    null,
                    null,
                    null,
                    null,
                    2)));
    var result = service.evaluateRequirements(application, requirement);
    assertThat(evidence(result, "subjectRequirements"))
        .extracting(row -> row.get("satisfied"))
        .containsExactly(false, true);
  }

  @ParameterizedTest
  @CsvSource({
    "36,10,1,24,8,true",
    "12,10,1,24,8,false",
    "36,4,1,24,8,false",
    "36,10,2,24,8,false",
    "36,10,1,,,true"
  })
  void qualificationGroupsUseOnlyDurationEligibleSittingsAndTheirPoints(
      Integer duration,
      BigDecimal actualPoints,
      int count,
      Integer minimumDuration,
      BigDecimal minimumPoints,
      boolean expected) {
    AdmissionRequirementSet requirement = requirement(null, null, null, false);
    ApplicantQualificationSitting degree =
        sitting(QualificationLevel.DEGREE, zimsec(), 2025, duration);
    addResult(degree, null, "PASS").applyCalculatedPoints(actualPoints);
    addResult(sitting(QualificationLevel.DIPLOMA, zimsec(), 2023, 24), null, "PASS")
        .applyCalculatedPoints(new BigDecimal("100"));
    AdmissionQualificationRequirementGroup group =
        identified(
            new AdmissionQualificationRequirementGroup(requirement, "PRIOR", "Prior study", 1, 1));
    when(groups.findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(requirement.getId()))
        .thenReturn(List.of(group));
    when(items.findAllByRequirementGroupIdAndDeletedAtIsNullOrderBySortOrderAsc(group.getId()))
        .thenReturn(
            List.of(
                new AdmissionQualificationRequirementItem(
                    group, QualificationLevel.DEGREE, count, minimumPoints, minimumDuration, 1)));
    var result = service.evaluateRequirements(application, requirement);
    assertThat(evidence(result, "qualificationGroups").get(0).get("satisfied")).isEqualTo(expected);
    assertThat(result.missingRequirements().isEmpty()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void advancedEvaluationReceivesOwnedRouteFactsAndRetainsFailureEvidence(boolean satisfied) {
    AdmissionRequirementSet requirement = requirement(null, null, null, true);
    ApplicantQualificationSitting sitting =
        sitting(QualificationLevel.A_LEVEL, zimsec(), 2025, null);
    addResult(sitting, null, "A");
    addResult(sitting, null, "B");
    when(employment.findAllByApplicantIdAndDeletedAtIsNullOrderByStartedOnDesc(
            application.getApplicant().getId()))
        .thenReturn(
            List.of(
                new ApplicantEmploymentHistory(
                    application.getApplicant(),
                    "Employer one",
                    "Analyst",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2026, 1, 1),
                    false,
                    null),
                new ApplicantEmploymentHistory(
                    application.getApplicant(),
                    "Employer two",
                    "Analyst",
                    LocalDate.of(2026, 1, 1),
                    null,
                    true,
                    null)));
    when(achievements.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            application.getId()))
        .thenReturn(
            List.of(
                new ApplicationProfessionalAchievement(
                    application,
                    ApplicationProfessionalAchievement.Type.AWARD,
                    "Research award",
                    null,
                    null,
                    null)));
    if (satisfied)
      when(priorUz.findByApplicationIdAndDeletedAtIsNull(application.getId()))
          .thenReturn(
              Optional.of(
                  new ApplicationPriorUzDeclaration(
                      application, true, "R220001A", LocalDate.of(2022, 1, 1), null, true, true)));
    when(choices.countEntryOptionSelections(application.getId())).thenReturn(2L);
    when(advanced.evaluate(eq("advanced_rules_v1"), eq("{}"), any()))
        .thenReturn(
            new AdvancedAdmissionRuleEvaluator.RuleResult(
                satisfied, Map.of("satisfied", satisfied)));
    var result = service.evaluateRequirements(application, requirement);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> facts = ArgumentCaptor.forClass(Map.class);
    verify(advanced).evaluate(eq("advanced_rules_v1"), eq("{}"), facts.capture());
    assertThat(facts.getValue())
        .containsEntry("qualification.A_LEVEL.count", 1L)
        .containsEntry("employment.totalMonths", 19L)
        .containsEntry("professionalAchievement.count", 1)
        .containsEntry("priorUz.previouslyStudied", satisfied)
        .containsEntry("entryOption.count", 2L)
        .containsEntry("applicant.category", "LOCAL");
    assertThat(result.ruleEvidence().get("advancedRules"))
        .isEqualTo(Map.of("satisfied", satisfied));
    assertThat(result.missingRequirements().contains("advanced admission rules"))
        .isEqualTo(!satisfied);
  }

  private AdmissionRequirementSet requirement(
      BigDecimal minimum, BigDecimal male, BigDecimal female, boolean advanced) {
    return identified(
        new AdmissionRequirementSet(
            UUID.randomUUID(),
            type,
            application.getIntakeId(),
            "v1",
            LocalDate.of(2026, 1, 1),
            null,
            minimum,
            male,
            female,
            false,
            false,
            advanced ? "{}" : null,
            advanced ? "advanced_rules_v1" : null));
  }

  private ApplicantQualificationSitting sitting(
      QualificationLevel level, ExamBody body, Integer year, Integer duration) {
    ApplicantQualificationSitting sitting =
        identified(new ApplicantQualificationSitting(application, level, body, "01", "02", year));
    if (duration != null)
      sitting.update(body, "University", "01", "02", year, null, null, duration);
    qualificationSittings.add(sitting);
    return sitting;
  }

  private ApplicantQualificationResult addResult(
      ApplicantQualificationSitting sitting, AdmissionSubject subject, String grade) {
    ApplicantQualificationResult result =
        identified(
            new ApplicantQualificationResult(
                sitting, subject, subject == null ? "Legacy subject" : subject.getName(), grade));
    qualificationResults.add(result);
    return result;
  }

  private ExamBody zimsec() {
    return new ExamBody("ZIMSEC", "ZIMSEC", null);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> evidence(
      QualificationEligibilityService.RequirementEvaluation evaluation, String key) {
    return (List<Map<String, Object>>) evaluation.ruleEvidence().get(key);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}

package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationResult;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ExamBody;
import zw.ac.uz.emhare.admissions.domain.model.GradingScale;
import zw.ac.uz.emhare.admissions.domain.model.GradingScaleValue;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationResultRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.GradingScaleRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.GradingScaleValueRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionSubjectRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantEmploymentHistoryRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProfessionalAchievementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationPriorUzDeclarationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionQualificationRequirementGroupRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionQualificationRequirementItemRepository;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubjectRequirement;
import zw.ac.uz.emhare.admissions.domain.model.SubjectRequirementType;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.QualificationPointsCalculator.EligibilitySnapshot;
import zw.ac.uz.emhare.admissions.domain.model.QualificationLevel;

/** @author Tinashe K */
@Service
public class QualificationEligibilityService {

    private static final GradingScale ZIMSEC_A_LEVEL_SCALE = new GradingScale(
            "ZIMSEC-A", "ZIMSEC A Level", QualificationLevel.A_LEVEL,
            LocalDate.of(1980, 1, 1), null);
    private static final GradingScale ZIMSEC_O_LEVEL_SCALE = new GradingScale(
            "ZIMSEC-O", "ZIMSEC O Level", QualificationLevel.O_LEVEL,
            LocalDate.of(1980, 1, 1), null);

    private final ApplicantQualificationResultRepository resultRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final GradingScaleValueRepository gradingScaleValueRepository;
    private final Clock clock;
    private final AdmissionSubjectRequirementRepository subjectRequirementRepository;
    private final ApplicantEmploymentHistoryRepository employmentHistoryRepository;
    private final ApplicationProfessionalAchievementRepository professionalAchievementRepository;
    private final ApplicationPriorUzDeclarationRepository priorUzDeclarationRepository;
    private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    private final AdmissionQualificationRequirementGroupRepository qualificationGroupRepository;
    private final AdmissionQualificationRequirementItemRepository qualificationItemRepository;
    private final AdvancedAdmissionRuleEvaluator advancedRuleEvaluator;

    public QualificationEligibilityService(
            ApplicantQualificationResultRepository resultRepository,
            GradingScaleRepository gradingScaleRepository,
            GradingScaleValueRepository gradingScaleValueRepository,
            AdmissionSubjectRequirementRepository subjectRequirementRepository,
            ApplicantEmploymentHistoryRepository employmentHistoryRepository,
            ApplicationProfessionalAchievementRepository professionalAchievementRepository,
            ApplicationPriorUzDeclarationRepository priorUzDeclarationRepository,
            ApplicationProgrammeChoiceRepository programmeChoiceRepository,
            AdmissionQualificationRequirementGroupRepository qualificationGroupRepository,
            AdmissionQualificationRequirementItemRepository qualificationItemRepository,
            AdvancedAdmissionRuleEvaluator advancedRuleEvaluator,
            Clock clock) {
        this.resultRepository = resultRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradingScaleValueRepository = gradingScaleValueRepository;
        this.subjectRequirementRepository = subjectRequirementRepository;
        this.employmentHistoryRepository = employmentHistoryRepository;
        this.professionalAchievementRepository = professionalAchievementRepository;
        this.priorUzDeclarationRepository = priorUzDeclarationRepository;
        this.programmeChoiceRepository = programmeChoiceRepository;
        this.qualificationGroupRepository = qualificationGroupRepository;
        this.qualificationItemRepository = qualificationItemRepository;
        this.advancedRuleEvaluator = advancedRuleEvaluator;
        this.clock = clock;
    }

    @Transactional
    public EligibilitySnapshot evaluateApplication(UUID applicationId) {
        List<ApplicantQualificationResult> results = resultRepository.findAllForApplication(applicationId);
        return QualificationPointsCalculator.compute(results, this::resolveGradingScaleValue);
    }

    @Transactional
    public EligibilitySnapshot recalculateApplicationPoints(UUID applicationId) {
        List<ApplicantQualificationResult> results = resultRepository.findAllForApplication(applicationId);
        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, this::resolveGradingScaleValue);
        Map<ApplicantQualificationResult, BigDecimal> calculatedPoints = new java.util.IdentityHashMap<>();
        results.forEach(result -> calculatedPoints.put(result, null));
        snapshot.consideredResults().forEach(considered -> calculatedPoints.put(
                considered.result(),
                considered.result().getQualificationSitting().getLevel() == QualificationLevel.A_LEVEL
                        ? considered.gradingScaleValue().getPoints()
                        : null));
        List<ApplicantQualificationResult> changedResults = results.stream()
                .filter(result -> !Objects.equals(result.getPoints(), calculatedPoints.get(result)))
                .toList();
        changedResults.forEach(result -> result.applyCalculatedPoints(calculatedPoints.get(result)));
        if (!changedResults.isEmpty()) {
            resultRepository.saveAll(changedResults);
        }
        return snapshot;
    }

    @Transactional
    public RequirementEvaluation evaluateRequirements(
            Application application,
            AdmissionRequirementSet requirementSet) {
        List<ApplicantQualificationResult> results = resultRepository.findAllForApplication(application.getId());
        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, this::resolveGradingScaleValue);
        java.math.BigDecimal submittedTotalPoints = application.getCalculatedTotalPoints() == null
                ? snapshot.totalPoints()
                : application.getCalculatedTotalPoints();
        boolean hasEnglishPass = snapshot.consideredResults().stream()
                .map(considered -> considered.result())
                .anyMatch(result -> result.getQualificationSitting().getLevel() == QualificationLevel.O_LEVEL
                        && result.getSubject() != null
                        && result.getSubject().isEnglishSubject());
        boolean hasMathematicsOrSciencePass = snapshot.consideredResults().stream()
                .map(considered -> considered.result())
                .anyMatch(result -> result.getSubject() != null
                        && (result.getSubject().isMathematicsSubject()
                        || result.getSubject().isScienceSubject()));

        List<String> missingRequirements = new java.util.ArrayList<>();
        List<Map<String, Object>> missingEvidence = new java.util.ArrayList<>();
        BigDecimal applicableCutoff = applicablePointsCutoff(application, requirementSet);
        if (applicableCutoff != null && submittedTotalPoints.compareTo(applicableCutoff) < 0) {
            missingRequirements.add("minimum total points");
            missingEvidence.add(Map.of(
                    "code", "MINIMUM_TOTAL_POINTS",
                    "required", applicableCutoff,
                    "calculated", submittedTotalPoints));
        }
        if (requirementSet.isRequiresEnglish() && !hasEnglishPass) {
            missingRequirements.add("O Level English pass");
            missingEvidence.add(Map.of("code", "ENGLISH_PASS", "satisfied", false));
        }
        if (requirementSet.isRequiresMathematicsOrScience() && !hasMathematicsOrSciencePass) {
            missingRequirements.add("Mathematics or Science subject pass");
            missingEvidence.add(Map.of("code", "MATHEMATICS_OR_SCIENCE_PASS", "satisfied", false));
        }

        List<Map<String, Object>> subjectEvidence = evaluateSubjectRequirements(
                requirementSet, snapshot, missingRequirements, missingEvidence);
        List<Map<String, Object>> qualificationGroupEvidence = evaluateQualificationGroups(
                requirementSet, results, missingRequirements, missingEvidence);

        Map<String, Object> advancedRuleEvidence = null;
        if (requirementSet.getAdvancedRulesJson() != null) {
            var advancedResult = advancedRuleEvaluator.evaluate(
                    requirementSet.getAdvancedRulesVersion(), requirementSet.getAdvancedRulesJson(),
                    advancedFacts(application, results));
            advancedRuleEvidence = advancedResult.evidence();
            if (!advancedResult.satisfied()) {
                missingRequirements.add("advanced admission rules");
                missingEvidence.add(Map.of(
                        "code", "ADVANCED_RULES",
                        "version", requirementSet.getAdvancedRulesVersion(),
                        "evidence", advancedResult.evidence()));
            }
        }

        Map<String, Object> ruleEvidence = new LinkedHashMap<>();
        ruleEvidence.put("pointsSource", "ZIMSEC_GRADE_SCALE");
        ruleEvidence.put("calculatedTotalPoints", submittedTotalPoints);
        ruleEvidence.put("pointsCalculatedAt", application.getPointsCalculatedAt());
        ruleEvidence.put("minimumTotalPoints", applicableCutoff);
        ruleEvidence.put("englishPass", hasEnglishPass);
        ruleEvidence.put("mathematicsOrSciencePass", hasMathematicsOrSciencePass);
        ruleEvidence.put("missingRequirements", missingRequirements);
        ruleEvidence.put("subjectRequirements", subjectEvidence);
        ruleEvidence.put("qualificationGroups", qualificationGroupEvidence);
        ruleEvidence.put("advancedRules", advancedRuleEvidence);
        return new RequirementEvaluation(
                submittedTotalPoints, List.copyOf(missingRequirements),
                List.copyOf(missingEvidence), java.util.Collections.unmodifiableMap(ruleEvidence));
    }

    private List<Map<String, Object>> evaluateQualificationGroups(
            AdmissionRequirementSet requirementSet,
            List<ApplicantQualificationResult> results,
            List<String> missingRequirements,
            List<Map<String, Object>> missingEvidence) {
        List<Map<String, Object>> groupEvidence = new java.util.ArrayList<>();
        for (var group : qualificationGroupRepository
                .findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(requirementSet.getId())) {
            List<Map<String, Object>> itemEvidence = new java.util.ArrayList<>();
            int satisfiedItems = 0;
            for (var item : qualificationItemRepository
                    .findAllByRequirementGroupIdAndDeletedAtIsNullOrderBySortOrderAsc(group.getId())) {
                List<ApplicantQualificationResult> levelResults = results.stream()
                        .filter(result -> result.getQualificationSitting().getLevel() == item.getQualificationLevel())
                        .toList();
                long qualificationCount = levelResults.stream()
                        .map(ApplicantQualificationResult::getQualificationSitting).distinct().count();
                BigDecimal totalPoints = levelResults.stream().map(ApplicantQualificationResult::getPoints)
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                boolean countSatisfied = qualificationCount >= item.getMinimumCount();
                boolean pointsSatisfied = item.getMinimumTotalPoints() == null
                        || totalPoints.compareTo(item.getMinimumTotalPoints()) >= 0;
                // Qualification duration is deliberately fail-closed until a verified duration fact is captured.
                boolean durationSatisfied = item.getMinimumDurationMonths() == null;
                boolean satisfied = countSatisfied && pointsSatisfied && durationSatisfied;
                if (satisfied) satisfiedItems++;
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("qualificationLevel", item.getQualificationLevel().name());
                evidence.put("minimumCount", item.getMinimumCount());
                evidence.put("actualCount", qualificationCount);
                evidence.put("minimumTotalPoints", item.getMinimumTotalPoints());
                evidence.put("actualTotalPoints", totalPoints);
                evidence.put("minimumDurationMonths", item.getMinimumDurationMonths());
                evidence.put("durationFactAvailable", item.getMinimumDurationMonths() == null);
                evidence.put("satisfied", satisfied);
                itemEvidence.add(evidence);
            }
            boolean groupSatisfied = satisfiedItems >= group.getMinimumSatisfiedItems();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("code", "QUALIFICATION_GROUP");
            evidence.put("groupCode", group.getGroupCode());
            evidence.put("minimumSatisfiedItems", group.getMinimumSatisfiedItems());
            evidence.put("satisfiedItems", satisfiedItems);
            evidence.put("items", itemEvidence);
            evidence.put("satisfied", groupSatisfied);
            groupEvidence.add(evidence);
            if (!groupSatisfied) {
                missingRequirements.add("qualification requirement group " + group.getGroupCode());
                missingEvidence.add(evidence);
            }
        }
        return groupEvidence;
    }

    private BigDecimal applicablePointsCutoff(Application application, AdmissionRequirementSet requirementSet) {
        BigDecimal cutoff = requirementSet.getMinimumTotalPoints();
        String gender = application.getApplicant().getGenderCode();
        BigDecimal genderCutoff = gender == null ? null : switch (gender.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "M", "MALE" -> requirementSet.getMaleCutoffPoints();
            case "F", "FEMALE" -> requirementSet.getFemaleCutoffPoints();
            default -> null;
        };
        if (genderCutoff == null) return cutoff;
        return cutoff == null || genderCutoff.compareTo(cutoff) > 0 ? genderCutoff : cutoff;
    }

    private List<Map<String, Object>> evaluateSubjectRequirements(
            AdmissionRequirementSet requirementSet,
            EligibilitySnapshot snapshot,
            List<String> missingRequirements,
            List<Map<String, Object>> missingEvidence) {
        List<AdmissionSubjectRequirement> requirements = subjectRequirementRepository
                .findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(requirementSet.getId());
        List<Map<String, Object>> evidence = new java.util.ArrayList<>();
        java.util.Set<String> evaluatedGroups = new java.util.HashSet<>();
        for (AdmissionSubjectRequirement requirement : requirements) {
            if ((requirement.getRequirementType() == SubjectRequirementType.ONE_OF
                    || requirement.getRequirementType() == SubjectRequirementType.ANY_OF)
                    && requirement.getSubjectGroupCode() != null) {
                String groupKey = requirement.getRequirementType() + ":" + requirement.getSubjectGroupCode();
                if (!evaluatedGroups.add(groupKey)) continue;
                List<AdmissionSubjectRequirement> grouped = requirements.stream()
                        .filter(candidate -> candidate.getRequirementType() == requirement.getRequirementType())
                        .filter(candidate -> java.util.Objects.equals(
                                candidate.getSubjectGroupCode(), requirement.getSubjectGroupCode())).toList();
                long matched = grouped.stream().filter(candidate -> subjectRequirementMatched(candidate, snapshot)).count();
                int minimum = requirement.getMinimumCount() == null ? 1 : requirement.getMinimumCount();
                boolean satisfied = matched >= minimum;
                Map<String, Object> condition = subjectEvidence(requirement, satisfied, matched, minimum);
                evidence.add(condition);
                if (!satisfied) addMissingSubjectRequirement(requirement, condition, missingRequirements, missingEvidence);
                continue;
            }
            boolean matched = subjectRequirementMatched(requirement, snapshot);
            boolean satisfied = requirement.getRequirementType() == SubjectRequirementType.EXCLUDED ? !matched : matched;
            Map<String, Object> condition = subjectEvidence(requirement, satisfied, matched ? 1 : 0, 1);
            evidence.add(condition);
            if (!satisfied) addMissingSubjectRequirement(requirement, condition, missingRequirements, missingEvidence);
        }
        return evidence;
    }

    private boolean subjectRequirementMatched(
            AdmissionSubjectRequirement requirement, EligibilitySnapshot snapshot) {
        return snapshot.consideredResults().stream().anyMatch(considered -> {
            var result = considered.result();
            boolean levelMatches = result.getQualificationSitting().getLevel().name().equals(requirement.getLevel().name());
            boolean subjectMatches = requirement.getSubject() == null
                    ? requirement.getSubjectGroupCode() != null && result.getSubject() != null
                        && requirement.getSubjectGroupCode().equalsIgnoreCase(result.getSubject().getSubjectGroupCode())
                    : result.getSubject() != null && requirement.getSubject().getId().equals(result.getSubject().getId());
            boolean pointsMatch = requirement.getMinimumPoints() == null
                    || (considered.gradingScaleValue().getPoints() != null
                        && considered.gradingScaleValue().getPoints().compareTo(requirement.getMinimumPoints()) >= 0);
            boolean gradeMatches = requirement.getMinimumGrade() == null
                    || resolveGradingScaleValue(result.getQualificationSitting(), requirement.getMinimumGrade())
                        .map(minimum -> considered.gradingScaleValue().getSortOrder() <= minimum.getSortOrder())
                        .orElse(false);
            return levelMatches && subjectMatches && pointsMatch && gradeMatches;
        });
    }

    private Map<String, Object> subjectEvidence(
            AdmissionSubjectRequirement requirement, boolean satisfied, long matched, int minimum) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("code", "SUBJECT_REQUIREMENT");
        evidence.put("requirementType", requirement.getRequirementType().name());
        evidence.put("level", requirement.getLevel().name());
        evidence.put("subjectId", requirement.getSubject() == null ? null : requirement.getSubject().getId());
        evidence.put("subjectGroupCode", requirement.getSubjectGroupCode());
        evidence.put("minimumGrade", requirement.getMinimumGrade());
        evidence.put("minimumPoints", requirement.getMinimumPoints());
        evidence.put("minimumCount", minimum);
        evidence.put("matchedCount", matched);
        evidence.put("satisfied", satisfied);
        return evidence;
    }

    private void addMissingSubjectRequirement(
            AdmissionSubjectRequirement requirement,
            Map<String, Object> evidence,
            List<String> missingRequirements,
            List<Map<String, Object>> missingEvidence) {
        missingRequirements.add("configured subject requirement");
        missingEvidence.add(evidence);
    }

    private Map<String, Object> advancedFacts(
            Application application, List<ApplicantQualificationResult> results) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("applicant.category", application.getApplicant().getApplicantCategoryCode());
        for (QualificationLevel level : QualificationLevel.values()) {
            long count = results.stream().map(ApplicantQualificationResult::getQualificationSitting)
                    .filter(sitting -> sitting.getLevel() == level).distinct().count();
            facts.put("qualification." + level.name() + ".count", count);
        }
        long employmentMonths = employmentHistoryRepository
                .findAllByApplicantIdAndDeletedAtIsNullOrderByStartedOnDesc(application.getApplicant().getId())
                .stream().mapToLong(employment -> Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(
                        employment.getStartedOn(), employment.getEndedOn() == null ? LocalDate.now(clock) : employment.getEndedOn()))).sum();
        facts.put("employment.totalMonths", employmentMonths);
        facts.put("professionalAchievement.count", professionalAchievementRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(application.getId()).size());
        facts.put("priorUz.previouslyStudied", priorUzDeclarationRepository
                .findByApplicationIdAndDeletedAtIsNull(application.getId())
                .map(declaration -> declaration.isPreviouslyStudiedAtUz()).orElse(false));
        facts.put("entryOption.count", programmeChoiceRepository.countEntryOptionSelections(application.getId()));
        return facts;
    }

    private Optional<GradingScaleValue> resolveGradingScaleValue(ApplicantQualificationSitting sitting, String grade) {
        if (isZimsecExamBody(sitting.getExamBody())) {
            return resolveZimsecGrade(sitting.getLevel(), grade);
        }
        LocalDate referenceDate = sitting.getYearWritten() != null
                ? LocalDate.of(sitting.getYearWritten(), 12, 31)
                : LocalDate.now(clock);
        return gradingScaleRepository.findApplicableScale(sitting.getLevel(), referenceDate)
                .flatMap(scale -> gradingScaleValueRepository
                        .findByGradingScaleIdAndGradeIgnoreCaseAndDeletedAtIsNull(scale.getId(), grade.trim()));
    }

    private boolean isZimsecExamBody(ExamBody examBody) {
        return examBody != null
                && examBody.getCode() != null
                && examBody.getCode().trim().toUpperCase(java.util.Locale.ROOT).startsWith("ZIMSEC");
    }

    private Optional<GradingScaleValue> resolveZimsecGrade(QualificationLevel level, String grade) {
        String normalizedGrade = grade == null ? "" : grade.trim().toUpperCase(java.util.Locale.ROOT);
        if (level == QualificationLevel.A_LEVEL) {
            BigDecimal points = switch (normalizedGrade) {
                case "A" -> new BigDecimal("5.00");
                case "B" -> new BigDecimal("4.00");
                case "C" -> new BigDecimal("3.00");
                case "D" -> new BigDecimal("2.00");
                case "E" -> new BigDecimal("1.00");
                default -> null;
            };
            return points == null
                    ? Optional.empty()
                    : Optional.of(new GradingScaleValue(
                            ZIMSEC_A_LEVEL_SCALE, normalizedGrade, points, true,
                            6 - points.intValue()));
        }
        if (level == QualificationLevel.O_LEVEL
                && ("A".equals(normalizedGrade) || "B".equals(normalizedGrade) || "C".equals(normalizedGrade))) {
            return Optional.of(new GradingScaleValue(
                    ZIMSEC_O_LEVEL_SCALE, normalizedGrade, null, true,
                    normalizedGrade.charAt(0) - 'A' + 1));
        }
        return Optional.empty();
    }

    public record RequirementEvaluation(
            java.math.BigDecimal totalPoints,
            List<String> missingRequirements,
            List<Map<String, Object>> missingRequirementEvidence,
            Map<String, Object> ruleEvidence) {
    }
}

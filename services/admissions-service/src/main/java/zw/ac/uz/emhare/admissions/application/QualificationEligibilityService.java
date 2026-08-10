package zw.ac.uz.emhare.admissions.application;

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

    public QualificationEligibilityService(
            ApplicantQualificationResultRepository resultRepository,
            GradingScaleRepository gradingScaleRepository,
            GradingScaleValueRepository gradingScaleValueRepository,
            Clock clock) {
        this.resultRepository = resultRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradingScaleValueRepository = gradingScaleValueRepository;
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
        EligibilitySnapshot snapshot = evaluateApplication(application.getId());
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
        if (requirementSet.getMinimumTotalPoints() != null
                && submittedTotalPoints.compareTo(requirementSet.getMinimumTotalPoints()) < 0) {
            missingRequirements.add("minimum total points");
            missingEvidence.add(Map.of(
                    "code", "MINIMUM_TOTAL_POINTS",
                    "required", requirementSet.getMinimumTotalPoints(),
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

        Map<String, Object> ruleEvidence = new LinkedHashMap<>();
        ruleEvidence.put("pointsSource", "ZIMSEC_GRADE_SCALE");
        ruleEvidence.put("calculatedTotalPoints", submittedTotalPoints);
        ruleEvidence.put("pointsCalculatedAt", application.getPointsCalculatedAt());
        ruleEvidence.put("minimumTotalPoints", requirementSet.getMinimumTotalPoints());
        ruleEvidence.put("englishPass", hasEnglishPass);
        ruleEvidence.put("mathematicsOrSciencePass", hasMathematicsOrSciencePass);
        ruleEvidence.put("missingRequirements", missingRequirements);
        return new RequirementEvaluation(
                submittedTotalPoints, List.copyOf(missingRequirements),
                List.copyOf(missingEvidence), java.util.Collections.unmodifiableMap(ruleEvidence));
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

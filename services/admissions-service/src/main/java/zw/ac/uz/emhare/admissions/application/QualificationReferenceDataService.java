package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.GradeReferenceOption;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationReferenceData;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationReferenceManagementData;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ReferenceOption;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.SubjectReferenceOption;

/** Maintains qualification subjects and grading values used by applicant capture. @author Tinashe K */
@Service
public class QualificationReferenceDataService {

    private static final Set<String> SUBJECT_GROUP_CODES = Set.of(
            "ARTS", "COMMERCIAL", "ENGLISH", "HUMANITIES", "MATHEMATICS", "SCIENCE", "TECHNICAL");

    private final ExamBodyRepository examBodyRepository;
    private final AdmissionSubjectRepository subjectRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final GradingScaleValueRepository gradingScaleValueRepository;
    private final Clock clock;

    public QualificationReferenceDataService(
            ExamBodyRepository examBodyRepository,
            AdmissionSubjectRepository subjectRepository,
            GradingScaleRepository gradingScaleRepository,
            GradingScaleValueRepository gradingScaleValueRepository,
            Clock clock) {
        this.examBodyRepository = examBodyRepository;
        this.subjectRepository = subjectRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradingScaleValueRepository = gradingScaleValueRepository;
        this.clock = clock;
    }

    @Transactional
    public QualificationReferenceData activeReferenceData() {
        return new QualificationReferenceData(
                examBodyRepository.findAllByActiveTrueAndDeletedAtIsNullOrderByNameAsc().stream()
                        .map(value -> new ReferenceOption(value.getId(), value.getCode(), value.getName(), null))
                        .toList(),
                activeSubjectOptions(SubjectLevel.O_LEVEL),
                activeSubjectOptions(SubjectLevel.A_LEVEL),
                activeSubjectOptions(SubjectLevel.OTHER),
                gradeOptions(QualificationLevel.O_LEVEL),
                gradeOptions(QualificationLevel.A_LEVEL));
    }

    @Transactional
    public QualificationReferenceManagementData managementReferenceData() {
        return new QualificationReferenceManagementData(
                managedSubjectOptions(SubjectLevel.O_LEVEL),
                managedSubjectOptions(SubjectLevel.A_LEVEL),
                gradeOptions(QualificationLevel.O_LEVEL),
                gradeOptions(QualificationLevel.A_LEVEL));
    }

    @Transactional
    public SubjectReferenceOption createSubject(
            String levelCode,
            String code,
            String name,
            String subjectGroupCode,
            boolean scienceSubject,
            boolean active) {
        SubjectLevel level = subjectLevel(levelCode);
        String normalizedCode = requiredUppercase(code, "Subject code", 50);
        String normalizedName = requiredText(name, "Subject name", 150);
        String normalizedGroupCode = subjectGroupCode(subjectGroupCode);
        assertSubjectCodeAvailable(level, normalizedCode, null);
        AdmissionSubject subject = new AdmissionSubject(
                normalizedCode, normalizedName, level, normalizedGroupCode, scienceSubject);
        subject.updateReference(normalizedCode, normalizedName, normalizedGroupCode, scienceSubject, active);
        return subjectOption(subjectRepository.saveAndFlush(subject));
    }

    @Transactional
    public SubjectReferenceOption updateSubject(
            UUID subjectId,
            String code,
            String name,
            String subjectGroupCode,
            boolean scienceSubject,
            boolean active,
            long expectedVersion) {
        AdmissionSubject subject = requireSubject(subjectId);
        assertVersion(subject.getVersion(), expectedVersion, "Subject");
        String normalizedCode = requiredUppercase(code, "Subject code", 50);
        String normalizedName = requiredText(name, "Subject name", 150);
        String normalizedGroupCode = subjectGroupCode(subjectGroupCode);
        assertSubjectCodeAvailable(subject.getLevel(), normalizedCode, subject);
        subject.updateReference(normalizedCode, normalizedName, normalizedGroupCode, scienceSubject, active);
        return subjectOption(subjectRepository.saveAndFlush(subject));
    }

    @Transactional
    public void deleteSubject(UUID subjectId, UUID actorUserId, long expectedVersion) {
        AdmissionSubject subject = requireSubject(subjectId);
        assertVersion(subject.getVersion(), expectedVersion, "Subject");
        subject.markDeleted(actorUserId);
        subjectRepository.saveAndFlush(subject);
    }

    @Transactional
    public GradeReferenceOption createGrade(
            String levelCode,
            String grade,
            BigDecimal points,
            boolean pass,
            int sortOrder) {
        QualificationLevel level = qualificationLevel(levelCode);
        GradingScale scale = requireScale(level);
        String normalizedGrade = requiredUppercase(grade, "Grade", 20);
        requireNonNegativeSortOrder(sortOrder);
        assertGradeAvailable(scale, normalizedGrade, null);
        GradingScaleValue value = new GradingScaleValue(scale, normalizedGrade, points, pass, sortOrder);
        return gradeOption(gradingScaleValueRepository.saveAndFlush(value));
    }

    @Transactional
    public GradeReferenceOption updateGrade(
            UUID gradeId,
            String grade,
            BigDecimal points,
            boolean pass,
            int sortOrder,
            long expectedVersion) {
        GradingScaleValue value = requireGrade(gradeId);
        assertVersion(value.getVersion(), expectedVersion, "Grade");
        String normalizedGrade = requiredUppercase(grade, "Grade", 20);
        requireNonNegativeSortOrder(sortOrder);
        assertGradeAvailable(value.getGradingScale(), normalizedGrade, value);
        value.updateReference(normalizedGrade, points, pass, sortOrder);
        return gradeOption(gradingScaleValueRepository.saveAndFlush(value));
    }

    @Transactional
    public void deleteGrade(UUID gradeId, UUID actorUserId, long expectedVersion) {
        GradingScaleValue value = requireGrade(gradeId);
        assertVersion(value.getVersion(), expectedVersion, "Grade");
        value.markDeleted(actorUserId);
        gradingScaleValueRepository.saveAndFlush(value);
    }

    private List<SubjectReferenceOption> activeSubjectOptions(SubjectLevel level) {
        return subjectRepository.findAllByLevelAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(level).stream()
                .map(this::subjectOption)
                .toList();
    }

    private List<SubjectReferenceOption> managedSubjectOptions(SubjectLevel level) {
        return subjectRepository.findAllByLevelAndDeletedAtIsNullOrderByNameAsc(level).stream()
                .map(this::subjectOption)
                .toList();
    }

    private List<GradeReferenceOption> gradeOptions(QualificationLevel level) {
        return gradingScaleRepository.findApplicableScale(level, LocalDate.now(clock))
                .map(scale -> gradingScaleValueRepository
                        .findAllByGradingScaleIdAndDeletedAtIsNullOrderBySortOrderAsc(scale.getId()).stream()
                        .map(this::gradeOption)
                        .toList())
                .orElseGet(List::of);
    }

    private SubjectReferenceOption subjectOption(AdmissionSubject subject) {
        return new SubjectReferenceOption(
                subject.getId(), subject.getCode(), subject.getName(), subject.getSubjectGroupCode(),
                subject.isScienceSubject(), subject.isActive(), subject.getVersion());
    }

    private GradeReferenceOption gradeOption(GradingScaleValue value) {
        return new GradeReferenceOption(
                value.getId(), value.getGrade(), value.getPoints(), value.isPass(),
                value.getSortOrder(), value.getVersion());
    }

    private AdmissionSubject requireSubject(UUID subjectId) {
        return subjectRepository.findByIdAndDeletedAtIsNull(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject was not found."));
    }

    private GradingScaleValue requireGrade(UUID gradeId) {
        return gradingScaleValueRepository.findByIdAndDeletedAtIsNull(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade was not found."));
    }

    private GradingScale requireScale(QualificationLevel level) {
        return gradingScaleRepository.findApplicableScale(level, LocalDate.now(clock))
                .orElseThrow(() -> new IllegalStateException(
                        "No effective grading scale is configured for " + level.name().replace('_', ' ') + "."));
    }

    private void assertSubjectCodeAvailable(SubjectLevel level, String code, AdmissionSubject currentSubject) {
        if (currentSubject != null && currentSubject.getCode().equalsIgnoreCase(code)) {
            return;
        }
        if (subjectRepository.existsByLevelAndCodeIgnoreCaseAndDeletedAtIsNull(level, code)) {
            throw new IllegalArgumentException("A subject with this code already exists at the selected level.");
        }
    }

    private void assertGradeAvailable(GradingScale scale, String grade, GradingScaleValue currentValue) {
        if (currentValue != null && currentValue.getGrade().equalsIgnoreCase(grade)) {
            return;
        }
        if (gradingScaleValueRepository
                .findByGradingScaleIdAndGradeIgnoreCaseAndDeletedAtIsNull(scale.getId(), grade)
                .isPresent()) {
            throw new IllegalArgumentException("This grade already exists in the selected grading scale.");
        }
    }

    private SubjectLevel subjectLevel(String levelCode) {
        try {
            SubjectLevel level = SubjectLevel.valueOf(requiredUppercase(levelCode, "Subject level", 30));
            if (level == SubjectLevel.OTHER) {
                throw new IllegalArgumentException("Only O Level and A Level subjects are managed here.");
            }
            return level;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Only")) {
                throw exception;
            }
            throw new IllegalArgumentException("Subject level must be O_LEVEL or A_LEVEL.");
        }
    }

    private QualificationLevel qualificationLevel(String levelCode) {
        try {
            QualificationLevel level = QualificationLevel.valueOf(requiredUppercase(levelCode, "Grade level", 30));
            if (level != QualificationLevel.O_LEVEL && level != QualificationLevel.A_LEVEL) {
                throw new IllegalArgumentException("Only O Level and A Level grades are managed here.");
            }
            return level;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Only")) {
                throw exception;
            }
            throw new IllegalArgumentException("Grade level must be O_LEVEL or A_LEVEL.");
        }
    }

    private String subjectGroupCode(String value) {
        String normalizedValue = requiredUppercase(value, "Subject group", 50);
        if (!SUBJECT_GROUP_CODES.contains(normalizedValue)) {
            throw new IllegalArgumentException("Select a recognised subject group.");
        }
        return normalizedValue;
    }

    private String requiredUppercase(String value, String fieldName, int maximumLength) {
        return requiredText(value, fieldName, maximumLength).toUpperCase(Locale.ROOT);
    }

    private String requiredText(String value, String fieldName, int maximumLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maximumLength + " characters.");
        }
        return normalizedValue;
    }

    private void requireNonNegativeSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("Sort order cannot be negative.");
        }
    }

    private void assertVersion(long actualVersion, long expectedVersion, String recordName) {
        if (actualVersion != expectedVersion) {
            throw new IllegalStateException(recordName + " was changed by another user. Refresh and try again.");
        }
    }
}

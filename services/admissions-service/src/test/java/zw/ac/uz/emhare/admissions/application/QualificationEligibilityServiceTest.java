package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class QualificationEligibilityServiceTest {

    @Mock private ApplicantQualificationResultRepository resultRepository;
    @Mock private GradingScaleRepository gradingScaleRepository;
    @Mock private GradingScaleValueRepository gradingScaleValueRepository;
    @Mock private Clock clock;

    @InjectMocks private QualificationEligibilityService service;

    @Test
    void recalculatesAndStoresExactZimsecALevelPointsWithoutManualInput() {
        UUID applicationId = UUID.randomUUID();
        ExamBody zimsec = new ExamBody("ZIMSEC", "Zimbabwe School Examinations Council", null);
        ApplicantQualificationSitting sitting = new ApplicantQualificationSitting(
                null, QualificationLevel.A_LEVEL, zimsec, "CENTRE", "CANDIDATE", 2026);
        List<ApplicantQualificationResult> results = List.of(
                result(sitting, "A"), result(sitting, "B"), result(sitting, "C"),
                result(sitting, "D"), result(sitting, "E"));
        when(resultRepository.findAllForApplication(applicationId)).thenReturn(results);

        QualificationPointsCalculator.EligibilitySnapshot snapshot =
                service.recalculateApplicationPoints(applicationId);

        assertEquals(new BigDecimal("15.00"), snapshot.totalPoints());
        assertEquals(List.of(
                new BigDecimal("5.00"), new BigDecimal("4.00"), new BigDecimal("3.00"),
                new BigDecimal("2.00"), new BigDecimal("1.00")),
                results.stream().map(ApplicantQualificationResult::getPoints).toList());
        verify(resultRepository).saveAll(results);
    }

    @Test
    void recognisesManagedZimsecExamBodyCodeVariantsWhenCalculatingPoints() {
        UUID applicationId = UUID.randomUUID();
        ExamBody managedZimsecExamBody = new ExamBody(
                "ZIMSEC_A14D46E2", "Zimbabwe School Examinations Council A14D46E2", null);
        ApplicantQualificationSitting sitting = new ApplicantQualificationSitting(
                null, QualificationLevel.A_LEVEL, managedZimsecExamBody, "801", "564", 1974);
        List<ApplicantQualificationResult> results = List.of(
                result(sitting, "E"), result(sitting, "A"), result(sitting, "A"));
        when(resultRepository.findAllForApplication(applicationId)).thenReturn(results);

        QualificationPointsCalculator.EligibilitySnapshot snapshot =
                service.recalculateApplicationPoints(applicationId);

        assertEquals(new BigDecimal("11.00"), snapshot.totalPoints());
        assertEquals(List.of(
                new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("5.00")),
                results.stream().map(ApplicantQualificationResult::getPoints).toList());
        verify(resultRepository).saveAll(results);
    }

    @Test
    void satisfiesMathematicsOrScienceRuleFromManagedSubjectMetadata() {
        UUID applicationId = UUID.randomUUID();
        Application application = mock(Application.class);
        AdmissionRequirementSet requirementSet = mock(AdmissionRequirementSet.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getCalculatedTotalPoints()).thenReturn(new BigDecimal("10.00"));
        when(requirementSet.getMinimumTotalPoints()).thenReturn(new BigDecimal("8.00"));
        when(requirementSet.isRequiresMathematicsOrScience()).thenReturn(true);
        ExamBody zimsec = new ExamBody("ZIMSEC", "Zimbabwe School Examinations Council", null);
        ApplicantQualificationSitting sitting = new ApplicantQualificationSitting(
                null, QualificationLevel.A_LEVEL, zimsec, "CENTRE", "CANDIDATE", 2026);
        AdmissionSubject physics = new AdmissionSubject(
                "PHYS", "Physics", SubjectLevel.A_LEVEL, "SCIENCE", true);
        ApplicantQualificationResult physicsResult = new ApplicantQualificationResult(
                sitting, physics, physics.getName(), "B");
        when(resultRepository.findAllForApplication(applicationId)).thenReturn(List.of(physicsResult));

        QualificationEligibilityService.RequirementEvaluation evaluation =
                service.evaluateRequirements(application, requirementSet);

        assertEquals(new BigDecimal("10.00"), evaluation.totalPoints());
        assertEquals(List.of(), evaluation.missingRequirements());
        assertEquals(true, evaluation.ruleEvidence().get("mathematicsOrSciencePass"));
    }

    private ApplicantQualificationResult result(ApplicantQualificationSitting sitting, String grade) {
        return new ApplicantQualificationResult(sitting, null, "Subject", grade);
    }
}

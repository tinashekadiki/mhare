package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubject;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationResult;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ExamBody;
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
import zw.ac.uz.emhare.admissions.domain.model.QualificationLevel;
import zw.ac.uz.emhare.admissions.domain.model.SubjectLevel;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubjectRequirement;
import zw.ac.uz.emhare.admissions.domain.model.SubjectRequirementType;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQualificationRequirementGroup;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQualificationRequirementItem;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class QualificationEligibilityServiceTest {

    @Mock private ApplicantQualificationResultRepository resultRepository;
    @Mock private GradingScaleRepository gradingScaleRepository;
    @Mock private GradingScaleValueRepository gradingScaleValueRepository;
    @Mock private Clock clock;
    @Mock private AdmissionSubjectRequirementRepository subjectRequirementRepository;
    @Mock private ApplicantEmploymentHistoryRepository employmentHistoryRepository;
    @Mock private ApplicationProfessionalAchievementRepository professionalAchievementRepository;
    @Mock private ApplicationPriorUzDeclarationRepository priorUzDeclarationRepository;
    @Mock private ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    @Mock private AdmissionQualificationRequirementGroupRepository qualificationGroupRepository;
    @Mock private AdmissionQualificationRequirementItemRepository qualificationItemRepository;
    @Mock private AdvancedAdmissionRuleEvaluator advancedRuleEvaluator;

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
        Applicant applicant = mock(Applicant.class);
        AdmissionRequirementSet requirementSet = mock(AdmissionRequirementSet.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
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

    @Test
    void appliesTheConfiguredGenderCutoffAndRecordsTheExactThreshold() {
        UUID applicationId = UUID.randomUUID();
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        AdmissionRequirementSet requirementSet = mock(AdmissionRequirementSet.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getGenderCode()).thenReturn("FEMALE");
        when(application.getCalculatedTotalPoints()).thenReturn(new BigDecimal("10.00"));
        when(requirementSet.getMinimumTotalPoints()).thenReturn(new BigDecimal("8.00"));
        when(requirementSet.getFemaleCutoffPoints()).thenReturn(new BigDecimal("12.00"));
        when(resultRepository.findAllForApplication(applicationId)).thenReturn(List.of());

        var evaluation = service.evaluateRequirements(application, requirementSet);

        assertEquals(List.of("minimum total points"), evaluation.missingRequirements());
        assertEquals(new BigDecimal("12.00"), evaluation.missingRequirementEvidence().getFirst().get("required"));
    }

    @Test
    void evaluatesAConfiguredCompulsorySubjectAndMinimumGrade() {
        UUID applicationId = UUID.randomUUID();
        UUID requirementSetId = UUID.randomUUID();
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        AdmissionRequirementSet requirementSet = mock(AdmissionRequirementSet.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
        when(application.getCalculatedTotalPoints()).thenReturn(new BigDecimal("4.00"));
        when(requirementSet.getId()).thenReturn(requirementSetId);
        ExamBody zimsec = new ExamBody("ZIMSEC", "Zimbabwe School Examinations Council", null);
        ApplicantQualificationSitting sitting = new ApplicantQualificationSitting(
                null, QualificationLevel.A_LEVEL, zimsec, "CENTRE", "CANDIDATE", 2026);
        AdmissionSubject physics = new AdmissionSubject("PHYS", "Physics", SubjectLevel.A_LEVEL, "SCIENCE", true);
        ReflectionTestUtils.setField(physics, "id", UUID.randomUUID());
        ApplicantQualificationResult result = new ApplicantQualificationResult(sitting, physics, physics.getName(), "B");
        AdmissionSubjectRequirement subjectRequirement = new AdmissionSubjectRequirement(
                requirementSet, SubjectLevel.A_LEVEL, SubjectRequirementType.COMPULSORY, 1);
        ReflectionTestUtils.setField(subjectRequirement, "subject", physics);
        ReflectionTestUtils.setField(subjectRequirement, "minimumGrade", "C");
        when(resultRepository.findAllForApplication(applicationId)).thenReturn(List.of(result));
        when(subjectRequirementRepository
                .findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(requirementSetId))
                .thenReturn(List.of(subjectRequirement));

        var evaluation = service.evaluateRequirements(application, requirementSet);

        assertEquals(List.of(), evaluation.missingRequirements());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) evaluation.ruleEvidence().get("subjectRequirements");
        assertEquals(true, evidence.getFirst().get("satisfied"));
    }

    @Test
    void satisfiesAConfiguredQualificationAlternativeAndRecordsGroupEvidence() {
        UUID applicationId = UUID.randomUUID();
        UUID requirementSetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        AdmissionRequirementSet requirementSet = mock(AdmissionRequirementSet.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
        when(application.getCalculatedTotalPoints()).thenReturn(BigDecimal.ZERO);
        when(requirementSet.getId()).thenReturn(requirementSetId);
        ApplicantQualificationSitting degree = new ApplicantQualificationSitting(
                null, QualificationLevel.DEGREE, null, "UZ", null, 2025);
        ApplicantQualificationResult degreeEvidence = result(degree, "PASS");
        AdmissionQualificationRequirementGroup group = new AdmissionQualificationRequirementGroup(
                requirementSet, "PRIOR_DEGREE", "Prior degree", 1, 1);
        ReflectionTestUtils.setField(group, "id", groupId);
        AdmissionQualificationRequirementItem item = new AdmissionQualificationRequirementItem(
                group, QualificationLevel.DEGREE, 1, null, null, 1);
        when(resultRepository.findAllForApplication(applicationId)).thenReturn(List.of(degreeEvidence));
        when(qualificationGroupRepository
                .findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(requirementSetId))
                .thenReturn(List.of(group));
        when(qualificationItemRepository
                .findAllByRequirementGroupIdAndDeletedAtIsNullOrderBySortOrderAsc(groupId))
                .thenReturn(List.of(item));

        var evaluation = service.evaluateRequirements(application, requirementSet);

        assertEquals(List.of(), evaluation.missingRequirements());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) evaluation.ruleEvidence().get("qualificationGroups");
        assertEquals(true, evidence.getFirst().get("satisfied"));
    }

    private ApplicantQualificationResult result(ApplicantQualificationSitting sitting, String grade) {
        return new ApplicantQualificationResult(sitting, null, "Subject", grade);
    }
}

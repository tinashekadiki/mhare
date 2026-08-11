package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsSelectionOfferServiceCycleUpdateTest {

    @Mock private AdmissionCycleRepository admissionCycleRepository;
    @Mock private AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
    @Mock private AdmissionCycleArchiveSummaryRepository admissionCycleArchiveSummaryRepository;
    @Mock private ApplicationTypeRepository applicationTypeRepository;
    @Mock private ApplicationTypeDocumentRequirementRepository applicationTypeDocumentRequirementRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    @Mock private AdmissionRequirementSetRepository requirementSetRepository;
    @Mock private ApplicationEvaluationRepository evaluationRepository;
    @Mock private QualificationEligibilityService qualificationEligibilityService;
    @Mock private ApplicationStatusEventRepository applicationStatusEventRepository;
    @Mock private SelectionRoundRepository selectionRoundRepository;
    @Mock private SelectionDecisionRepository selectionDecisionRepository;
    @Mock private AcademicReviewAssignmentRepository academicReviewAssignmentRepository;
    @Mock private OfferBatchRepository offerBatchRepository;
    @Mock private AdmissionOfferRepository offerRepository;
    @Mock private OfferConditionRepository offerConditionRepository;
    @Mock private OfferDispatchRepository offerDispatchRepository;
    @Mock private OfferResponseRepository offerResponseRepository;
    @Mock private OfferStatusEventRepository offerStatusEventRepository;
    @Mock private AdmissionsIdentifierGenerator identifierGenerator;
    @Mock private AdmissionsIntegrationOutboxService integrationOutboxService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Clock clock;

    @InjectMocks
    private AdmissionsSelectionOfferService service;

    @Test
    void createsOfferBatchForApprovedSelectionRoundWhileIntakeRemainsOpen() {
        // NB: this used to also approve the created batch via the now-retired
        // AdmissionsSelectionOfferService.approveOfferBatch (ADR-0014 hard cutover,
        // 2026-08-11 admissions backend rolling-workflow plan, Task 4). createOfferBatch
        // itself is unchanged and still in scope, so this test was trimmed rather than
        // deleted.
        UUID intakeId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID selectionRoundId = UUID.randomUUID();
        UUID offerBatchId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Instant now = Instant.parse("2027-01-15T10:00:00Z");

        AdmissionCycle admissionCycle = new AdmissionCycle(
                UUID.randomUUID(), intakeId, "JAN27", "January 2027",
                now.minusSeconds(3600), now.plusSeconds(3600));
        ReflectionTestUtils.setField(admissionCycle, "id", admissionCycleId);
        admissionCycle.open(now);
        SelectionRound selectionRound = new SelectionRound(
                admissionCycle, "2027R1", "2027 first selection round");
        ReflectionTestUtils.setField(selectionRound, "id", selectionRoundId);
        selectionRound.open(now);
        selectionRound.approve(actorUserId, now);

        when(admissionsIntakeProjectionService.requireProjection(intakeId)).thenReturn(admissionCycle);
        when(selectionRoundRepository.findById(selectionRoundId)).thenReturn(Optional.of(selectionRound));
        when(offerBatchRepository.save(org.mockito.ArgumentMatchers.any(OfferBatch.class))).thenAnswer(invocation -> {
            OfferBatch batch = invocation.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", offerBatchId);
            return batch;
        });

        OfferBatchSummary created = service.createOfferBatch(
                intakeId, selectionRoundId, "2027-R1-OFFERS", "First merit offers", "INSTITUTION", null);

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(admissionCycle.getStatus()).isEqualTo(AdmissionCycleStatus.OPEN);
    }

    @Test
    void updatesDraftCycleThroughTheGovernedServiceBoundary() {
        UUID admissionCycleId = UUID.randomUUID();
        UUID academicYearId = UUID.randomUUID();
        UUID intakeId = UUID.randomUUID();
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "JAN27",
                "January 2027 Cycle",
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-31T23:59:00Z"));
        when(admissionCycleRepository.findById(admissionCycleId)).thenReturn(Optional.of(cycle));
        when(admissionCycleRepository.saveAndFlush(cycle)).thenReturn(cycle);

        AdmissionCycleSummary result = service.updateAdmissionCycle(
                admissionCycleId,
                academicYearId,
                intakeId,
                "AUG27",
                "August 2027 Cycle",
                Instant.parse("2027-03-01T06:00:00Z"),
                Instant.parse("2027-04-30T15:00:00Z"),
                5,
                null,
                "Corrected cycle dates after calendar approval.",
                0);

        assertThat(result.academicYearId()).isEqualTo(academicYearId);
        assertThat(result.intakeId()).isEqualTo(intakeId);
        assertThat(result.code()).isEqualTo("AUG27");
        assertThat(result.maximumProgrammeChoices()).isEqualTo(5);
        assertThat(result.changeReason()).isEqualTo("Corrected cycle dates after calendar approval.");
        verify(admissionCycleRepository).saveAndFlush(cycle);
    }

    @Test
    void createsAnInactiveApplicationTypeUntilAdmissionsActivatesIt() {
        ApplicationType applicationType = new ApplicationType(
                "POSTGRAD", "Postgraduate", true, true, false);
        when(applicationTypeRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("POSTGRAD"))
                .thenReturn(false);
        when(applicationTypeRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ApplicationType.class)))
                .thenReturn(applicationType);

        ApplicationTypeSummary result = service.createApplicationType(
                " postgrad ", "Postgraduate", true, true, null, null, null, false);

        assertThat(result.code()).isEqualTo("POSTGRAD");
        assertThat(result.requiresEmploymentHistory()).isTrue();
        assertThat(result.requiresReferees()).isTrue();
        assertThat(result.active()).isFalse();
        verify(applicationTypeDocumentRequirementRepository)
                .saveAllAndFlush(org.mockito.ArgumentMatchers.argThat(requirements -> {
                    List<ApplicationTypeDocumentRequirement> requirementList =
                            java.util.stream.StreamSupport.stream(requirements.spliterator(), false).toList();
                    return requirementList.size() == 2
                            && requirementList.stream().anyMatch(requirement ->
                            "IDENTITY_DOCUMENT".equals(requirement.getRequirementCode())
                                    && requirement.isRequired())
                            && requirementList.stream().anyMatch(requirement ->
                            "ACADEMIC_QUALIFICATION_EVIDENCE".equals(requirement.getRequirementCode())
                                    && requirement.isRequired());
                }));
    }

    @Test
    void activatesAnApplicationTypeWithOptimisticLockingAndChangeEvidence() {
        UUID applicationTypeId = UUID.randomUUID();
        ApplicationType applicationType = new ApplicationType(
                "UNDERGRAD", "Undergraduate", false, false, false);
        when(applicationTypeRepository.findById(applicationTypeId)).thenReturn(Optional.of(applicationType));
        when(applicationTypeRepository.saveAndFlush(applicationType)).thenReturn(applicationType);

        ApplicationTypeSummary result = service.updateApplicationType(
                applicationTypeId,
                "Undergraduate applications",
                false,
                false,
                null,
                null,
                null,
                true,
                "Admissions approved this route for applicant use.",
                0);

        assertThat(result.name()).isEqualTo("Undergraduate applications");
        assertThat(result.active()).isTrue();
        verify(applicationTypeRepository).saveAndFlush(applicationType);
    }

    @Test
    void rejectsAnApplicationTypeUpdateBasedOnAStaleVersion() {
        UUID applicationTypeId = UUID.randomUUID();
        ApplicationType applicationType = new ApplicationType(
                "TRANSFER", "Transfer", false, false, false);
        when(applicationTypeRepository.findById(applicationTypeId)).thenReturn(Optional.of(applicationType));

        assertThatThrownBy(() -> service.updateApplicationType(
                applicationTypeId,
                "Transfer applicants",
                false,
                false,
                null,
                null,
                null,
                true,
                "Activate the approved transfer route.",
                9))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed by another user");
    }

    @Test
    void approvingNewRequirementSetRetiresOverlappingApprovedVersion() {
        UUID requirementSetId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID applicationTypeId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        ApplicationType applicationType = new ApplicationType(
                "UNDERGRAD", "Undergraduate", false, false);
        ReflectionTestUtils.setField(applicationType, "id", applicationTypeId);
        AdmissionCycle admissionCycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "AUG27", "August 2027",
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2027-03-31T23:59:00Z"));
        ReflectionTestUtils.setField(admissionCycle, "id", admissionCycleId);
        AdmissionRequirementSet previous = new AdmissionRequirementSet(
                programmeId, applicationType, admissionCycle, "2027.1",
                java.time.LocalDate.parse("2027-01-01"), null, new java.math.BigDecimal("8.00"),
                null, null, true, false, null, null);
        ReflectionTestUtils.setField(previous, "id", UUID.randomUUID());
        previous.approve(actorUserId, Instant.parse("2026-12-01T00:00:00Z"));
        AdmissionRequirementSet replacement = new AdmissionRequirementSet(
                programmeId, applicationType, admissionCycle, "2027.2",
                java.time.LocalDate.parse("2027-02-01"), null, new java.math.BigDecimal("10.00"),
                null, null, true, true, null, null);
        ReflectionTestUtils.setField(replacement, "id", requirementSetId);
        when(requirementSetRepository.findById(requirementSetId)).thenReturn(Optional.of(replacement));
        when(requirementSetRepository.findApprovedForRouteForUpdate(
                programmeId, applicationTypeId, admissionCycleId)).thenReturn(List.of(previous));
        when(clock.instant()).thenReturn(Instant.parse("2027-01-15T10:00:00Z"));
        when(requirementSetRepository.saveAndFlush(replacement)).thenReturn(replacement);

        AdmissionRequirementSetSummary approved = service.approveRequirementSet(requirementSetId, actorUserId);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.requiresMathematicsOrScience()).isTrue();
        assertThat(previous.getStatus()).isEqualTo(RequirementSetStatus.RETIRED);
        verify(requirementSetRepository).saveAllAndFlush(List.of(previous));
    }
}

package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.command.*;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationFeePolicySnapshot;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeProgrammeMapping;
import zw.ac.uz.emhare.admissions.domain.model.PaymentReferenceStatus;
import zw.ac.uz.emhare.admissions.domain.model.QualificationResultStatus;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationSittingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationClearanceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationEvaluationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationFeeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationPaymentReferenceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeOptionSnapshotRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeProgrammeMappingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveApplicationFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeLine;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeStructure;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;

@ExtendWith(MockitoExtension.class)
class AdmissionsApplicationServiceTest {
  private static final String AUTHORIZATION = "Bearer applicant-token";
  private static final UUID PROGRAMME_LEVEL_ID =
      UUID.fromString("f091ab26-33dc-47d2-97b8-f713ea135fd6");

  @Mock private ApplicantRepository applicantRepository;

  @Mock private AdmissionsIntakeProjectionService admissionsIntakeProjectionService;

  @Mock private ApplicationTypeRepository applicationTypeRepository;
  @Mock private ApplicationTypeProgrammeMappingRepository programmeMappingRepository;
  @Mock private ApplicationProgrammeOptionSnapshotRepository programmeOptionSnapshotRepository;

  @Mock private ApplicationFeeRepository applicationFeeRepository;

  @Mock private ApplicationRepository applicationRepository;

  @Mock private ApplicationProgrammeChoiceRepository programmeChoiceRepository;

  @Mock private ApplicationPaymentReferenceRepository applicationPaymentReferenceRepository;

  @Mock private ApplicationStatusEventRepository statusEventRepository;

  @Mock private ApplicationEvaluationRepository evaluationRepository;
  @Mock private ApplicationClearanceRepository clearanceRepository;
  @Mock private ApplicationSectionRepository sectionRepository;
  @Mock private ApplicantQualificationSittingRepository qualificationSittingRepository;

  @Mock private AdmissionsIdentifierGenerator admissionsIdentifierGenerator;

  @Mock private AdmissionsIntegrationOutboxService integrationOutboxService;

  @Mock private FinanceCatalogueClient financeCatalogueClient;

  @Mock private AdmissionsDocumentService admissionsDocumentService;

  @Mock private ApplicantApplicationWorkspaceService applicantApplicationWorkspaceService;

  @Mock private QualificationEligibilityService qualificationEligibilityService;

  @Mock private ApplicationDuplicateCheckService duplicateCheckService;

  private AdmissionsApplicationService admissionsApplicationService;
  private final Instant currentInstant = Instant.parse("2027-01-15T10:00:00Z");
  private final Clock clock = Clock.fixed(currentInstant, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    admissionsApplicationService =
        new AdmissionsApplicationService(
            applicantRepository,
            admissionsIntakeProjectionService,
            applicationTypeRepository,
            programmeMappingRepository,
            programmeOptionSnapshotRepository,
            applicationRepository,
            programmeChoiceRepository,
            applicationPaymentReferenceRepository,
            statusEventRepository,
            evaluationRepository,
            clearanceRepository,
            sectionRepository,
            qualificationSittingRepository,
            admissionsIdentifierGenerator,
            integrationOutboxService,
            financeCatalogueClient,
            admissionsDocumentService,
            applicantApplicationWorkspaceService,
            qualificationEligibilityService,
            clock,
            new ObjectMapper(),
            duplicateCheckService);
  }

  @Test
  void startApplication_shouldResolveAndSnapshotFinanceOwnedApplicationFee() {
    UUID userId = UUID.randomUUID();
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId = UUID.randomUUID();
    UUID programmeId = UUID.randomUUID();
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            intakeId,
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    admissionCycle.open(currentInstant);
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
    UUID financeFeeStructureId = UUID.randomUUID();
    applicationType.associateFeeStructure(
        financeFeeStructureId, "APP-UG-LOCAL", "Local undergraduate application");
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            userId,
            UUID.randomUUID(),
            "LOCAL",
            "Nyasha",
            "Moyo",
            null,
            "nyasha@example.test",
            intakeId,
            applicationTypeId,
            List.of(programmeId));
    when(applicantRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(applicantRepository.save(any(Applicant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(admissionsIntakeProjectionService.requireOpenIntake(intakeId))
        .thenReturn(resolvedIntake(admissionCycle, List.of(programmeId)));
    when(applicationTypeRepository.findById(applicationTypeId))
        .thenReturn(Optional.of(applicationType));
    allowProgrammeForRoute(applicationTypeId, applicationType, programmeId);
    when(financeCatalogueClient.resolveApplicationFeeStructure(
            org.mockito.ArgumentMatchers.eq(AUTHORIZATION),
            any(ResolveApplicationFeeStructureRequest.class)))
        .thenReturn(
            new ResolvedApplicationFeeStructure(
                financeFeeStructureId,
                "APP-UG-LOCAL",
                "Local undergraduate application",
                "APPLICATION",
                "ACTIVE",
                4,
                "USD",
                currentInstant.minusSeconds(86400),
                null,
                "LOCAL",
                PROGRAMME_LEVEL_ID,
                "UG",
                List.of(
                    new ResolvedApplicationFeeLine(
                        UUID.randomUUID(),
                        1,
                        "APPLICATION",
                        new BigDecimal("25.00"),
                        "USD",
                        "RATED",
                        "APPROVED"))));
    when(admissionsIdentifierGenerator.nextApplicantNumber()).thenReturn("APP-0001");
    when(admissionsIdentifierGenerator.nextApplicationNumber("AUG"))
        .thenReturn("EMH-2027-AUG-00000001");
    UUID savedApplicationId = UUID.randomUUID();
    when(applicationRepository.saveAndFlush(any(Application.class)))
        .thenAnswer(
            invocation -> {
              Application savedApplication = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedApplication, "id", savedApplicationId);
              return savedApplication;
            });
    when(programmeChoiceRepository.saveAllAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ApplicationSummary summary =
        admissionsApplicationService.startApplication(command, AUTHORIZATION);

    assertEquals("DRAFT", summary.status());
    assertEquals("APP-0001", summary.applicantNumber());
    assertEquals("EMH-2027-AUG-00000001", summary.applicationNumber());
    assertTrue(summary.paymentRequired());
    assertFalse(summary.canSubmit());
    verify(applicantRepository).save(any(Applicant.class));
    ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
    verify(applicationRepository).saveAndFlush(applicationCaptor.capture());
    ApplicationFeePolicySnapshot feeSnapshot =
        applicationCaptor.getValue().getApplicationFeePolicySnapshot();
    assertEquals(financeFeeStructureId, feeSnapshot.getFeeStructureId());
    assertEquals(4L, feeSnapshot.getFeeStructureVersion());
    assertEquals(PROGRAMME_LEVEL_ID, feeSnapshot.getProgrammeLevelId());
    assertEquals("LOCAL", feeSnapshot.getApplicantCategoryCode());
    assertEquals(new BigDecimal("25.00"), feeSnapshot.getAmount());
    assertEquals(currentInstant, feeSnapshot.getEffectiveAt());
    assertEquals(financeFeeStructureId, summary.feePolicy().feeStructureId());
    assertEquals("APP-UG-LOCAL", summary.feePolicy().feeStructureCode());
    assertEquals(new BigDecimal("25.00"), summary.feePolicy().amount());
    assertEquals("USD", summary.feePolicy().currencyCode());
    verify(integrationOutboxService)
        .enqueueApplicationFeeRequired(
            savedApplicationId,
            userId,
            command.applicantKeycloakUserId(),
            new BigDecimal("25.00"),
            "USD");
    ArgumentCaptor<ApplicationStatusEvent> eventCaptor =
        ArgumentCaptor.forClass(ApplicationStatusEvent.class);
    verify(statusEventRepository).save(eventCaptor.capture());
    verify(applicantApplicationWorkspaceService).initializeSections(any(Application.class));
    assertEquals(
        List.of("BSCIT"),
        summary.programmeChoices().stream()
            .map(ApplicationProgrammeChoiceSummary::programmeCode)
            .toList());
  }

  @Test
  void startApplication_shouldCreateDraftWithoutProgrammeChoicesForWorkspaceCompletion() {
    UUID userId = UUID.randomUUID();
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId = UUID.randomUUID();
    UUID availableProgrammeId = UUID.randomUUID();
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            intakeId,
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    admissionCycle.open(currentInstant);
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
    applicationType.recordFeeFreeDecision(
        UUID.randomUUID(), "Council-approved application-fee waiver.", currentInstant);
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            userId,
            UUID.randomUUID(),
            "LOCAL",
            "Nyasha",
            "Moyo",
            null,
            "nyasha@example.test",
            intakeId,
            applicationTypeId,
            List.of());

    when(applicantRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(applicantRepository.save(any(Applicant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(admissionsIntakeProjectionService.requireOpenIntake(intakeId))
        .thenReturn(resolvedIntake(admissionCycle, List.of(availableProgrammeId)));
    when(applicationTypeRepository.findById(applicationTypeId))
        .thenReturn(Optional.of(applicationType));
    allowProgrammeForRoute(applicationTypeId, applicationType, availableProgrammeId);
    when(admissionsIdentifierGenerator.nextApplicantNumber()).thenReturn("APP-0002");
    when(admissionsIdentifierGenerator.nextApplicationNumber("AUG"))
        .thenReturn("EMH-2027-AUG-00000002");
    when(applicationRepository.saveAndFlush(any(Application.class)))
        .thenAnswer(
            invocation -> {
              Application savedApplication = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedApplication, "id", UUID.randomUUID());
              return savedApplication;
            });

    ApplicationSummary summary =
        admissionsApplicationService.startApplication(command, AUTHORIZATION);

    assertEquals("DRAFT", summary.status());
    assertTrue(summary.programmeChoices().isEmpty());
    verify(applicantApplicationWorkspaceService).initializeSections(any(Application.class));
    verify(programmeChoiceRepository, org.mockito.Mockito.never()).saveAllAndFlush(any());
  }

  @Test
  void startApplication_shouldRejectClosedIntake() {
    UUID intakeId = UUID.randomUUID();
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            intakeId,
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "LOCAL",
            "Nyasha",
            "Moyo",
            null,
            "nyasha@example.test",
            intakeId,
            UUID.randomUUID(),
            List.of(UUID.randomUUID()));

    when(admissionsIntakeProjectionService.requireOpenIntake(intakeId))
        .thenThrow(new IllegalStateException("Intake is not open for applications."));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> admissionsApplicationService.startApplication(command, AUTHORIZATION));

    assertEquals("Intake is not open for applications.", exception.getMessage());
  }

  @Test
  void startApplication_shouldRejectDuplicateNationalIdWithinTheSameIntake() {
    UUID userId = UUID.randomUUID();
    UUID intakeId = UUID.randomUUID();
    UUID intakeProjectionId = UUID.randomUUID();
    UUID applicationTypeId = UUID.randomUUID();
    UUID programmeId = UUID.randomUUID();
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            intakeId,
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    ReflectionTestUtils.setField(admissionCycle, "id", intakeProjectionId);
    admissionCycle.open(currentInstant);
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
    applicationType.recordFeeFreeDecision(
        UUID.randomUUID(), "Council-approved application-fee waiver.", currentInstant);
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            userId,
            UUID.randomUUID(),
            "LOCAL",
            "Nyasha",
            "Moyo",
            "63-123456A78",
            "nyasha@example.test",
            intakeId,
            applicationTypeId,
            List.of(programmeId));
    Applicant existingApplicant =
        new Applicant(userId, "APP-0001", "LOCAL", "Nyasha", "Moyo", "nyasha@example.test");

    when(admissionsIntakeProjectionService.requireOpenIntake(intakeId))
        .thenReturn(resolvedIntake(admissionCycle, List.of(programmeId)));
    when(applicationTypeRepository.findById(applicationTypeId))
        .thenReturn(Optional.of(applicationType));
    allowProgrammeForRoute(applicationTypeId, applicationType, programmeId);
    when(applicantRepository.findByUserId(userId)).thenReturn(Optional.of(existingApplicant));
    when(applicationRepository.findByIntakeIdAndApplicantNationalIdNumber(intakeId, "63-123456A78"))
        .thenReturn(
            List.of(
                new Application(
                    admissionCycle, existingApplicant, applicationType, "EMH-1", false)));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> admissionsApplicationService.startApplication(command, AUTHORIZATION));

    assertEquals(
        "An application already exists for this national ID number in this intake.",
        exception.getMessage());
    verify(applicationRepository, org.mockito.Mockito.never()).saveAndFlush(any(Application.class));
  }

  @Test
  void startApplication_shouldRejectDuplicateProgrammeSelectionsBeforeWritingApplication() {
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId = UUID.randomUUID();
    UUID programmeId = UUID.randomUUID();
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            intakeId,
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    admissionCycle.open(currentInstant);
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "LOCAL",
            "Nyasha",
            "Moyo",
            null,
            "nyasha@example.test",
            intakeId,
            applicationTypeId,
            List.of(programmeId, programmeId));
    when(admissionsIntakeProjectionService.requireOpenIntake(intakeId))
        .thenReturn(resolvedIntake(admissionCycle, List.of(programmeId)));
    when(applicationTypeRepository.findById(applicationTypeId))
        .thenReturn(Optional.of(applicationType));
    allowProgrammeForRoute(applicationTypeId, applicationType, programmeId);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> admissionsApplicationService.startApplication(command, AUTHORIZATION));

    assertEquals("The same programme cannot be selected more than once.", exception.getMessage());
  }

  @Test
  void startApplication_shouldRejectProgrammeOutsideConfiguredRouteBeforeWritingApplication() {
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId = UUID.randomUUID();
    UUID mbaProgrammeId = UUID.randomUUID();
    UUID unrelatedProgrammeId = UUID.randomUUID();
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            intakeId,
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    admissionCycle.open(currentInstant);
    ApplicationType applicationType = new ApplicationType("MBA", "MBA", true, true);
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "LOCAL",
            "Nyasha",
            "Moyo",
            null,
            "nyasha@example.test",
            intakeId,
            applicationTypeId,
            List.of(unrelatedProgrammeId));
    when(admissionsIntakeProjectionService.requireOpenIntake(intakeId))
        .thenReturn(resolvedIntake(admissionCycle, List.of(mbaProgrammeId, unrelatedProgrammeId)));
    when(applicationTypeRepository.findById(applicationTypeId))
        .thenReturn(Optional.of(applicationType));
    allowProgrammeForRoute(applicationTypeId, applicationType, mbaProgrammeId);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> admissionsApplicationService.startApplication(command, AUTHORIZATION));

    assertTrue(exception.getMessage().contains("application route and intake"));
    verify(applicationRepository, org.mockito.Mockito.never()).saveAndFlush(any(Application.class));
  }

  private void allowProgrammeForRoute(
      UUID applicationTypeId, ApplicationType applicationType, UUID programmeId) {
    when(programmeMappingRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
                applicationTypeId))
        .thenReturn(
            List.of(
                new ApplicationTypeProgrammeMapping(
                    applicationType,
                    programmeId,
                    "BSCIT",
                    "Bachelor of Science in Information Technology")));
  }

  private AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake resolvedIntake(
      AdmissionCycle projection, List<UUID> programmeIds) {
    List<AcademicProgrammeOption> programmes =
        programmeIds.stream()
            .map(
                programmeId ->
                    new AcademicProgrammeOption(
                        programmeId,
                        "BSCIT",
                        "Bachelor of Science in Information Technology",
                        "Bachelor of Science Honours Degree",
                        UUID.randomUUID(),
                        "2027.1",
                        UUID.randomUUID(),
                        "Department of Computing",
                        8,
                        12,
                        UUID.randomUUID(),
                        "DEGREE",
                        "Degree",
                        PROGRAMME_LEVEL_ID,
                        "UG",
                        "Undergraduate",
                        0,
                        0,
                        List.of()))
            .toList();
    return new AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake(
        new AcademicAdmissionsIntake(
            projection.getIntakeId(),
            projection.getAcademicYearId(),
            "2027",
            "AUG",
            "August Intake",
            LocalDate.parse("2027-01-01"),
            LocalDate.parse("2027-12-31"),
            "OPEN",
            3,
            programmes));
  }

  @Test
  void submitApplication_shouldAcceptPaymentEvidenceReadyDraftBeforeFinanceReviewClearance() {
    UUID applicationId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    UUID applicantUserId = application.getApplicant().getUserId();
    ReflectionTestUtils.setField(application, "id", applicationId);
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationPaymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(Optional.empty());
    when(qualificationEligibilityService.recalculateApplicationPoints(applicationId))
        .thenReturn(
            new QualificationPointsCalculator.EligibilitySnapshot(
                new BigDecimal("9.00"), List.of(), List.of()));

    ApplicationSummary summary =
        admissionsApplicationService.submitApplication(applicationId, applicantUserId);

    assertEquals("SUBMITTED", summary.status());
    assertFalse(summary.canEnterReview());
    verify(applicantApplicationWorkspaceService).assertReadyForSubmission(application);
    verify(admissionsDocumentService).assertReadyForSubmission(application);
  }

  @Test
  void submitApplication_shouldSubmitPaidApplicantOwnedDraft() {
    UUID applicationId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    UUID applicantUserId = application.getApplicant().getUserId();
    ReflectionTestUtils.setField(application, "id", applicationId);
    application.confirmPayment(currentInstant.minusSeconds(60));
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationPaymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(Optional.empty());
    when(qualificationEligibilityService.recalculateApplicationPoints(applicationId))
        .thenReturn(
            new QualificationPointsCalculator.EligibilitySnapshot(
                new BigDecimal("9.00"), List.of(), List.of()));

    ApplicationSummary summary =
        admissionsApplicationService.submitApplication(applicationId, applicantUserId);

    assertEquals("SUBMITTED", summary.status());
    assertEquals(new BigDecimal("9.00"), summary.calculatedTotalPoints());
    verify(admissionsDocumentService).assertReadyForSubmission(application);
    verify(statusEventRepository).save(any(ApplicationStatusEvent.class));
    verify(integrationOutboxService).enqueueApplicationSubmittedNotification(application);
  }

  @Test
  void applyFinancePaymentReferenceUpdate_shouldCreateLocalProjectionAndClearWorkflow() {
    UUID applicationId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    ReflectionTestUtils.setField(application, "id", applicationId);
    Instant paidAt = currentInstant.minusSeconds(60);
    ApplicationPaymentReferenceUpdatedEvent financePaymentReference =
        financePaymentReference(applicationId, 1, "PAID", "RATED", true, paidAt);
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationPaymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(Optional.empty());
    when(applicationPaymentReferenceRepository.save(any(ApplicationPaymentReference.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    admissionsApplicationService.applyFinancePaymentReferenceUpdate(financePaymentReference);

    assertTrue(application.canEnterReview());
    verify(applicationPaymentReferenceRepository).save(any(ApplicationPaymentReference.class));
    verify(integrationOutboxService)
        .enqueuePaymentConfirmedNotification(application, financePaymentReference.reference());
  }

  @Test
  void applyFinancePaymentReferenceUpdate_shouldIgnoreOlderFinanceState() {
    UUID applicationId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    ReflectionTestUtils.setField(application, "id", applicationId);
    ApplicationPaymentReference paymentReference =
        new ApplicationPaymentReference(
            application,
            financePaymentReference(applicationId, 2, "PAID", "RATED", true, currentInstant));
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(applicationPaymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(Optional.of(paymentReference));

    admissionsApplicationService.applyFinancePaymentReferenceUpdate(
        financePaymentReference(applicationId, 1, "PENDING", "RATED", false, null));

    assertEquals(PaymentReferenceStatus.PAID, paymentReference.getStatus());
  }

  @Test
  void moveToReview_shouldRejectDraftApplication() {
    UUID applicationId = UUID.randomUUID();
    UUID actorUserId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                admissionsApplicationService.moveToReview(
                    applicationId, actorUserId, "Verify documents"));

    assertEquals("Only a submitted application can enter review.", exception.getMessage());
  }

  @Test
  void moveToReview_shouldAllowFeeRequiredApplication_whenPaymentIsConfirmed() {
    UUID applicationId = UUID.randomUUID();
    UUID actorUserId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    application.confirmPayment(Instant.now());
    application.submit("Submitted by applicant");
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    ApplicantQualificationSitting verifiedSitting =
        org.mockito.Mockito.mock(ApplicantQualificationSitting.class);
    when(verifiedSitting.getVerificationStatus()).thenReturn(QualificationResultStatus.VERIFIED);
    when(qualificationSittingRepository
            .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(applicationId))
        .thenReturn(List.of(verifiedSitting));
    when(duplicateCheckService.check(application))
        .thenReturn(
            new ApplicationDuplicateCheckService.DuplicateCheckResult(
                true, "Applicant identity, intake application and programme choices are unique."));

    ApplicationSummary summary =
        admissionsApplicationService.moveToReview(applicationId, actorUserId, "Documents verified");

    assertEquals("UNDER_REVIEW", summary.status());
    verify(admissionsDocumentService).assertReadyForReview(application);
    verify(statusEventRepository).save(any(ApplicationStatusEvent.class));
    verify(integrationOutboxService).enqueueVerificationDecisionNotification(application);
  }

  @Test
  void returnToDraft_shouldReopenSubmittedApplicationForApplicantCorrection() {
    UUID applicationId = UUID.randomUUID();
    UUID actorUserId = UUID.randomUUID();
    Application application = feeRequiredDraftApplication();
    ReflectionTestUtils.setField(application, "id", applicationId);
    application.confirmPayment(currentInstant);
    application.submit("Submitted by applicant");
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(evaluationRepository.existsByApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(false);
    when(applicationPaymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(Optional.empty());

    ApplicationSummary summary =
        admissionsApplicationService.returnToDraft(
            applicationId, actorUserId, "Please correct the captured qualification grades.");

    assertEquals("DRAFT", summary.status());
    verify(applicantApplicationWorkspaceService)
        .reopenQualificationsForApplicantCorrection(applicationId);
    verify(statusEventRepository).save(any(ApplicationStatusEvent.class));
    verify(integrationOutboxService).enqueueVerificationDecisionNotification(application);
  }

  private Application feeRequiredDraftApplication() {
    AdmissionCycle admissionCycle =
        new AdmissionCycle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "2027-AUG",
            "2027 August Intake",
            currentInstant.minusSeconds(3600),
            currentInstant.plusSeconds(86400));
    admissionCycle.open(currentInstant);
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
    Applicant applicant =
        new Applicant(
            UUID.randomUUID(), "APP-0001", "LOCAL", "Nyasha", "Moyo", "nyasha@example.test");
    Application application =
        new Application(admissionCycle, applicant, applicationType, "EMH-2027-0001", true);
    return application;
  }

  private ApplicationPaymentReferenceUpdatedEvent financePaymentReference(
      UUID applicationId,
      long stateSequence,
      String status,
      String ratingStatus,
      boolean workflowCleared,
      Instant paidAt) {
    return new ApplicationPaymentReferenceUpdatedEvent(
        UUID.randomUUID(),
        ApplicationPaymentReferenceUpdatedEvent.CURRENT_SCHEMA_VERSION,
        currentInstant,
        stateSequence,
        UUID.randomUUID(),
        applicationId,
        "EMH-PAY-0000000001",
        new BigDecimal("25.00"),
        "USD",
        "USD",
        null,
        new BigDecimal("25.00"),
        ratingStatus,
        status,
        true,
        workflowCleared,
        currentInstant.plusSeconds(86400),
        paidAt);
  }
}

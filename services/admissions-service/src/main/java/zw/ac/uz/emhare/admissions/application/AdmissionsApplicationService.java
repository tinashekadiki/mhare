package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.command.*;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantCategoryCode;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearance;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearanceOutcome;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationFeePolicySnapshot;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeOptionSnapshot;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationSection;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeSelectionSnapshot;
import zw.ac.uz.emhare.admissions.domain.model.QualificationResultStatus;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationSittingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationClearanceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationEvaluationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationPaymentReferenceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeOptionSnapshotRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeProgrammeMappingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveApplicationFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedApplicationFeeStructure;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;

@Service
public class AdmissionsApplicationService {

  private final ApplicantRepository applicantRepository;
  private final AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
  private final ApplicationTypeRepository applicationTypeRepository;
  private final ApplicationTypeProgrammeMappingRepository programmeMappingRepository;
  private final ApplicationProgrammeOptionSnapshotRepository programmeOptionSnapshotRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
  private final ApplicationPaymentReferenceRepository applicationPaymentReferenceRepository;
  private final ApplicationStatusEventRepository statusEventRepository;
  private final ApplicationEvaluationRepository evaluationRepository;
  private final ApplicationClearanceRepository clearanceRepository;
  private final ApplicationSectionRepository sectionRepository;
  private final ApplicantQualificationSittingRepository qualificationSittingRepository;
  private final AdmissionsIdentifierGenerator admissionsIdentifierGenerator;
  private final AdmissionsIntegrationOutboxService integrationOutboxService;
  private final FinanceCatalogueClient financeCatalogueClient;
  private final AdmissionsDocumentService admissionsDocumentService;
  private final ApplicantApplicationWorkspaceService applicantApplicationWorkspaceService;
  private final QualificationEligibilityService qualificationEligibilityService;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final ApplicationDuplicateCheckService duplicateCheckService;

  public AdmissionsApplicationService(
      ApplicantRepository applicantRepository,
      AdmissionsIntakeProjectionService admissionsIntakeProjectionService,
      ApplicationTypeRepository applicationTypeRepository,
      ApplicationTypeProgrammeMappingRepository programmeMappingRepository,
      ApplicationProgrammeOptionSnapshotRepository programmeOptionSnapshotRepository,
      ApplicationRepository applicationRepository,
      ApplicationProgrammeChoiceRepository programmeChoiceRepository,
      ApplicationPaymentReferenceRepository applicationPaymentReferenceRepository,
      ApplicationStatusEventRepository statusEventRepository,
      ApplicationEvaluationRepository evaluationRepository,
      ApplicationClearanceRepository clearanceRepository,
      ApplicationSectionRepository sectionRepository,
      ApplicantQualificationSittingRepository qualificationSittingRepository,
      AdmissionsIdentifierGenerator admissionsIdentifierGenerator,
      AdmissionsIntegrationOutboxService integrationOutboxService,
      FinanceCatalogueClient financeCatalogueClient,
      AdmissionsDocumentService admissionsDocumentService,
      ApplicantApplicationWorkspaceService applicantApplicationWorkspaceService,
      QualificationEligibilityService qualificationEligibilityService,
      Clock clock,
      ObjectMapper objectMapper,
      ApplicationDuplicateCheckService duplicateCheckService) {
    this.applicantRepository = applicantRepository;
    this.admissionsIntakeProjectionService = admissionsIntakeProjectionService;
    this.applicationTypeRepository = applicationTypeRepository;
    this.programmeMappingRepository = programmeMappingRepository;
    this.programmeOptionSnapshotRepository = programmeOptionSnapshotRepository;
    this.applicationRepository = applicationRepository;
    this.programmeChoiceRepository = programmeChoiceRepository;
    this.applicationPaymentReferenceRepository = applicationPaymentReferenceRepository;
    this.statusEventRepository = statusEventRepository;
    this.evaluationRepository = evaluationRepository;
    this.clearanceRepository = clearanceRepository;
    this.sectionRepository = sectionRepository;
    this.qualificationSittingRepository = qualificationSittingRepository;
    this.admissionsIdentifierGenerator = admissionsIdentifierGenerator;
    this.integrationOutboxService = integrationOutboxService;
    this.financeCatalogueClient = financeCatalogueClient;
    this.admissionsDocumentService = admissionsDocumentService;
    this.applicantApplicationWorkspaceService = applicantApplicationWorkspaceService;
    this.qualificationEligibilityService = qualificationEligibilityService;
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.duplicateCheckService = duplicateCheckService;
  }

  @Transactional
  public ApplicationSummary startApplication(
      CreateApplicationCommand command, String authorization) {
    String applicantCategoryCode =
        ApplicantCategoryCode.from(command.applicantCategoryCode()).name();
    AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake resolvedIntake =
        admissionsIntakeProjectionService.requireOpenIntake(command.intakeId());
    var intake = resolvedIntake.intake();
    ApplicationType applicationType =
        applicationTypeRepository
            .findById(command.applicationTypeId())
            .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
    if (!applicationType.isActive()) {
      throw new IllegalStateException("Application type is not active.");
    }
    List<AcademicProgrammeOption> eligibleProgrammes =
        eligibleProgrammes(resolvedIntake, command.applicationTypeId());
    List<ProgrammeSelectionSnapshot> programmeSelections =
        validateProgrammeSelections(resolvedIntake, eligibleProgrammes, command.programmeIds());

    ApplicationFeePolicySnapshot feePolicySnapshot =
        resolveApplicationFeePolicy(
            applicationType,
            applicantCategoryCode,
            eligibleProgrammes,
            authorization,
            clock.instant());

    Applicant applicant =
        applicantRepository
            .findByUserId(command.applicantUserId())
            .orElseGet(
                () ->
                    applicantRepository.save(
                        new Applicant(
                            command.applicantUserId(),
                            admissionsIdentifierGenerator.nextApplicantNumber(),
                            applicantCategoryCode,
                            command.firstName(),
                            command.lastName(),
                            command.primaryEmail())));
    applicant.synchronizeRegisteredName(command.firstName(), command.lastName());
    applicantRepository.saveAndFlush(applicant);
    if (command.nationalIdNumber() != null && !command.nationalIdNumber().isBlank()) {
      applicant.recordNationalIdNumber(command.nationalIdNumber());
    }
    if (applicant.getNationalIdNumber() != null
        && !applicationRepository
            .findByIntakeIdAndApplicantNationalIdNumber(
                intake.intakeId(), applicant.getNationalIdNumber())
            .isEmpty()) {
      throw new IllegalStateException(
          "An application already exists for this national ID number in this intake.");
    }

    Application application =
        new Application(
            intake.intakeId(),
            intake.code(),
            intake.name(),
            intake.startsOn(),
            intake.endsOn(),
            intake.maximumProgrammeChoices(),
            applicant,
            applicationType,
            admissionsIdentifierGenerator.nextApplicationNumber(intake.code()),
            feePolicySnapshot);
    Application savedApplication = applicationRepository.saveAndFlush(application);
    programmeOptionSnapshotRepository.saveAllAndFlush(
        eligibleProgrammes.stream()
            .map(
                option ->
                    new ApplicationProgrammeOptionSnapshot(
                        savedApplication, option, serializeEntryOptions(option)))
            .toList());
    admissionsDocumentService.snapshotRequirements(savedApplication);
    List<ApplicationProgrammeChoice> programmeChoices = new java.util.ArrayList<>();
    for (int index = 0; index < programmeSelections.size(); index++) {
      programmeChoices.add(
          new ApplicationProgrammeChoice(
              savedApplication, programmeSelections.get(index), index + 1));
    }
    if (!programmeChoices.isEmpty()) {
      programmeChoices = programmeChoiceRepository.saveAllAndFlush(programmeChoices);
    }
    if (feePolicySnapshot.requiresPayment()) {
      integrationOutboxService.enqueueApplicationFeeRequired(
          savedApplication.getId(),
          applicant.getUserId(),
          command.applicantKeycloakUserId(),
          feePolicySnapshot.getAmount(),
          feePolicySnapshot.getCurrencyCode());
    }
    statusEventRepository.save(
        new ApplicationStatusEvent(
            savedApplication,
            null,
            savedApplication.getStatus(),
            "Application draft created by applicant",
            command.applicantUserId()));

    applicantApplicationWorkspaceService.initializeSections(savedApplication);

    return ApplicationSummary.from(savedApplication, null, programmeChoices);
  }

  @Transactional
  public ApplicationSummary submitApplication(UUID applicationId, UUID applicantUserId) {
    Application application = findApplicantOwnedApplication(applicationId, applicantUserId);
    admissionsIntakeProjectionService.requireOpenIntake(application.getIntakeId());
    ApplicationPaymentReference paymentReference = findPaymentReference(application.getId());
    applicantApplicationWorkspaceService.assertReadyForSubmission(application);
    admissionsDocumentService.assertReadyForSubmission(application);
    ApplicationStatus fromStatus = application.getStatus();
    application.submit("Submitted by applicant");
    QualificationPointsCalculator.EligibilitySnapshot pointsSnapshot =
        qualificationEligibilityService.recalculateApplicationPoints(application.getId());
    application.recordCalculatedPoints(pointsSnapshot.totalPoints(), clock.instant());
    statusEventRepository.save(
        new ApplicationStatusEvent(
            application,
            fromStatus,
            application.getStatus(),
            "Application submitted by applicant",
            applicantUserId));
    integrationOutboxService.enqueueApplicationSubmittedNotification(application);
    return summary(application, paymentReference);
  }

  @Transactional
  public List<ApplicationSummary> listApplicationsForApplicant(UUID applicantUserId) {
    List<Application> applications =
        applicationRepository.findByApplicantUserId(applicantUserId).stream()
            .filter(application -> !application.isDeleted())
            .toList();
    return applications.stream()
        .map(application -> summary(application, findPaymentReference(application.getId())))
        .toList();
  }

  @Transactional
  public List<ApplicationSummary> listApplicationsForApplicantRecord(UUID applicantId) {
    return applicationRepository
        .findAllByApplicantIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicantId)
        .stream()
        .map(application -> summary(application, findPaymentReference(application.getId())))
        .toList();
  }

  @Transactional
  public List<ApplicationSummary> listApplications() {
    List<Application> applications =
        applicationRepository.findAll().stream()
            .filter(application -> !application.isDeleted())
            .toList();
    return applications.stream()
        .map(application -> summary(application, findPaymentReference(application.getId())))
        .toList();
  }

  @Transactional
  public ApplicationSummary overridePayment(UUID applicationId, UUID actorUserId, String reason) {
    Application application =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    ApplicationStatus fromStatus = application.getStatus();
    application.overridePayment(actorUserId, reason);
    statusEventRepository.save(
        new ApplicationStatusEvent(
            application, fromStatus, application.getStatus(), reason, actorUserId));
    return summary(application, findPaymentReference(application.getId()));
  }

  @Transactional
  public ApplicationSummary moveToReview(UUID applicationId, UUID actorUserId, String reason) {
    Application application =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    if (application.getStatus() != ApplicationStatus.SUBMITTED) {
      throw new IllegalStateException("Only a submitted application can enter review.");
    }
    if (!application.canEnterReview()) {
      throw new IllegalStateException("Application fee must be confirmed or waived before review.");
    }
    admissionsDocumentService.assertReadyForReview(application);
    List<ApplicationSection> requiredSections =
        sectionRepository
            .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(applicationId)
            .stream()
            .filter(ApplicationSection::isRequired)
            .toList();
    if (requiredSections.stream().anyMatch(section -> !section.isComplete())) {
      throw new IllegalStateException(
          "All required application sections must be complete before Admissions confirmation.");
    }
    List<ApplicantQualificationSitting> qualificationSittings =
        qualificationSittingRepository
            .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(applicationId);
    if (qualificationSittings.isEmpty()
        || qualificationSittings.stream()
            .anyMatch(
                sitting -> sitting.getVerificationStatus() != QualificationResultStatus.VERIFIED)) {
      throw new IllegalStateException(
          "All qualification sittings must be verified before Admissions confirmation.");
    }
    if (clearanceRepository
        .findByApplicationIdAndOutcomeAndDeletedAtIsNull(
            applicationId, ApplicationClearanceOutcome.CONFIRMED)
        .isPresent()) {
      throw new IllegalStateException("Application has already been confirmed by Admissions.");
    }
    ApplicationDuplicateCheckService.DuplicateCheckResult duplicateCheck =
        duplicateCheckService.check(application);
    if (!duplicateCheck.passed()) {
      throw new IllegalStateException(
          "Application duplicate checks must pass before Admissions confirmation: "
              + duplicateCheck.summary());
    }
    ApplicationStatus fromStatus = application.getStatus();
    application.moveToUnderReview(actorUserId, reason);
    clearanceRepository.save(
        new ApplicationClearance(
            application, actorUserId, reason, duplicateCheck.summary(), clock.instant()));
    statusEventRepository.save(
        new ApplicationStatusEvent(
            application, fromStatus, application.getStatus(), reason, actorUserId));
    integrationOutboxService.enqueueVerificationDecisionNotification(application);
    return summary(application, findPaymentReference(application.getId()));
  }

  @Transactional
  public ApplicationSummary returnToDraft(UUID applicationId, UUID actorUserId, String reason) {
    Application application =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    if (evaluationRepository.existsByApplicationIdAndDeletedAtIsNull(applicationId)) {
      throw new IllegalStateException(
          "An application with recorded programme evaluations cannot return to draft.");
    }
    ApplicationStatus fromStatus = application.getStatus();
    clearanceRepository
        .findByApplicationIdAndOutcomeAndDeletedAtIsNull(
            applicationId, ApplicationClearanceOutcome.CONFIRMED)
        .ifPresent(clearance -> clearance.invalidate(actorUserId, reason, clock.instant()));
    application.returnToDraft(reason);
    applicantApplicationWorkspaceService.reopenQualificationsForApplicantCorrection(applicationId);
    statusEventRepository.save(
        new ApplicationStatusEvent(
            application, fromStatus, application.getStatus(), reason, actorUserId));
    integrationOutboxService.enqueueVerificationDecisionNotification(application);
    return summary(application, findPaymentReference(application.getId()));
  }

  private Application findApplicantOwnedApplication(UUID applicationId, UUID applicantUserId) {
    Application application =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    if (!application.getApplicant().getUserId().equals(applicantUserId)) {
      throw new IllegalArgumentException("Application not found.");
    }
    return application;
  }

  @Transactional
  public void applyFinancePaymentReferenceUpdate(
      ApplicationPaymentReferenceUpdatedEvent financePaymentReference) {
    Application application =
        applicationRepository
            .findById(financePaymentReference.applicationId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Application for Finance payment reference was not found."));
    if (!application.isPaymentRequired()) {
      throw new IllegalStateException(
          "Finance supplied a payment reference for a fee-free application.");
    }
    ApplicationPaymentReference paymentReference =
        applicationPaymentReferenceRepository
            .findByApplicationIdAndDeletedAtIsNull(application.getId())
            .orElseGet(() -> new ApplicationPaymentReference(application, financePaymentReference));
    boolean applied = paymentReference.synchronize(financePaymentReference);
    if (!applied) {
      return;
    }
    applicationPaymentReferenceRepository.save(paymentReference);
    if (financePaymentReference.workflowCleared()
        && application.confirmPayment(financePaymentReference.paidAt())) {
      integrationOutboxService.enqueuePaymentConfirmedNotification(
          application, financePaymentReference.reference());
    }
  }

  private ApplicationPaymentReference findPaymentReference(UUID applicationId) {
    return applicationPaymentReferenceRepository
        .findByApplicationIdAndDeletedAtIsNull(applicationId)
        .orElse(null);
  }

  private ApplicationFeePolicySnapshot resolveApplicationFeePolicy(
      ApplicationType applicationType,
      String applicantCategoryCode,
      List<AcademicProgrammeOption> eligibleProgrammes,
      String authorization,
      Instant effectiveAt) {
    if ("FEE_FREE".equals(applicationType.getFeePolicyStatus())) {
      return ApplicationFeePolicySnapshot.feeFree(
          applicationType.getFeeFreeReason(),
          applicationType.getFeePolicyDecidedByUserId(),
          applicationType.getFeePolicyDecidedAt());
    }
    if (!"FEE_STRUCTURE".equals(applicationType.getFeePolicyStatus())) {
      throw new IllegalStateException("This application route has no governed Finance fee policy.");
    }
    if (authorization == null || authorization.isBlank()) {
      throw new IllegalStateException(
          "JWT authentication is required for governed application-fee pricing.");
    }
    ProgrammeLevelIdentity programmeLevel = requireSingleProgrammeLevel(eligibleProgrammes);
    ResolvedApplicationFeeStructure resolvedFee =
        financeCatalogueClient.resolveApplicationFeeStructure(
            authorization,
            new ResolveApplicationFeeStructureRequest(
                "APPLICATION",
                effectiveAt,
                programmeLevel.id(),
                programmeLevel.code(),
                applicantCategoryCode));
    return ApplicationFeePolicySnapshot.financeStructure(
        resolvedFee.id(),
        resolvedFee.code(),
        resolvedFee.name(),
        resolvedFee.version(),
        programmeLevel.id(),
        programmeLevel.code(),
        applicantCategoryCode,
        resolvedFee.totalTransactionAmount(),
        resolvedFee.transactionCurrencyCode(),
        effectiveAt);
  }

  private ProgrammeLevelIdentity requireSingleProgrammeLevel(
      List<AcademicProgrammeOption> eligibleProgrammes) {
    List<ProgrammeLevelIdentity> programmeLevels =
        eligibleProgrammes.stream()
            .map(
                programme ->
                    new ProgrammeLevelIdentity(
                        programme.programmeLevelId(), programme.programmeLevelCode()))
            .distinct()
            .toList();
    if (programmeLevels.size() != 1
        || programmeLevels.getFirst().id() == null
        || programmeLevels.getFirst().code() == null
        || programmeLevels.getFirst().code().isBlank()) {
      throw new IllegalStateException(
          "This application route must resolve to exactly one governed programme level before pricing.");
    }
    return programmeLevels.getFirst();
  }

  private record ProgrammeLevelIdentity(UUID id, String code) {}

  private List<ProgrammeSelectionSnapshot> validateProgrammeSelections(
      AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake resolvedIntake,
      List<AcademicProgrammeOption> eligibleProgrammes,
      List<UUID> programmeIds) {
    if (programmeIds == null || programmeIds.isEmpty()) return List.of();
    if (programmeIds.size() > resolvedIntake.intake().maximumProgrammeChoices()) {
      throw new IllegalArgumentException(
          "This intake allows a maximum of "
              + resolvedIntake.intake().maximumProgrammeChoices()
              + " programme choices.");
    }
    if (new LinkedHashSet<>(programmeIds).size() != programmeIds.size()) {
      throw new IllegalArgumentException("The same programme cannot be selected more than once.");
    }
    Map<UUID, AcademicProgrammeOption> availableProgrammes =
        eligibleProgrammes.stream()
            .collect(Collectors.toMap(AcademicProgrammeOption::programmeId, Function.identity()));
    return programmeIds.stream()
        .map(
            programmeId -> {
              AcademicProgrammeOption option = availableProgrammes.get(programmeId);
              if (option == null) {
                throw new IllegalArgumentException(
                    "Selected programme is not available for this application route and intake: "
                        + programmeId);
              }
              return programmeSelectionSnapshot(option);
            })
        .toList();
  }

  private List<AcademicProgrammeOption> eligibleProgrammes(
      AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake resolvedIntake,
      UUID applicationTypeId) {
    java.util.Set<UUID> mappedProgrammeIds =
        programmeMappingRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
                applicationTypeId)
            .stream()
            .map(mapping -> mapping.getProgrammeId())
            .collect(java.util.stream.Collectors.toSet());
    List<AcademicProgrammeOption> eligibleProgrammes =
        resolvedIntake.intake().programmes().stream()
            .filter(programme -> mappedProgrammeIds.contains(programme.programmeId()))
            .toList();
    if (eligibleProgrammes.isEmpty()) {
      throw new IllegalStateException(
          "This application route has no configured programmes available in the selected intake.");
    }
    return eligibleProgrammes;
  }

  private String serializeEntryOptions(AcademicProgrammeOption option) {
    try {
      return objectMapper.writeValueAsString(option.entryOptions());
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Programme entry options could not be snapshotted.", exception);
    }
  }

  private ProgrammeSelectionSnapshot programmeSelectionSnapshot(AcademicProgrammeOption option) {
    return new ProgrammeSelectionSnapshot(
        option.programmeId(),
        option.programmeVersionId(),
        option.programmeCode(),
        option.programmeName(),
        option.awardName(),
        option.owningAcademicUnitId(),
        option.owningAcademicUnitName(),
        option.programmeVersionCode());
  }

  private ApplicationSummary summary(
      Application application, ApplicationPaymentReference paymentReference) {
    return ApplicationSummary.from(
        application,
        paymentReference,
        programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()),
        clearanceRepository
            .findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                application.getId(), ApplicationClearanceOutcome.CONFIRMED)
            .orElse(null));
  }
}

package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;

@Service
public class AdmissionsApplicationService {

    private final ApplicantRepository applicantRepository;
    private final AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
    private final ApplicationTypeRepository applicationTypeRepository;
    private final ApplicationFeeRepository applicationFeeRepository;
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

    public AdmissionsApplicationService(
            ApplicantRepository applicantRepository,
            AdmissionsIntakeProjectionService admissionsIntakeProjectionService,
            ApplicationTypeRepository applicationTypeRepository,
            ApplicationFeeRepository applicationFeeRepository,
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
            Clock clock) {
        this.applicantRepository = applicantRepository;
        this.admissionsIntakeProjectionService = admissionsIntakeProjectionService;
        this.applicationTypeRepository = applicationTypeRepository;
        this.applicationFeeRepository = applicationFeeRepository;
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
    }

    @Transactional
    public ApplicationSummary startApplication(CreateApplicationCommand command) {
        String applicantCategoryCode = ApplicantCategoryCode.from(command.applicantCategoryCode()).name();
        AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake resolvedIntake =
                admissionsIntakeProjectionService.requireOpenIntake(command.intakeId());
        AdmissionCycle intakeProjection = resolvedIntake.projection();
        ApplicationType applicationType = applicationTypeRepository.findById(command.applicationTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
        if (!applicationType.isActive()) {
            throw new IllegalStateException("Application type is not active.");
        }
        List<ProgrammeSelectionSnapshot> programmeSelections = validateProgrammeSelections(resolvedIntake, command.programmeIds());

        ResolvedApplicationFee resolvedFee = resolveApplicationFee(
                command.applicationTypeId(), applicationType, applicantCategoryCode, LocalDate.now(clock));
        boolean paymentRequired = resolvedFee.required();

        Applicant applicant = applicantRepository.findByUserId(command.applicantUserId())
                .orElseGet(() -> applicantRepository.save(new Applicant(
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
                && !applicationRepository.findByAdmissionCycleIdAndApplicantNationalIdNumber(
                        intakeProjection.getId(), applicant.getNationalIdNumber()).isEmpty()) {
            throw new IllegalStateException(
                    "An application already exists for this national ID number in this intake.");
        }

        Application application = new Application(
                intakeProjection,
                applicant,
                applicationType,
                admissionsIdentifierGenerator.nextApplicationNumber(intakeProjection),
                paymentRequired);
        Application savedApplication = applicationRepository.saveAndFlush(application);
        List<ApplicationProgrammeChoice> programmeChoices = new java.util.ArrayList<>();
        for (int index = 0; index < programmeSelections.size(); index++) {
            programmeChoices.add(new ApplicationProgrammeChoice(savedApplication, programmeSelections.get(index), index + 1));
        }
        if (!programmeChoices.isEmpty()) {
            programmeChoices = programmeChoiceRepository.saveAllAndFlush(programmeChoices);
        }
        if (paymentRequired) {
            integrationOutboxService.enqueueApplicationFeeRequired(
                    savedApplication.getId(),
                    applicant.getUserId(),
                    command.applicantKeycloakUserId(),
                    resolvedFee.amount(),
                    resolvedFee.currencyCode());
        }
        statusEventRepository.save(new ApplicationStatusEvent(
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
        admissionsIntakeProjectionService.requireOpenIntake(application.getAdmissionCycle().getIntakeId());
        ApplicationPaymentReference paymentReference = findPaymentReference(application.getId());
        applicantApplicationWorkspaceService.assertReadyForSubmission(application);
        admissionsDocumentService.assertReadyForSubmission(application);
        ApplicationStatus fromStatus = application.getStatus();
        application.submit("Submitted by applicant");
        QualificationPointsCalculator.EligibilitySnapshot pointsSnapshot =
                qualificationEligibilityService.recalculateApplicationPoints(application.getId());
        application.recordCalculatedPoints(pointsSnapshot.totalPoints(), clock.instant());
        statusEventRepository.save(new ApplicationStatusEvent(
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
        List<Application> applications = applicationRepository.findByApplicantUserId(applicantUserId).stream()
                .filter(application -> !application.isDeleted())
                .toList();
        return applications.stream()
                .map(application -> summary(application, findPaymentReference(application.getId())))
                .toList();
    }

    @Transactional
    public List<ApplicationSummary> listApplicationsForApplicantRecord(UUID applicantId) {
        return applicationRepository.findAllByApplicantIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicantId).stream()
                .map(application -> summary(application, findPaymentReference(application.getId())))
                .toList();
    }

    @Transactional
    public List<ApplicationSummary> listApplications() {
        List<Application> applications = applicationRepository.findAll().stream()
                .filter(application -> !application.isDeleted())
                .toList();
        return applications.stream()
                .map(application -> summary(application, findPaymentReference(application.getId())))
                .toList();
    }

    @Transactional
    public ApplicationSummary overridePayment(UUID applicationId, UUID actorUserId, String reason) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        ApplicationStatus fromStatus = application.getStatus();
        application.overridePayment(actorUserId, reason);
        statusEventRepository.save(new ApplicationStatusEvent(application, fromStatus, application.getStatus(), reason, actorUserId));
        return summary(application, findPaymentReference(application.getId()));
    }

    @Transactional
    public ApplicationSummary moveToReview(UUID applicationId, UUID actorUserId, String reason) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted application can enter review.");
        }
        if (!application.canEnterReview()) {
            throw new IllegalStateException("Application fee must be confirmed or waived before review.");
        }
        admissionsDocumentService.assertReadyForReview(application);
        List<ApplicationSection> requiredSections = sectionRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(applicationId).stream()
                .filter(ApplicationSection::isRequired)
                .toList();
        if (requiredSections.stream().anyMatch(section -> !section.isComplete())) {
            throw new IllegalStateException("All required application sections must be complete before Admissions confirmation.");
        }
        List<ApplicantQualificationSitting> qualificationSittings = qualificationSittingRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(applicationId);
        if (qualificationSittings.isEmpty()
                || qualificationSittings.stream().anyMatch(sitting ->
                        sitting.getVerificationStatus() != QualificationResultStatus.VERIFIED)) {
            throw new IllegalStateException("All qualification sittings must be verified before Admissions confirmation.");
        }
        if (clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED).isPresent()) {
            throw new IllegalStateException("Application has already been confirmed by Admissions.");
        }
        ApplicationStatus fromStatus = application.getStatus();
        application.moveToUnderReview(actorUserId, reason);
        clearanceRepository.save(new ApplicationClearance(application, actorUserId, reason, clock.instant()));
        statusEventRepository.save(new ApplicationStatusEvent(application, fromStatus, application.getStatus(), reason, actorUserId));
        integrationOutboxService.enqueueVerificationDecisionNotification(application);
        return summary(application, findPaymentReference(application.getId()));
    }

    @Transactional
    public ApplicationSummary returnToDraft(UUID applicationId, UUID actorUserId, String reason) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (evaluationRepository.existsByApplicationIdAndDeletedAtIsNull(applicationId)) {
            throw new IllegalStateException(
                    "An application with recorded programme evaluations cannot return to draft.");
        }
        ApplicationStatus fromStatus = application.getStatus();
        clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                        applicationId, ApplicationClearanceOutcome.CONFIRMED)
                .ifPresent(clearance -> clearance.invalidate(actorUserId, reason, clock.instant()));
        application.returnToDraft(reason);
        applicantApplicationWorkspaceService.reopenQualificationsForApplicantCorrection(applicationId);
        statusEventRepository.save(new ApplicationStatusEvent(
                application, fromStatus, application.getStatus(), reason, actorUserId));
        integrationOutboxService.enqueueVerificationDecisionNotification(application);
        return summary(application, findPaymentReference(application.getId()));
    }

    private Application findApplicantOwnedApplication(UUID applicationId, UUID applicantUserId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (!application.getApplicant().getUserId().equals(applicantUserId)) {
            throw new IllegalArgumentException("Application not found.");
        }
        return application;
    }

    @Transactional
    public void applyFinancePaymentReferenceUpdate(ApplicationPaymentReferenceUpdatedEvent financePaymentReference) {
        Application application = applicationRepository.findById(financePaymentReference.applicationId())
                .orElseThrow(() -> new IllegalArgumentException("Application for Finance payment reference was not found."));
        if (!application.isPaymentRequired()) {
            throw new IllegalStateException("Finance supplied a payment reference for a fee-free application.");
        }
        ApplicationPaymentReference paymentReference = applicationPaymentReferenceRepository
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
                    application,
                    financePaymentReference.reference());
        }
    }

    private ApplicationPaymentReference findPaymentReference(UUID applicationId) {
        return applicationPaymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId).orElse(null);
    }

    private record ResolvedApplicationFee(boolean required, BigDecimal amount, String currencyCode) {
    }

    private ResolvedApplicationFee resolveApplicationFee(
            UUID applicationTypeId, ApplicationType applicationType, String applicantCategoryCode, LocalDate effectiveDate) {
        if (applicationType.getFinanceFeeStructureId() != null) {
            var pricing = financeCatalogueClient.getApplicationFeeStructurePricing(applicationType.getFinanceFeeStructureId());
            boolean required = "ACTIVE".equals(pricing.status()) && pricing.totalTransactionAmount().signum() > 0;
            return new ResolvedApplicationFee(required, pricing.totalTransactionAmount(), pricing.transactionCurrencyCode());
        }
        List<ApplicationFee> effectiveFees = applicationFeeRepository.findEffectiveFees(
                applicationTypeId, applicantCategoryCode, effectiveDate);
        if (effectiveFees.size() > 1) {
            throw new IllegalStateException("Multiple effective application fees are configured for this application route.");
        }
        ApplicationFee effectiveFee = effectiveFees.stream().findFirst().orElse(null);
        boolean required = effectiveFee != null && effectiveFee.getAmount().signum() > 0;
        return new ResolvedApplicationFee(
                required, effectiveFee == null ? null : effectiveFee.getAmount(),
                effectiveFee == null ? null : effectiveFee.getCurrencyCode());
    }

    private List<ProgrammeSelectionSnapshot> validateProgrammeSelections(
            AdmissionsIntakeProjectionService.ResolvedAdmissionsIntake resolvedIntake,
            List<UUID> programmeIds) {
        if (programmeIds == null || programmeIds.isEmpty()) return List.of();
        if (programmeIds.size() > resolvedIntake.intake().maximumProgrammeChoices()) {
            throw new IllegalArgumentException(
                    "This intake allows a maximum of "
                            + resolvedIntake.intake().maximumProgrammeChoices() + " programme choices.");
        }
        if (new LinkedHashSet<>(programmeIds).size() != programmeIds.size()) {
            throw new IllegalArgumentException("The same programme cannot be selected more than once.");
        }
        Map<UUID, AcademicProgrammeOption> availableProgrammes = resolvedIntake.intake().programmes().stream()
                .collect(Collectors.toMap(AcademicProgrammeOption::programmeId, Function.identity()));
        return programmeIds.stream()
                .map(programmeId -> {
                    AcademicProgrammeOption option = availableProgrammes.get(programmeId);
                    if (option == null) {
                        throw new IllegalArgumentException(
                                "Selected programme is not available for this intake: " + programmeId);
                    }
                    return ProgrammeSelectionSnapshot.from(option);
                })
                .toList();
    }

    private ApplicationSummary summary(Application application, ApplicationPaymentReference paymentReference) {
        return ApplicationSummary.from(
                application,
                paymentReference,
                programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()),
                clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                        application.getId(), ApplicationClearanceOutcome.CONFIRMED).orElse(null));
    }
}

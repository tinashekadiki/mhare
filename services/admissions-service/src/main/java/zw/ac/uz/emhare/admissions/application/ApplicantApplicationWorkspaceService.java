package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubject;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantEmploymentHistory;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantNextOfKin;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationResult;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantReferee;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationSection;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirement;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeSection;
import zw.ac.uz.emhare.admissions.domain.model.ExamBody;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeSelectionSnapshot;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionSubjectRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantEmploymentHistoryRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantNextOfKinRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationResultRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRefereeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationPaymentReferenceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeOptionSnapshotRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRefereeNominationRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationRefereeNomination;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationPriorUzDeclaration;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProfessionalAchievement;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationPriorUzDeclarationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProfessionalAchievementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeEntryOptionSelectionRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeEntryOptionSelection;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ExamBodyRepository;

import zw.ac.uz.emhare.admissions.application.command.*;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.ApplicationDocumentRegister;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationDocumentVerificationRow;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.PriorUzDeclarationSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ProfessionalAchievementSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ProgrammeEntryPreferenceSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationSectionSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationSectionVerificationRow;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationWorkspace;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.EmploymentHistorySummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.NextOfKinSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationResultSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationSittingSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationSittingVerificationRow;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ReferenceOption;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.RefereeSummary;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.VerificationQueue;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationSittingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationSectionStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.QualificationLevel;
import zw.ac.uz.emhare.admissions.domain.model.QualificationResultStatus;
import zw.ac.uz.emhare.admissions.domain.model.SubjectLevel;

/** Owns application progress, applicant capture and completeness gates. @author Tinashe K */
@Service
public class ApplicantApplicationWorkspaceService {

    public static final String CURRENT_DECLARATION_VERSION = "2026.1";

    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;
    private final ApplicationPaymentReferenceRepository paymentReferenceRepository;
    private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    private final ApplicationTypeSectionRepository sectionDefinitionRepository;
    private final ApplicationSectionRepository sectionRepository;
    private final ApplicationTypeDocumentRequirementRepository documentRequirementRepository;
    private final ApplicantNextOfKinRepository nextOfKinRepository;
    private final ApplicantEmploymentHistoryRepository employmentRepository;
    private final ApplicantRefereeRepository refereeRepository;
    private final ApplicationRefereeNominationRepository refereeNominationRepository;
    private final ApplicationPriorUzDeclarationRepository priorUzDeclarationRepository;
    private final ApplicationProfessionalAchievementRepository professionalAchievementRepository;
    private final ApplicantRefereeInvitationService refereeInvitationService;
    private final ApplicantQualificationSittingRepository qualificationSittingRepository;
    private final ApplicantQualificationResultRepository qualificationResultRepository;
    private final ExamBodyRepository examBodyRepository;
    private final AdmissionSubjectRepository subjectRepository;
    private final AdmissionsDocumentService documentService;
    private final ApplicationPaymentSubmissionReadinessService paymentSubmissionReadinessService;
    private final QualificationEligibilityService qualificationEligibilityService;
    private final ApplicationProgrammeOptionSnapshotRepository programmeOptionSnapshotRepository;
    private final ApplicationProgrammeEntryOptionSelectionRepository entryOptionSelectionRepository;
    private final AdmissionsApplicationWorkflowProgressService workflowProgressService;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public ApplicantApplicationWorkspaceService(
            ApplicationRepository applicationRepository,
            ApplicantRepository applicantRepository,
            ApplicationPaymentReferenceRepository paymentReferenceRepository,
            ApplicationProgrammeChoiceRepository programmeChoiceRepository,
            ApplicationTypeSectionRepository sectionDefinitionRepository,
            ApplicationSectionRepository sectionRepository,
            ApplicationTypeDocumentRequirementRepository documentRequirementRepository,
            ApplicantNextOfKinRepository nextOfKinRepository,
            ApplicantEmploymentHistoryRepository employmentRepository,
            ApplicantRefereeRepository refereeRepository,
            ApplicationRefereeNominationRepository refereeNominationRepository,
            ApplicationPriorUzDeclarationRepository priorUzDeclarationRepository,
            ApplicationProfessionalAchievementRepository professionalAchievementRepository,
            ApplicantRefereeInvitationService refereeInvitationService,
            ApplicantQualificationSittingRepository qualificationSittingRepository,
            ApplicantQualificationResultRepository qualificationResultRepository,
            ExamBodyRepository examBodyRepository,
            AdmissionSubjectRepository subjectRepository,
            AdmissionsDocumentService documentService,
            ApplicationPaymentSubmissionReadinessService paymentSubmissionReadinessService,
            QualificationEligibilityService qualificationEligibilityService,
            ApplicationProgrammeOptionSnapshotRepository programmeOptionSnapshotRepository,
            ApplicationProgrammeEntryOptionSelectionRepository entryOptionSelectionRepository,
            AdmissionsApplicationWorkflowProgressService workflowProgressService,
            Clock clock,
            tools.jackson.databind.ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
        this.paymentReferenceRepository = paymentReferenceRepository;
        this.programmeChoiceRepository = programmeChoiceRepository;
        this.sectionDefinitionRepository = sectionDefinitionRepository;
        this.sectionRepository = sectionRepository;
        this.documentRequirementRepository = documentRequirementRepository;
        this.nextOfKinRepository = nextOfKinRepository;
        this.employmentRepository = employmentRepository;
        this.refereeRepository = refereeRepository;
        this.refereeNominationRepository = refereeNominationRepository;
        this.priorUzDeclarationRepository = priorUzDeclarationRepository;
        this.professionalAchievementRepository = professionalAchievementRepository;
        this.refereeInvitationService = refereeInvitationService;
        this.qualificationSittingRepository = qualificationSittingRepository;
        this.qualificationResultRepository = qualificationResultRepository;
        this.examBodyRepository = examBodyRepository;
        this.subjectRepository = subjectRepository;
        this.documentService = documentService;
        this.paymentSubmissionReadinessService = paymentSubmissionReadinessService;
        this.qualificationEligibilityService = qualificationEligibilityService;
        this.programmeOptionSnapshotRepository = programmeOptionSnapshotRepository;
        this.entryOptionSelectionRepository = entryOptionSelectionRepository;
        this.workflowProgressService = workflowProgressService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void initializeSections(Application application) {
        if (!sectionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId()).isEmpty()) {
            return;
        }
        List<ApplicationTypeSection> definitions = ensureDefinitions(application.getApplicationType());
        boolean documentsRequired = documentRequirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        application.getApplicationType().getId())
                .stream().anyMatch(ApplicationTypeDocumentRequirement::isRequired);
        List<ApplicationSection> sections = definitions.stream()
                .map(definition -> new ApplicationSection(
                        application,
                        definition,
                        switch (definition.getSectionCode()) {
                            case "DOCUMENTS" -> documentsRequired;
                            case "PAYMENT" -> application.isPaymentRequired();
                            default -> definition.isRequired();
                        }))
                .toList();
        sectionRepository.saveAllAndFlush(sections);
        refreshSectionProgress(application);
    }

    @Transactional
    public ApplicationWorkspace applicantWorkspace(UUID applicationId, UUID applicantUserId) {
        Application application = requireOwnedApplication(applicationId, applicantUserId);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace staffWorkspace(UUID applicationId) {
        return workspace(requireApplication(applicationId), true);
    }

    @Transactional
    public ApplicationWorkspace saveOwnProfile(
            UUID applicationId,
            UUID applicantUserId,
            UpdateApplicantProfileCommand command) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        Applicant applicant = application.getApplicant();
        assertIdentityAvailable(applicant, command.nationalIdNumber(), command.passportNumber());
        applicant.correctProfile(command.toProfileCorrection());
        applicantRepository.saveAndFlush(applicant);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace saveNextOfKin(
            UUID applicationId,
            UUID applicantUserId,
            UUID nextOfKinId,
            String fullName,
            String relationshipCode,
            String phoneNumber,
            String email,
            String address,
            boolean primary,
            long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        Applicant applicant = application.getApplicant();
        if (primary) {
            nextOfKinRepository.findAllByApplicantIdAndDeletedAtIsNullOrderByPrimaryDescFullNameAsc(applicant.getId())
                    .stream().filter(ApplicantNextOfKin::isPrimary)
                    .filter(existing -> nextOfKinId == null || !existing.getId().equals(nextOfKinId))
                    .forEach(existing -> existing.update(existing.getFullName(), existing.getRelationshipCode(),
                            existing.getPhoneNumber(), existing.getEmail(), existing.getAddress(), false));
        }
        ApplicantNextOfKin nextOfKin;
        if (nextOfKinId == null) {
            nextOfKin = new ApplicantNextOfKin(applicant, fullName, relationshipCode, phoneNumber, email, address, primary);
        } else {
            nextOfKin = nextOfKinRepository.findByIdAndApplicantIdAndDeletedAtIsNull(nextOfKinId, applicant.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Next-of-kin record was not found."));
            assertVersion(nextOfKin.getVersion(), expectedVersion, "Next-of-kin record");
            nextOfKin.update(fullName, relationshipCode, phoneNumber, email, address, primary);
        }
        nextOfKinRepository.saveAndFlush(nextOfKin);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace deleteNextOfKin(UUID applicationId, UUID applicantUserId, UUID nextOfKinId, long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantNextOfKin nextOfKin = nextOfKinRepository
                .findByIdAndApplicantIdAndDeletedAtIsNull(nextOfKinId, application.getApplicant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Next-of-kin record was not found."));
        assertVersion(nextOfKin.getVersion(), expectedVersion, "Next-of-kin record");
        nextOfKin.markDeleted(applicantUserId);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace saveEmployment(
            UUID applicationId,
            UUID applicantUserId,
            UUID employmentId,
            String employerName,
            String positionTitle,
            java.time.LocalDate startedOn,
            java.time.LocalDate endedOn,
            boolean current,
            String responsibilities,
            long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantEmploymentHistory employment;
        if (employmentId == null) {
            employment = new ApplicantEmploymentHistory(application.getApplicant(), employerName, positionTitle,
                    startedOn, endedOn, current, responsibilities);
        } else {
            employment = employmentRepository.findByIdAndApplicantIdAndDeletedAtIsNull(
                            employmentId, application.getApplicant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Employment record was not found."));
            assertVersion(employment.getVersion(), expectedVersion, "Employment record");
            employment.update(employerName, positionTitle, startedOn, endedOn, current, responsibilities);
        }
        employmentRepository.saveAndFlush(employment);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace deleteEmployment(UUID applicationId, UUID applicantUserId, UUID employmentId, long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantEmploymentHistory employment = employmentRepository.findByIdAndApplicantIdAndDeletedAtIsNull(
                        employmentId, application.getApplicant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Employment record was not found."));
        assertVersion(employment.getVersion(), expectedVersion, "Employment record");
        employment.markDeleted(applicantUserId);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace saveReferee(
            UUID applicationId,
            UUID applicantUserId,
            UUID refereeId,
            String fullName,
            String title,
            String organisation,
            String positionTitle,
            String email,
            String phoneNumber,
            String expertise,
            String relationshipToApplicant,
            long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantReferee referee;
        String previousEmail = null;
        if (refereeId == null) {
            referee = new ApplicantReferee(application.getApplicant(), fullName, title, organisation,
                    positionTitle, email, phoneNumber);
        } else {
            referee = refereeRepository.findByIdAndApplicantIdAndDeletedAtIsNull(refereeId, application.getApplicant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Referee record was not found."));
            assertVersion(referee.getVersion(), expectedVersion, "Referee record");
            previousEmail = referee.getEmail();
            referee.update(fullName, title, organisation, positionTitle, email, phoneNumber);
        }
        refereeRepository.saveAndFlush(referee);
        assertUniqueNominationContacts(application, referee);
        ApplicationRefereeNomination nomination = refereeNominationRepository
                .findByApplicationIdAndRefereeIdAndCurrentTrueAndDeletedAtIsNull(applicationId, referee.getId())
                .orElseGet(() -> new ApplicationRefereeNomination(
                        application, referee, organisation, positionTitle, expertise, relationshipToApplicant));
        nomination.update(organisation, positionTitle, expertise, relationshipToApplicant);
        refereeNominationRepository.saveAndFlush(nomination);
        if (refereeId == null || !referee.getEmail().equalsIgnoreCase(previousEmail)) {
            refereeInvitationService.issueInvitation(application, referee, nomination);
        }
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace savePriorUzDeclaration(
            UUID applicationId,
            UUID applicantUserId,
            boolean previouslyStudiedAtUz,
            String registrationNumber,
            java.time.LocalDate enrolmentStartedOn,
            java.time.LocalDate enrolmentEndedOn,
            Boolean previouslyAcceptedOffer,
            Boolean previouslyTookUpPlace) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicationPriorUzDeclaration declaration = priorUzDeclarationRepository
                .findByApplicationIdAndDeletedAtIsNull(applicationId)
                .orElseGet(() -> new ApplicationPriorUzDeclaration(
                        application, previouslyStudiedAtUz, registrationNumber, enrolmentStartedOn,
                        enrolmentEndedOn, previouslyAcceptedOffer, previouslyTookUpPlace));
        declaration.update(previouslyStudiedAtUz, registrationNumber, enrolmentStartedOn,
                enrolmentEndedOn, previouslyAcceptedOffer, previouslyTookUpPlace);
        priorUzDeclarationRepository.saveAndFlush(declaration);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace replaceProfessionalAchievements(
            UUID applicationId,
            UUID applicantUserId,
            boolean declaredNone,
            List<ProfessionalAchievementInput> inputs) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        List<ProfessionalAchievementInput> safeInputs = inputs == null ? List.of() : inputs;
        if (declaredNone && !safeInputs.isEmpty()) {
            throw new IllegalArgumentException("Professional achievements cannot be supplied when none are declared.");
        }
        if (!declaredNone && safeInputs.isEmpty()) {
            throw new IllegalArgumentException("Add a professional achievement or explicitly declare none.");
        }
        professionalAchievementRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(applicationId)
                .forEach(achievement -> achievement.markDeleted(applicantUserId));
        if (!safeInputs.isEmpty()) {
            professionalAchievementRepository.saveAllAndFlush(safeInputs.stream()
                    .map(input -> new ApplicationProfessionalAchievement(
                            application,
                            professionalAchievementType(input.type()),
                            input.title(), input.organisation(), input.achievedOn(), input.description()))
                    .toList());
        }
        application.recordProfessionalAchievementsDeclaredNone(declaredNone);
        applicationRepository.save(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace resendRefereeInvitation(
            UUID applicationId, UUID applicantUserId, UUID refereeId, long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantReferee referee = refereeRepository.findByIdAndApplicantIdAndDeletedAtIsNull(
                        refereeId, application.getApplicant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Referee record was not found."));
        assertVersion(referee.getVersion(), expectedVersion, "Referee record");
        ApplicationRefereeNomination nomination = refereeNominationRepository
                .findByApplicationIdAndRefereeIdAndCurrentTrueAndDeletedAtIsNull(applicationId, refereeId)
                .orElseThrow(() -> new IllegalArgumentException("Referee is not nominated for this application."));
        refereeInvitationService.issueInvitation(application, referee, nomination);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace deleteReferee(UUID applicationId, UUID applicantUserId, UUID refereeId, long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantReferee referee = refereeRepository.findByIdAndApplicantIdAndDeletedAtIsNull(
                        refereeId, application.getApplicant().getId())
                .orElseThrow(() -> new IllegalArgumentException("Referee record was not found."));
        assertVersion(referee.getVersion(), expectedVersion, "Referee record");
        refereeInvitationService.revokeInvitations(applicationId, refereeId);
        refereeNominationRepository.findByApplicationIdAndRefereeIdAndCurrentTrueAndDeletedAtIsNull(applicationId, refereeId)
                .ifPresent(nomination -> nomination.withdraw(applicantUserId));
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace saveQualificationSitting(
            UUID applicationId,
            UUID applicantUserId,
            UUID sittingId,
            String levelCode,
            UUID examBodyId,
            String institutionName,
            String centreNumber,
            String candidateNumber,
            Integer yearWritten,
            UUID countryId,
            UUID documentId,
            long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        QualificationLevel level = qualificationLevel(levelCode);
        ExamBody examBody = examBodyId == null ? null : examBodyRepository.findById(examBodyId)
                .filter(value -> value.isActive() && !value.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Exam body was not found."));
        if ((level == QualificationLevel.O_LEVEL || level == QualificationLevel.A_LEVEL) && examBody == null) {
            throw new IllegalArgumentException("Exam body is required for O Level and A Level qualifications.");
        }
        ApplicantQualificationSitting sitting;
        if (sittingId == null) {
            sitting = new ApplicantQualificationSitting(application, level, examBody, institutionName,
                    centreNumber, candidateNumber, yearWritten, countryId, documentId);
        } else {
            sitting = qualificationSittingRepository.findByIdAndApplicationIdAndDeletedAtIsNull(sittingId, applicationId)
                    .orElseThrow(() -> new IllegalArgumentException("Qualification sitting was not found."));
            assertVersion(sitting.getVersion(), expectedVersion, "Qualification sitting");
            if (sitting.getLevel() != level) {
                throw new IllegalArgumentException("Qualification level cannot change after the sitting is created.");
            }
            sitting.update(examBody, institutionName, centreNumber, candidateNumber, yearWritten, countryId, documentId);
        }
        qualificationSittingRepository.saveAndFlush(sitting);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace deleteQualificationSitting(
            UUID applicationId, UUID applicantUserId, UUID sittingId, long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantQualificationSitting sitting = qualificationSittingRepository
                .findByIdAndApplicationIdAndDeletedAtIsNull(sittingId, applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification sitting was not found."));
        assertVersion(sitting.getVersion(), expectedVersion, "Qualification sitting");
        qualificationResultRepository.findAllByQualificationSittingIdAndDeletedAtIsNullOrderBySubjectNameSnapshotAsc(sittingId)
                .forEach(result -> result.markDeleted(applicantUserId));
        sitting.markDeleted(applicantUserId);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace addQualificationResults(
            UUID applicationId,
            UUID applicantUserId,
            UUID sittingId,
            List<CreateQualificationResultCommand> commands) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantQualificationSitting sitting = qualificationSittingRepository
                .findByIdAndApplicationIdAndDeletedAtIsNull(sittingId, applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification sitting was not found."));
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("Add at least one qualification result.");
        }

        List<UUID> requestedSubjectIds = commands.stream()
                .map(CreateQualificationResultCommand::subjectId)
                .toList();
        if (new LinkedHashSet<>(requestedSubjectIds).size() != requestedSubjectIds.size()) {
            throw new IllegalArgumentException("The same subject cannot be added more than once.");
        }
        LinkedHashSet<UUID> existingSubjectIds = new LinkedHashSet<>(
                qualificationResultRepository.findActiveSubjectIdsByQualificationSittingId(sittingId));
        if (requestedSubjectIds.stream().anyMatch(existingSubjectIds::contains)) {
            throw new IllegalArgumentException("One or more selected subjects have already been captured for this sitting.");
        }

        Map<UUID, AdmissionSubject> subjectsById = subjectRepository.findAllById(requestedSubjectIds).stream()
                .filter(subject -> subject.isActive() && !subject.isDeleted())
                .collect(Collectors.toMap(AdmissionSubject::getId, Function.identity()));
        if (subjectsById.size() != requestedSubjectIds.size()) {
            throw new IllegalArgumentException("One or more admission subjects were not found.");
        }

        List<ApplicantQualificationResult> results = commands.stream().map(command -> {
            AdmissionSubject subject = subjectsById.get(command.subjectId());
            validateSubjectLevel(sitting.getLevel(), subject);
            return new ApplicantQualificationResult(
                    sitting,
                    subject,
                    subject.getName(),
                    command.grade().trim().toUpperCase(Locale.ROOT),
                    null,
                    null,
                    command.principalSubject());
        }).toList();
        qualificationResultRepository.saveAllAndFlush(results);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace saveQualificationResult(
            UUID applicationId,
            UUID applicantUserId,
            UUID sittingId,
            UUID resultId,
            UUID subjectId,
            String grade,
            Boolean principalSubject,
            long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        ApplicantQualificationSitting sitting = qualificationSittingRepository
                .findByIdAndApplicationIdAndDeletedAtIsNull(sittingId, applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification sitting was not found."));
        AdmissionSubject subject = subjectRepository.findById(subjectId)
                .filter(value -> value.isActive() && !value.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Admission subject was not found."));
        validateSubjectLevel(sitting.getLevel(), subject);
        ApplicantQualificationResult result;
        if (resultId == null) {
            result = new ApplicantQualificationResult(sitting, subject,
                    subject.getName(), grade.trim().toUpperCase(Locale.ROOT),
                    null, null, principalSubject);
        } else {
            result = qualificationResultRepository.findByIdAndQualificationSittingIdAndDeletedAtIsNull(resultId, sittingId)
                    .orElseThrow(() -> new IllegalArgumentException("Qualification result was not found."));
            assertVersion(result.getVersion(), expectedVersion, "Qualification result");
            if (result.getSubject() == null || !result.getSubject().getId().equals(subjectId)) {
                throw new IllegalArgumentException("Subject cannot change after a result is created.");
            }
            result.update(grade, null, null, principalSubject);
        }
        qualificationResultRepository.saveAndFlush(result);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace deleteQualificationResult(
            UUID applicationId, UUID applicantUserId, UUID sittingId, UUID resultId, long expectedVersion) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        qualificationSittingRepository.findByIdAndApplicationIdAndDeletedAtIsNull(sittingId, applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification sitting was not found."));
        ApplicantQualificationResult result = qualificationResultRepository
                .findByIdAndQualificationSittingIdAndDeletedAtIsNull(resultId, sittingId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification result was not found."));
        assertVersion(result.getVersion(), expectedVersion, "Qualification result");
        result.markDeleted(applicantUserId);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public void reopenQualificationsForApplicantCorrection(UUID applicationId) {
        qualificationSittingRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(applicationId)
                .forEach(sitting -> {
                    sitting.reopenForApplicantCorrection();
                    qualificationResultRepository
                            .findAllByQualificationSittingIdAndDeletedAtIsNullOrderBySubjectNameSnapshotAsc(sitting.getId())
                            .forEach(ApplicantQualificationResult::reopenForApplicantCorrection);
                });
    }

    @Transactional
    public ApplicationWorkspace replaceProgrammeChoices(
            UUID applicationId,
            UUID actorUserId,
            List<UUID> programmeIds,
            boolean staffAmendment,
            String changeReason) {
        return replaceStructuredProgrammeChoices(applicationId, actorUserId,
                programmeIds.stream().map(programmeId -> new ProgrammeChoiceSelection(programmeId, List.of())).toList(),
                staffAmendment, changeReason);
    }

    @Transactional
    public ApplicationWorkspace replaceStructuredProgrammeChoices(
            UUID applicationId,
            UUID actorUserId,
            List<ProgrammeChoiceSelection> requestedChoices,
            boolean staffAmendment,
            String changeReason) {
        Application application = staffAmendment
                ? requireDraft(requireApplication(applicationId))
                : requireOwnedDraft(applicationId, actorUserId);
        if (staffAmendment && (changeReason == null || changeReason.trim().length() < 10)) {
            throw new IllegalArgumentException("A staff programme-choice amendment reason is required.");
        }
        List<ValidatedProgrammeChoice> selections = validateProgrammeSelections(application, requestedChoices);
        List<ApplicationProgrammeChoice> currentChoices = programmeChoiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(applicationId).stream()
                .filter(choice -> !choice.isDeleted()).toList();
        currentChoices.forEach(choice -> choice.markDeleted(actorUserId));
        programmeChoiceRepository.saveAllAndFlush(currentChoices);
        List<ApplicationProgrammeChoice> replacements = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            replacements.add(new ApplicationProgrammeChoice(application, selections.get(index).programme(), index + 1));
        }
        programmeChoiceRepository.saveAllAndFlush(replacements);
        List<ApplicationProgrammeEntryOptionSelection> entrySelections = new ArrayList<>();
        for (int choiceIndex = 0; choiceIndex < replacements.size(); choiceIndex++) {
            ApplicationProgrammeChoice programmeChoice = replacements.get(choiceIndex);
            List<EntryOptionSnapshot> entryOptions = selections.get(choiceIndex).entryOptions();
            for (int entryIndex = 0; entryIndex < entryOptions.size(); entryIndex++) {
                EntryOptionSnapshot entry = entryOptions.get(entryIndex);
                entrySelections.add(new ApplicationProgrammeEntryOptionSelection(
                        programmeChoice, entry.id(), entry.code(), entry.name(), entryIndex + 1));
            }
        }
        if (!entrySelections.isEmpty()) entryOptionSelectionRepository.saveAllAndFlush(entrySelections);
        invalidateDeclaration(application);
        return workspace(application);
    }

    @Transactional
    public ApplicationWorkspace acceptDeclaration(UUID applicationId, UUID applicantUserId, boolean accepted, String version) {
        Application application = requireOwnedDraft(applicationId, applicantUserId);
        if (!accepted) {
            application.invalidateDeclaration();
            return workspace(application);
        }
        refreshSectionProgress(application);
        List<ApplicationSection> blockers = sectionRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(applicationId).stream()
                .filter(ApplicationSection::isRequired)
                .filter(section -> !"REVIEW_DECLARATION".equals(section.getSectionCode()))
                .filter(section -> !section.isComplete())
                .toList();
        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Complete all required application sections before accepting the declaration.");
        }
        if (!CURRENT_DECLARATION_VERSION.equals(version)) {
            throw new IllegalArgumentException("The declaration has changed. Refresh and review the current declaration.");
        }
        application.acceptDeclaration(applicantUserId, version, clock.instant());
        return workspace(application);
    }

    @Transactional
    public void assertReadyForSubmission(Application application) {
        refreshSectionProgress(application);
        if (!application.isSectionsComplete()) {
            List<String> missing = missingRequirements(application);
            throw new IllegalStateException("Application is incomplete: " + String.join("; ", missing));
        }
    }

    @Transactional
    public QualificationSittingSummary recordQualificationDecision(
            UUID applicationId, UUID sittingId, UUID actorUserId, String decision, String reason, long expectedVersion) {
        ApplicantQualificationSitting sitting = qualificationSittingRepository
                .findByIdAndApplicationIdAndDeletedAtIsNull(sittingId, applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification sitting was not found."));
        assertVersion(sitting.getVersion(), expectedVersion, "Qualification sitting");
        List<ApplicantQualificationResult> results = qualificationResultRepository
                .findAllByQualificationSittingIdAndDeletedAtIsNullOrderBySubjectNameSnapshotAsc(sittingId);
        if (results.isEmpty()) throw new IllegalStateException("A qualification sitting without results cannot be verified.");
        if ("VERIFIED".equalsIgnoreCase(decision)) {
            sitting.verify(actorUserId, clock.instant());
            results.forEach(ApplicantQualificationResult::verify);
        } else if ("REJECTED".equalsIgnoreCase(decision)) {
            sitting.reject(actorUserId, reason, clock.instant());
            results.forEach(ApplicantQualificationResult::reject);
        } else {
            throw new IllegalArgumentException("Qualification decision must be VERIFIED or REJECTED.");
        }
        qualificationResultRepository.saveAll(results);
        qualificationSittingRepository.saveAndFlush(sitting);
        refreshSectionProgress(sitting.getApplication());
        return qualificationSummary(sitting);
    }

    @Transactional
    public VerificationQueue verificationQueue() {
        List<Application> applications = applicationRepository.findAll().stream()
                .filter(application -> !application.isDeleted())
                .filter(application -> application.getStatus() == ApplicationStatus.SUBMITTED
                        || application.getStatus() == ApplicationStatus.UNDER_REVIEW)
                .toList();
        List<ApplicationSectionVerificationRow> sectionRows = new ArrayList<>();
        List<QualificationSittingVerificationRow> qualificationRows = new ArrayList<>();
        List<ApplicationDocumentVerificationRow> documentRows = new ArrayList<>();
        for (Application application : applications) {
            refreshSectionProgress(application);
            sectionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId()).stream()
                    .filter(ApplicationSection::isRequired)
                    .forEach(section -> sectionRows.add(new ApplicationSectionVerificationRow(
                            application.getId(), application.getApplicationNumber(), application.getApplicant().getDisplayName(),
                            section.getSectionCode(), section.getSectionName(), section.getStatus().name(),
                            section.getCompletionSummary(), section.getVersion())));
            qualificationSittingRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(application.getId())
                    .stream().filter(sitting -> sitting.getVerificationStatus() != QualificationResultStatus.VERIFIED)
                    .forEach(sitting -> qualificationRows.add(new QualificationSittingVerificationRow(
                            application.getId(), application.getApplicationNumber(), application.getApplicant().getDisplayName(),
                            qualificationSummary(sitting))));
            ApplicationDocumentRegister register = documentService.staffRegister(application.getId());
            if (!register.pendingRequirementCodes().isEmpty() || !register.rejectedRequirementCodes().isEmpty()) {
                documentRows.add(new ApplicationDocumentVerificationRow(
                        application.getId(), application.getApplicationNumber(), application.getApplicant().getDisplayName(), register));
            }
        }
        return new VerificationQueue(sectionRows, qualificationRows, documentRows);
    }

    private ApplicationWorkspace workspace(Application application) {
        return workspace(application, false);
    }

    private ApplicationWorkspace workspace(Application application, boolean includeConfidentialReferenceResponses) {
        initializeSections(application);
        synchronizeQualificationPoints(application);
        List<ApplicationSection> sections = refreshSectionProgress(application);
        Applicant applicant = application.getApplicant();
        Map<UUID, ApplicantRefereeInvitation> latestRefereeInvitations =
                refereeInvitationService.latestInvitations(application.getId());
        ApplicationDocumentRegister documents = documentService.staffRegister(application.getId());
        List<ApplicationProgrammeChoice> choices = activeProgrammeChoices(application.getId());
        ApplicationSummary summary = ApplicationSummary.from(application,
                paymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(application.getId()).orElse(null), choices);
        List<String> missing = missingRequirements(sections);
        return new ApplicationWorkspace(
                summary,
                ApplicantProfileAssembler.profile(applicant),
                sections.stream().map(ApplicationSectionSummary::from).toList(),
                nextOfKinRepository.findAllByApplicantIdAndDeletedAtIsNullOrderByPrimaryDescFullNameAsc(applicant.getId())
                        .stream().map(NextOfKinSummary::from).toList(),
                employmentRepository.findAllByApplicantIdAndDeletedAtIsNullOrderByStartedOnDesc(applicant.getId())
                        .stream().map(EmploymentHistorySummary::from).toList(),
                refereeNominationRepository
                        .findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByCreatedAtAsc(application.getId())
                        .stream().map(nomination -> RefereeSummary.from(
                                nomination,
                                latestRefereeInvitations.get(nomination.getReferee().getId()),
                                includeConfidentialReferenceResponses)).toList(),
                priorUzDeclarationRepository.findByApplicationIdAndDeletedAtIsNull(application.getId())
                        .map(declaration -> new PriorUzDeclarationSummary(
                                declaration.isPreviouslyStudiedAtUz(), declaration.getRegistrationNumber(),
                                declaration.getEnrolmentStartedOn(), declaration.getEnrolmentEndedOn(),
                                declaration.getPreviouslyAcceptedOffer(), declaration.getPreviouslyTookUpPlace(),
                                declaration.getVersion()))
                        .orElse(null),
                application.isProfessionalAchievementsDeclaredNone(),
                professionalAchievementRepository
                        .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(application.getId())
                        .stream().map(achievement -> new ProfessionalAchievementSummary(
                                achievement.getId(), achievement.getType().name(), achievement.getTitle(),
                                achievement.getOrganisation(), achievement.getAchievedOn(), achievement.getDescription(),
                                achievement.getVersion())).toList(),
                choices.stream()
                        .flatMap(choice -> entryOptionSelectionRepository
                                .findAllByProgrammeChoice_IdAndDeletedAtIsNullOrderByPreferenceRankAsc(choice.getId()).stream())
                        .map(selection -> new ProgrammeEntryPreferenceSummary(
                                selection.getProgrammeChoiceId(), selection.getEntryOptionId(),
                                selection.getEntryOptionCode(), selection.getEntryOptionName(),
                                selection.getPreferenceRank()))
                        .toList(),
                qualificationSittingRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(application.getId())
                        .stream().map(this::qualificationSummary).toList(),
                documents,
                application.canSubmit(),
                missing,
                application.getDeclarationAcceptedAt(),
                application.getDeclarationVersion(),
                workflowProgressService.progress(application.getId()));
    }

    private List<ApplicationSection> refreshSectionProgress(Application application) {
        List<ApplicationSection> sections = sectionRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId());
        if (sections.isEmpty()) {
            initializeSections(application);
            sections = sectionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId());
        }
        Map<String, ApplicationSection> byCode = sections.stream()
                .collect(Collectors.toMap(ApplicationSection::getSectionCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Applicant applicant = application.getApplicant();
        Instant now = clock.instant();
        var profile = ApplicantProfileAssembler.profile(applicant);
        updateSection(byCode.get("PERSONAL_DETAILS"), profile.missingRequiredFields().isEmpty(),
                profile.missingRequiredFields().isEmpty() ? "Applicant details complete." : String.join(", ", profile.missingRequiredFields()), now);
        updateCountSection(byCode.get("NEXT_OF_KIN"), nextOfKinRepository.countByApplicantIdAndDeletedAtIsNull(applicant.getId()), now);
        long completeSittings = qualificationSittingRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(application.getId()).stream()
                .filter(sitting -> qualificationResultRepository.countByQualificationSittingIdAndDeletedAtIsNull(sitting.getId()) > 0)
                .count();
        updateCountSection(byCode.get("QUALIFICATIONS"), completeSittings, now);
        updateCountSection(byCode.get("EMPLOYMENT_HISTORY"), employmentRepository.countByApplicantIdAndDeletedAtIsNull(applicant.getId()), now);
        updateSection(byCode.get("PRIOR_UZ_STUDY"),
                priorUzDeclarationRepository.findByApplicationIdAndDeletedAtIsNull(application.getId()).isPresent(),
                priorUzDeclarationRepository.findByApplicationIdAndDeletedAtIsNull(application.getId()).isPresent()
                        ? "Prior UZ study declaration completed." : "Declare whether you previously studied at UZ.", now);
        long achievementCount = professionalAchievementRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(application.getId()).size();
        boolean achievementsComplete = application.isProfessionalAchievementsDeclaredNone() || achievementCount > 0;
        updateSection(byCode.get("PROFESSIONAL_ACHIEVEMENTS"), achievementsComplete,
                achievementsComplete
                        ? application.isProfessionalAchievementsDeclaredNone()
                            ? "Applicant declared no professional achievements."
                            : achievementCount + " professional achievement(s) captured."
                        : "Add professional achievements or explicitly declare none.", now);
        updateCountSection(byCode.get("REFEREES"),
                refereeInvitationService.countCurrentSubmittedReferences(application.getId()), now);
        updateCountSection(byCode.get("PROGRAMME_CHOICES"), activeProgrammeChoices(application.getId()).size(), now);
        ApplicationDocumentRegister documents = documentService.staffRegister(application.getId());
        updateSection(byCode.get("DOCUMENTS"), documents.requiredDocumentsUploaded(),
                documents.requiredDocumentsUploaded() ? "Required documents uploaded." : "Required documents are missing or rejected.", now);
        var paymentReadiness = paymentSubmissionReadinessService.evaluate(application);
        updateSection(byCode.get("PAYMENT"), paymentReadiness.readyForSubmission(),
                paymentReadiness.summary(), now);
        updateSection(byCode.get("REVIEW_DECLARATION"), application.isDeclarationAccepted(),
                application.isDeclarationAccepted() ? "Applicant declaration accepted." : "Review and accept the applicant declaration.", now);
        sectionRepository.saveAll(sections);
        boolean complete = sections.stream().filter(ApplicationSection::isRequired).allMatch(ApplicationSection::isComplete);
        application.recordSectionCompleteness(complete);
        applicationRepository.save(application);
        return sections;
    }

    private void synchronizeQualificationPoints(Application application) {
        var pointsSnapshot = qualificationEligibilityService.recalculateApplicationPoints(application.getId());
        if (application.getCalculatedTotalPoints() == null
                || application.getCalculatedTotalPoints().compareTo(pointsSnapshot.totalPoints()) != 0) {
            application.recordCalculatedPoints(pointsSnapshot.totalPoints(), clock.instant());
            applicationRepository.save(application);
        }
    }

    private void updateCountSection(ApplicationSection section, long count, Instant now) {
        if (section == null) return;
        int minimum = Math.max(1, section.getMinimumRecords());
        updateSection(section, count >= minimum, count + " of " + minimum + " required record(s) captured.", now);
    }

    private void updateSection(ApplicationSection section, boolean complete, String summary, Instant now) {
        if (section == null) return;
        if (!section.isRequired() && !complete) {
            section.recordStatus(ApplicationSectionStatus.COMPLETE, "Not required for this application route.", now);
        } else {
            section.recordStatus(complete ? ApplicationSectionStatus.COMPLETE : ApplicationSectionStatus.IN_PROGRESS, summary, now);
        }
    }

    private List<String> missingRequirements(Application application) {
        return missingRequirements(sectionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId()));
    }

    private List<String> missingRequirements(List<ApplicationSection> sections) {
        return sections.stream().filter(ApplicationSection::isRequired).filter(section -> !section.isComplete())
                .map(section -> section.getSectionName() + ": " +
                        (section.getCompletionSummary() == null ? "Incomplete" : section.getCompletionSummary()))
                .toList();
    }

    private List<ApplicationTypeSection> ensureDefinitions(ApplicationType applicationType) {
        List<ApplicationTypeSection> definitions = sectionDefinitionRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(applicationType.getId());
        if (!definitions.isEmpty()) return definitions;
        List<ApplicationTypeSection> defaults = ApplicationSectionTemplate.defaults(applicationType).stream()
                .map(template -> new ApplicationTypeSection(
                        applicationType,
                        template.code(),
                        template.name(),
                        template.required(),
                        template.repeatable(),
                        template.minimumRecords(),
                        template.sortOrder()))
                .toList();
        return sectionDefinitionRepository.saveAllAndFlush(defaults);
    }

    private QualificationSittingSummary qualificationSummary(ApplicantQualificationSitting sitting) {
        ReferenceOption examBody = sitting.getExamBody() == null ? null : new ReferenceOption(
                sitting.getExamBody().getId(), sitting.getExamBody().getCode(), sitting.getExamBody().getName(), null);
        List<QualificationResultSummary> results = qualificationResultRepository
                .findAllByQualificationSittingIdAndDeletedAtIsNullOrderBySubjectNameSnapshotAsc(sitting.getId()).stream()
                .map(result -> new QualificationResultSummary(
                        result.getId(),
                        result.getSubject() == null ? null : new ReferenceOption(
                                result.getSubject().getId(), result.getSubject().getCode(), result.getSubject().getName(),
                                result.getSubject().isScienceSubject()),
                        result.getSubjectNameSnapshot(), result.getGrade(), result.getMark(), result.getPoints(),
                        result.getPrincipalSubject(), result.getResultStatus().name(), result.getVersion()))
                .toList();
        return new QualificationSittingSummary(
                sitting.getId(), sitting.getLevel().name(), examBody, sitting.getInstitutionName(),
                sitting.getCentreNumber(), sitting.getCandidateNumber(), sitting.getYearWritten(), sitting.getCountryId(),
                sitting.getDocumentId(), sitting.getVerificationStatus().name(), sitting.getVerifiedByUserId(),
                sitting.getVerifiedAt(), sitting.getRejectionReason(), results, sitting.getVersion());
    }

    private List<ApplicationProgrammeChoice> activeProgrammeChoices(UUID applicationId) {
        return programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId).stream()
                .filter(choice -> !choice.isDeleted()).toList();
    }

    private List<ValidatedProgrammeChoice> validateProgrammeSelections(
            Application application, List<ProgrammeChoiceSelection> requestedChoices) {
        if (requestedChoices == null || requestedChoices.isEmpty()) {
            throw new IllegalArgumentException("At least one programme choice is required.");
        }
        if (requestedChoices.size() > application.getAdmissionCycle().getMaximumProgrammeChoices()) {
            throw new IllegalArgumentException("This intake allows a maximum of "
                    + application.getAdmissionCycle().getMaximumProgrammeChoices() + " programme choices.");
        }
        List<UUID> programmeIds = requestedChoices.stream().map(ProgrammeChoiceSelection::programmeId).toList();
        if (new LinkedHashSet<>(programmeIds).size() != programmeIds.size()) {
            throw new IllegalArgumentException("The same programme cannot be selected more than once.");
        }
        Map<UUID, zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeOptionSnapshot> available =
                programmeOptionSnapshotRepository
                        .findAllByApplicationIdAndDeletedAtIsNullOrderByProgrammeCodeAsc(application.getId())
                        .stream().collect(Collectors.toMap(
                                zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeOptionSnapshot::getProgrammeId,
                                Function.identity()));
        return requestedChoices.stream().map(requested -> {
            var option = available.get(requested.programmeId());
            if (option == null) throw new IllegalArgumentException(
                    "Selected programme is outside this application's eligible programme snapshot: " + requested.programmeId());
            List<EntryOptionSnapshot> availableEntryOptions = entryOptions(option.getEntryOptionsJson());
            List<UUID> entryOptionIds = requested.entryOptionIds() == null ? List.of() : requested.entryOptionIds();
            if (new LinkedHashSet<>(entryOptionIds).size() != entryOptionIds.size()) {
                throw new IllegalArgumentException("The same entry option cannot be selected more than once.");
            }
            if (entryOptionIds.size() < option.getMinimumEntryOptionSelections()
                    || entryOptionIds.size() > option.getMaximumEntryOptionSelections()) {
                throw new IllegalArgumentException("Programme " + option.getProgrammeCode() + " requires between "
                        + option.getMinimumEntryOptionSelections() + " and "
                        + option.getMaximumEntryOptionSelections() + " entry-option selections.");
            }
            Map<UUID, EntryOptionSnapshot> byId = availableEntryOptions.stream()
                    .collect(Collectors.toMap(EntryOptionSnapshot::id, Function.identity()));
            List<EntryOptionSnapshot> selectedEntries = entryOptionIds.stream().map(entryOptionId -> {
                EntryOptionSnapshot entry = byId.get(entryOptionId);
                if (entry == null) throw new IllegalArgumentException(
                        "Selected entry option is outside the programme snapshot: " + entryOptionId);
                return entry;
            }).toList();
            return new ValidatedProgrammeChoice(option.toProgrammeSelectionSnapshot(), selectedEntries);
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<EntryOptionSnapshot> entryOptions(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (!(parsed instanceof List<?> values)) return List.of();
            return values.stream().map(value -> {
                Map<String, Object> map = (Map<String, Object>) value;
                return new EntryOptionSnapshot(
                        UUID.fromString(String.valueOf(map.get("id"))),
                        String.valueOf(map.get("code")), String.valueOf(map.get("name")));
            }).toList();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Programme entry-option snapshot is invalid.", exception);
        }
    }

    private void validateSubjectLevel(QualificationLevel level, AdmissionSubject subject) {
        if (subject == null) return;
        SubjectLevel expected = switch (level) {
            case O_LEVEL -> SubjectLevel.O_LEVEL;
            case A_LEVEL -> SubjectLevel.A_LEVEL;
            default -> SubjectLevel.OTHER;
        };
        if (subject.getLevel() != expected) {
            throw new IllegalArgumentException("Selected subject does not belong to the qualification level.");
        }
    }

    private QualificationLevel qualificationLevel(String value) {
        try {
            return QualificationLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unsupported qualification level: " + value, exception);
        }
    }

    private ApplicationProfessionalAchievement.Type professionalAchievementType(String value) {
        try {
            return ApplicationProfessionalAchievement.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unsupported professional achievement type: " + value, exception);
        }
    }

    public record ProfessionalAchievementInput(
            String type, String title, String organisation, java.time.LocalDate achievedOn, String description) { }

    public record ProgrammeChoiceSelection(UUID programmeId, List<UUID> entryOptionIds) { }
    private record EntryOptionSnapshot(UUID id, String code, String name) { }
    private record ValidatedProgrammeChoice(ProgrammeSelectionSnapshot programme, List<EntryOptionSnapshot> entryOptions) { }

    private void assertIdentityAvailable(Applicant applicant, String nationalId, String passport) {
        if (nationalId != null && !nationalId.isBlank()) {
            applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull(nationalId.trim())
                    .filter(existing -> !existing.getId().equals(applicant.getId()))
                    .ifPresent(existing -> { throw new IllegalStateException("This national ID is already linked to another applicant account."); });
        }
        if (passport != null && !passport.isBlank()) {
            applicantRepository.findByPassportNumberIgnoreCaseAndDeletedAtIsNull(passport.trim())
                    .filter(existing -> !existing.getId().equals(applicant.getId()))
                    .ifPresent(existing -> { throw new IllegalStateException("This passport is already linked to another applicant account."); });
        }
    }

    private void assertUniqueNominationContacts(Application application, ApplicantReferee referee) {
        String normalizedEmail = ApplicationRefereeNomination.normalizeEmail(referee.getEmail());
        String normalizedPhone = ApplicationRefereeNomination.normalizePhone(referee.getPhoneNumber());
        boolean duplicate = refereeNominationRepository
                .findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByCreatedAtAsc(application.getId())
                .stream().filter(nomination -> !nomination.getReferee().getId().equals(referee.getId()))
                .anyMatch(nomination -> nomination.getNormalizedEmail().equals(normalizedEmail)
                        || (normalizedPhone != null && normalizedPhone.equals(nomination.getNormalizedPhoneNumber())));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Each nominated referee must have a distinct email address and supplied phone number.");
        }
    }

    private Application requireOwnedApplication(UUID applicationId, UUID applicantUserId) {
        Application application = requireApplication(applicationId);
        if (!application.getApplicant().getUserId().equals(applicantUserId)) {
            throw new IllegalArgumentException("Application not found.");
        }
        return application;
    }

    private Application requireOwnedDraft(UUID applicationId, UUID applicantUserId) {
        return requireDraft(requireOwnedApplication(applicationId, applicantUserId));
    }

    private Application requireDraft(Application application) {
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new IllegalStateException("Submitted applications cannot be edited by the applicant.");
        }
        return application;
    }

    private Application requireApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(application -> !application.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    }

    private void invalidateDeclaration(Application application) {
        application.invalidateDeclaration();
        applicationRepository.save(application);
    }

    private void assertVersion(long actual, long expected, String recordName) {
        if (actual != expected) throw new IllegalStateException(recordName + " was changed. Refresh before retrying.");
    }
}

package zw.ac.uz.emhare.admissions.api.controller;

import zw.ac.uz.emhare.admissions.domain.model.Applicant;

import zw.ac.uz.emhare.admissions.application.command.*;

import zw.ac.uz.emhare.admissions.api.model.*;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceService;
import zw.ac.uz.emhare.admissions.application.AdmissionsRollingWorkflowService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationWorkspace;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationSittingSummary;
import zw.ac.uz.emhare.admissions.application.command.CreateQualificationResultCommand;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.VerificationQueue;
import zw.ac.uz.emhare.admissions.application.command.UpdateApplicantProfileCommand;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.security.ApplicantRegistrationIdentityResolver;
import zw.ac.uz.emhare.admissions.security.ApplicantRegistrationIdentityResolver.ApplicantRegistrationIdentity;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.AcceptDeclarationRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.AddQualificationResultsRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.QualificationDecisionRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.ReplaceProgrammeChoicesRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveEmploymentRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveNextOfKinRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveOwnProfileRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveQualificationResultRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveQualificationSittingRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveRefereeRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SavePriorUzDeclarationRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.ReplaceProfessionalAchievementsRequest;

/** Applicant workspace and staff verification endpoints. @author Tinashe K */
@RestController
@RequestMapping("/api/admissions")
public class ApplicantApplicationWorkspaceController {

    private final ApplicantApplicationWorkspaceService workspaceService;
    private final CoreIdentityClient coreIdentityClient;
    private final ApplicantRegistrationIdentityResolver applicantRegistrationIdentityResolver;
    private final AdmissionsRollingWorkflowService rollingWorkflowService;

    public ApplicantApplicationWorkspaceController(
            ApplicantApplicationWorkspaceService workspaceService,
            CoreIdentityClient coreIdentityClient,
            ApplicantRegistrationIdentityResolver applicantRegistrationIdentityResolver,
            AdmissionsRollingWorkflowService rollingWorkflowService) {
        this.workspaceService = workspaceService;
        this.coreIdentityClient = coreIdentityClient;
        this.applicantRegistrationIdentityResolver = applicantRegistrationIdentityResolver;
        this.rollingWorkflowService = rollingWorkflowService;
    }

    @GetMapping("/applications/{applicationId}/workspace")
    public ApplicationWorkspace workspace(Authentication authentication, @PathVariable("applicationId") UUID applicationId) {
        return workspaceService.applicantWorkspace(applicationId, currentUser(authentication).user().id());
    }

    @GetMapping("/applications/{applicationId}/workspace/staff")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public ApplicationWorkspace staffWorkspace(@PathVariable("applicationId") UUID applicationId) {
        return workspaceService.staffWorkspace(applicationId);
    }

    @PutMapping("/applications/{applicationId}/profile")
    public ApplicationWorkspace saveProfile(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody SaveOwnProfileRequest request) {
        ApplicantRegistrationIdentity registeredIdentity =
                applicantRegistrationIdentityResolver.requireIdentity(authentication);
        return workspaceService.saveOwnProfile(applicationId, currentUser(authentication).user().id(),
                new UpdateApplicantProfileCommand(
                        request.applicantCategoryCode(), request.titleCode(), registeredIdentity.firstName(), request.middleNames(),
                        registeredIdentity.lastName(), request.dateOfBirth(), request.genderCode(), request.maritalStatusCode(),
                        request.nationalIdNumber(), request.passportNumber(), request.countryId(),
                        request.nationalityCountryId(), request.placeOfBirth(), request.disabilityStatusCode(),
                        request.specialNeeds(), request.sponsorTypeCode(), request.primaryEmail(), request.primaryPhone(),
                        request.postalAddress(), request.residentialAddress(),
                        "Applicant autosave before submission.", request.expectedVersion()));
    }

    @PostMapping("/applications/{applicationId}/next-of-kin")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationWorkspace addNextOfKin(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody SaveNextOfKinRequest request) {
        return saveNextOfKin(authentication, applicationId, null, request);
    }

    @PutMapping("/applications/{applicationId}/next-of-kin/{nextOfKinId}")
    public ApplicationWorkspace updateNextOfKin(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("nextOfKinId") UUID nextOfKinId, @Valid @RequestBody SaveNextOfKinRequest request) {
        return saveNextOfKin(authentication, applicationId, nextOfKinId, request);
    }

    @DeleteMapping("/applications/{applicationId}/next-of-kin/{nextOfKinId}")
    public ApplicationWorkspace deleteNextOfKin(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("nextOfKinId") UUID nextOfKinId, @RequestParam("expectedVersion") long expectedVersion) {
        return workspaceService.deleteNextOfKin(applicationId, currentUser(authentication).user().id(), nextOfKinId, expectedVersion);
    }

    @PostMapping("/applications/{applicationId}/employment-history")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationWorkspace addEmployment(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody SaveEmploymentRequest request) {
        return saveEmployment(authentication, applicationId, null, request);
    }

    @PutMapping("/applications/{applicationId}/employment-history/{employmentId}")
    public ApplicationWorkspace updateEmployment(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("employmentId") UUID employmentId, @Valid @RequestBody SaveEmploymentRequest request) {
        return saveEmployment(authentication, applicationId, employmentId, request);
    }

    @DeleteMapping("/applications/{applicationId}/employment-history/{employmentId}")
    public ApplicationWorkspace deleteEmployment(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("employmentId") UUID employmentId, @RequestParam("expectedVersion") long expectedVersion) {
        return workspaceService.deleteEmployment(applicationId, currentUser(authentication).user().id(), employmentId, expectedVersion);
    }

    @PostMapping("/applications/{applicationId}/referees")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationWorkspace addReferee(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody SaveRefereeRequest request) {
        return saveReferee(authentication, applicationId, null, request);
    }

    @PutMapping("/applications/{applicationId}/referees/{refereeId}")
    public ApplicationWorkspace updateReferee(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("refereeId") UUID refereeId, @Valid @RequestBody SaveRefereeRequest request) {
        return saveReferee(authentication, applicationId, refereeId, request);
    }

    @DeleteMapping("/applications/{applicationId}/referees/{refereeId}")
    public ApplicationWorkspace deleteReferee(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("refereeId") UUID refereeId, @RequestParam("expectedVersion") long expectedVersion) {
        return workspaceService.deleteReferee(applicationId, currentUser(authentication).user().id(), refereeId, expectedVersion);
    }

    @PostMapping("/applications/{applicationId}/referees/{refereeId}/invitation")
    public ApplicationWorkspace resendRefereeInvitation(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @PathVariable("refereeId") UUID refereeId,
            @RequestParam("expectedVersion") long expectedVersion) {
        return workspaceService.resendRefereeInvitation(
                applicationId, currentUser(authentication).user().id(), refereeId, expectedVersion);
    }

    @PutMapping("/applications/{applicationId}/prior-uz-declaration")
    public ApplicationWorkspace savePriorUzDeclaration(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody SavePriorUzDeclarationRequest request) {
        return workspaceService.savePriorUzDeclaration(
                applicationId,
                currentUser(authentication).user().id(),
                request.previouslyStudiedAtUz(),
                request.registrationNumber(),
                request.enrolmentStartedOn(),
                request.enrolmentEndedOn(),
                request.previouslyAcceptedOffer(),
                request.previouslyTookUpPlace());
    }

    @PutMapping("/applications/{applicationId}/professional-achievements")
    public ApplicationWorkspace replaceProfessionalAchievements(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody ReplaceProfessionalAchievementsRequest request) {
        return workspaceService.replaceProfessionalAchievements(
                applicationId,
                currentUser(authentication).user().id(),
                request.declaredNone(),
                request.achievements().stream()
                        .map(achievement -> new ApplicantApplicationWorkspaceService.ProfessionalAchievementInput(
                                achievement.type(), achievement.title(), achievement.organisation(),
                                achievement.achievedOn(), achievement.description()))
                        .toList());
    }

    @PostMapping("/applications/{applicationId}/qualifications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationWorkspace addQualification(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody SaveQualificationSittingRequest request) {
        return saveQualification(authentication, applicationId, null, request);
    }

    @PutMapping("/applications/{applicationId}/qualifications/{sittingId}")
    public ApplicationWorkspace updateQualification(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId, @Valid @RequestBody SaveQualificationSittingRequest request) {
        return saveQualification(authentication, applicationId, sittingId, request);
    }

    @DeleteMapping("/applications/{applicationId}/qualifications/{sittingId}")
    public ApplicationWorkspace deleteQualification(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId, @RequestParam("expectedVersion") long expectedVersion) {
        return workspaceService.deleteQualificationSitting(
                applicationId, currentUser(authentication).user().id(), sittingId, expectedVersion);
    }

    @PostMapping("/applications/{applicationId}/qualifications/{sittingId}/results")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationWorkspace addQualificationResult(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId, @Valid @RequestBody SaveQualificationResultRequest request) {
        return saveQualificationResult(authentication, applicationId, sittingId, null, request);
    }

    @PostMapping("/applications/{applicationId}/qualifications/{sittingId}/results/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationWorkspace addQualificationResults(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId,
            @Valid @RequestBody AddQualificationResultsRequest request) {
        return workspaceService.addQualificationResults(
                applicationId,
                currentUser(authentication).user().id(),
                sittingId,
                request.results().stream()
                        .map(result -> new CreateQualificationResultCommand(
                                result.subjectId(), result.grade(), result.principalSubject()))
                        .toList());
    }

    @PutMapping("/applications/{applicationId}/qualifications/{sittingId}/results/{resultId}")
    public ApplicationWorkspace updateQualificationResult(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId, @PathVariable("resultId") UUID resultId,
            @Valid @RequestBody SaveQualificationResultRequest request) {
        return saveQualificationResult(authentication, applicationId, sittingId, resultId, request);
    }

    @DeleteMapping("/applications/{applicationId}/qualifications/{sittingId}/results/{resultId}")
    public ApplicationWorkspace deleteQualificationResult(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId, @PathVariable("resultId") UUID resultId,
            @RequestParam("expectedVersion") long expectedVersion) {
        return workspaceService.deleteQualificationResult(
                applicationId, currentUser(authentication).user().id(), sittingId, resultId, expectedVersion);
    }

    @PutMapping("/applications/{applicationId}/programme-choices")
    public ApplicationWorkspace replaceProgrammeChoices(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody ReplaceProgrammeChoicesRequest request) {
        return workspaceService.replaceStructuredProgrammeChoices(
                applicationId, currentUser(authentication).user().id(), programmeChoiceSelections(request), false, null);
    }

    @PutMapping("/applications/{applicationId}/declaration")
    public ApplicationWorkspace acceptDeclaration(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody AcceptDeclarationRequest request) {
        return workspaceService.acceptDeclaration(
                applicationId, currentUser(authentication).user().id(), request.accepted(), request.declarationVersion());
    }

    @GetMapping("/verification-queue")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public VerificationQueue verificationQueue() {
        return workspaceService.verificationQueue();
    }

    @PostMapping("/applications/{applicationId}/qualifications/{sittingId}/decision")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public QualificationSittingSummary recordQualificationDecision(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("sittingId") UUID sittingId, @Valid @RequestBody QualificationDecisionRequest request) {
        UUID actorUserId = currentUser(authentication).user().id();
        QualificationSittingSummary result = workspaceService.recordQualificationDecision(
                applicationId, sittingId, actorUserId,
                request.decision(), request.reason(), request.expectedVersion());
        rollingWorkflowService.advance(applicationId, actorUserId);
        return result;
    }

    @PutMapping("/applications/{applicationId}/programme-choices/staff-amendment")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public ApplicationWorkspace amendProgrammeChoices(
            Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody ReplaceProgrammeChoicesRequest request) {
        return workspaceService.replaceStructuredProgrammeChoices(applicationId, currentUser(authentication).user().id(),
                programmeChoiceSelections(request), true, request.changeReason());
    }

    private ApplicationWorkspace saveNextOfKin(
            Authentication authentication, UUID applicationId, UUID nextOfKinId, SaveNextOfKinRequest request) {
        return workspaceService.saveNextOfKin(applicationId, currentUser(authentication).user().id(), nextOfKinId,
                request.fullName(), request.relationshipCode(), request.phoneNumber(), request.email(), request.address(),
                request.primary(), request.expectedVersion());
    }

    private ApplicationWorkspace saveEmployment(
            Authentication authentication, UUID applicationId, UUID employmentId, SaveEmploymentRequest request) {
        return workspaceService.saveEmployment(applicationId, currentUser(authentication).user().id(), employmentId,
                request.employerName(), request.positionTitle(), request.startedOn(), request.endedOn(), request.current(),
                request.responsibilities(), request.expectedVersion());
    }

    private ApplicationWorkspace saveReferee(
            Authentication authentication, UUID applicationId, UUID refereeId, SaveRefereeRequest request) {
        return workspaceService.saveReferee(applicationId, currentUser(authentication).user().id(), refereeId,
                request.fullName(), request.title(), request.organisation(), request.positionTitle(), request.email(),
                request.phoneNumber(), request.expertise(), request.relationshipToApplicant(), request.expectedVersion());
    }

    private ApplicationWorkspace saveQualification(
            Authentication authentication, UUID applicationId, UUID sittingId, SaveQualificationSittingRequest request) {
        return workspaceService.saveQualificationSitting(applicationId, currentUser(authentication).user().id(), sittingId,
                request.level(), request.examBodyId(), request.institutionName(), request.centreNumber(),
                request.candidateNumber(), request.yearWritten(), request.countryId(), request.documentId(), request.expectedVersion());
    }

    private ApplicationWorkspace saveQualificationResult(
            Authentication authentication, UUID applicationId, UUID sittingId, UUID resultId,
            SaveQualificationResultRequest request) {
        return workspaceService.saveQualificationResult(applicationId, currentUser(authentication).user().id(), sittingId,
                resultId, request.subjectId(), request.grade(),
                request.principalSubject(), request.expectedVersion());
    }

    private CoreCurrentUserProfile currentUser(Authentication authentication) {
        return coreIdentityClient.syncCurrentUser(authentication);
    }

    private java.util.List<ApplicantApplicationWorkspaceService.ProgrammeChoiceSelection> programmeChoiceSelections(
            ReplaceProgrammeChoicesRequest request) {
        if (request.choices() != null && !request.choices().isEmpty()) {
            return request.choices().stream().map(choice -> new ApplicantApplicationWorkspaceService.ProgrammeChoiceSelection(
                    choice.programmeId(), choice.entryOptionIds())).toList();
        }
        if (request.programmeIds() == null || request.programmeIds().isEmpty()) {
            throw new IllegalArgumentException("At least one structured programme choice is required.");
        }
        return request.programmeIds().stream()
                .map(programmeId -> new ApplicantApplicationWorkspaceService.ProgrammeChoiceSelection(programmeId, java.util.List.of()))
                .toList();
    }
}

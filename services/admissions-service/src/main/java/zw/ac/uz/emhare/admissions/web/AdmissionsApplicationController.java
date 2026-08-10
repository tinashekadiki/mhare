package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationConfigurationService;
import zw.ac.uz.emhare.admissions.application.ApplicationSummary;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicantCategoryOption;
import zw.ac.uz.emhare.admissions.application.CreateApplicationCommand;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.security.ApplicantRegistrationIdentityResolver;
import zw.ac.uz.emhare.admissions.security.ApplicantRegistrationIdentityResolver.ApplicantRegistrationIdentity;

@RestController
@RequestMapping("/api/admissions/applications")
public class AdmissionsApplicationController {

    private final AdmissionsApplicationService admissionsApplicationService;
    private final ApplicantApplicationConfigurationService applicantApplicationConfigurationService;
    private final CoreIdentityClient coreIdentityClient;
    private final ApplicantRegistrationIdentityResolver applicantRegistrationIdentityResolver;

    public AdmissionsApplicationController(
            AdmissionsApplicationService admissionsApplicationService,
            ApplicantApplicationConfigurationService applicantApplicationConfigurationService,
            CoreIdentityClient coreIdentityClient,
            ApplicantRegistrationIdentityResolver applicantRegistrationIdentityResolver) {
        this.admissionsApplicationService = admissionsApplicationService;
        this.applicantApplicationConfigurationService = applicantApplicationConfigurationService;
        this.coreIdentityClient = coreIdentityClient;
        this.applicantRegistrationIdentityResolver = applicantRegistrationIdentityResolver;
    }

    @GetMapping("/start-options")
    public ApplicationStartOptionsSummary applicationStartOptions(
            @RequestParam("applicantCategoryCode") String applicantCategoryCode) {
        return applicantApplicationConfigurationService.getStartOptions(applicantCategoryCode);
    }

    @GetMapping("/applicant-categories")
    public List<ApplicantCategoryOption> applicantCategories() {
        return applicantApplicationConfigurationService.getApplicantCategories();
    }

    @GetMapping
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public List<ApplicationSummary> listApplications() {
        return admissionsApplicationService.listApplications();
    }

    @GetMapping("/mine")
    public List<ApplicationSummary> myApplications(Authentication authentication) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        return admissionsApplicationService.listApplicationsForApplicant(profile.user().id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_APPLY')")
    public ApplicationSummary startApplication(Authentication authentication, @Valid @RequestBody CreateApplicationRequest request) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        ApplicantRegistrationIdentity registeredIdentity =
                applicantRegistrationIdentityResolver.requireIdentity(authentication);
        return admissionsApplicationService.startApplication(new CreateApplicationCommand(
                profile.user().id(),
                profile.user().keycloakUserId(),
                request.applicantCategoryCode(),
                registeredIdentity.firstName(),
                registeredIdentity.lastName(),
                null,
                profile.user().email(),
                request.intakeId(),
                request.applicationTypeId(),
                request.programmeIds()));
    }

    @PostMapping("/{applicationId}/submission")
    public ApplicationSummary submitApplication(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        return admissionsApplicationService.submitApplication(
                applicationId,
                profile.user().id());
    }

    @PostMapping("/{applicationId}/payment-waiver")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_PAYMENT_OVERRIDE')")
    public ApplicationSummary waivePayment(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody PaymentWaiverRequest request) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        return admissionsApplicationService.overridePayment(applicationId, profile.user().id(), request.reason());
    }

    @PostMapping("/{applicationId}/review")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_CONFIRM')")
    public ApplicationSummary moveToReview(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody MoveApplicationToReviewRequest request) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        return admissionsApplicationService.moveToReview(applicationId, profile.user().id(), request.reason());
    }

    @PostMapping("/{applicationId}/return-to-draft")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_CONFIRM')")
    public ApplicationSummary returnToDraft(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody ReturnApplicationToDraftRequest request) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        return admissionsApplicationService.returnToDraft(
                applicationId, profile.user().id(), request.reason());
    }
}

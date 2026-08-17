package zw.ac.uz.emhare.admissions.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.*;
import zw.ac.uz.emhare.admissions.api.model.*;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationService;
import zw.ac.uz.emhare.admissions.application.AdmissionsRollingWorkflowService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationConfigurationService;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicantCategoryOption;
import zw.ac.uz.emhare.admissions.application.ApplicationSummary;
import zw.ac.uz.emhare.admissions.application.command.*;
import zw.ac.uz.emhare.admissions.application.command.CreateApplicationCommand;
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
  private final AdmissionsRollingWorkflowService rollingWorkflowService;

  @org.springframework.beans.factory.annotation.Autowired
  public AdmissionsApplicationController(
      AdmissionsApplicationService admissionsApplicationService,
      ApplicantApplicationConfigurationService applicantApplicationConfigurationService,
      CoreIdentityClient coreIdentityClient,
      ApplicantRegistrationIdentityResolver applicantRegistrationIdentityResolver,
      AdmissionsRollingWorkflowService rollingWorkflowService) {
    this.admissionsApplicationService = admissionsApplicationService;
    this.applicantApplicationConfigurationService = applicantApplicationConfigurationService;
    this.coreIdentityClient = coreIdentityClient;
    this.applicantRegistrationIdentityResolver = applicantRegistrationIdentityResolver;
    this.rollingWorkflowService = rollingWorkflowService;
  }

  public AdmissionsApplicationController(
      AdmissionsApplicationService admissionsApplicationService,
      ApplicantApplicationConfigurationService applicantApplicationConfigurationService,
      CoreIdentityClient coreIdentityClient,
      ApplicantRegistrationIdentityResolver applicantRegistrationIdentityResolver) {
    this(
        admissionsApplicationService,
        applicantApplicationConfigurationService,
        coreIdentityClient,
        applicantRegistrationIdentityResolver,
        null);
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
  public ApplicationSummary startApplication(
      Authentication authentication, @Valid @RequestBody CreateApplicationRequest request) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    ApplicantRegistrationIdentity registeredIdentity =
        applicantRegistrationIdentityResolver.requireIdentity(authentication);
    return admissionsApplicationService.startApplication(
        new CreateApplicationCommand(
            profile.user().id(),
            profile.user().keycloakUserId(),
            request.applicantCategoryCode(),
            registeredIdentity.firstName(),
            registeredIdentity.lastName(),
            null,
            profile.user().email(),
            request.intakeId(),
            request.applicationTypeId(),
            request.programmeIds()),
        authorization(authentication));
  }

  private String authorization(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      return "Bearer " + jwtAuthenticationToken.getToken().getTokenValue();
    }
    throw new IllegalStateException(
        "JWT authentication is required for governed application-fee pricing.");
  }

  @PostMapping("/{applicationId}/submission")
  public ApplicationSummary submitApplication(
      Authentication authentication, @PathVariable("applicationId") UUID applicationId) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    ApplicationSummary summary =
        admissionsApplicationService.submitApplication(applicationId, profile.user().id());
    if (rollingWorkflowService != null)
      rollingWorkflowService.advance(applicationId, profile.user().id());
    return summary;
  }

  @PostMapping("/{applicationId}/payment-waiver")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_PAYMENT_OVERRIDE')")
  public ApplicationSummary waivePayment(
      Authentication authentication,
      @PathVariable("applicationId") UUID applicationId,
      @Valid @RequestBody PaymentWaiverRequest request) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    ApplicationSummary summary =
        admissionsApplicationService.overridePayment(
            applicationId, profile.user().id(), request.reason());
    if (rollingWorkflowService != null)
      rollingWorkflowService.advance(applicationId, profile.user().id());
    return summary;
  }

  @PostMapping("/{applicationId}/review")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_CONFIRM')")
  public ApplicationSummary moveToReview(
      Authentication authentication,
      @PathVariable("applicationId") UUID applicationId,
      @Valid @RequestBody MoveApplicationToReviewRequest request) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    return admissionsApplicationService.moveToReview(
        applicationId, profile.user().id(), request.reason());
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

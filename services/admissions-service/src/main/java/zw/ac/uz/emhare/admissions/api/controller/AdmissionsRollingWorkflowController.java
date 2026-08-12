package zw.ac.uz.emhare.admissions.api.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.admissions.api.model.RollingAdmissionsRequests.*;
import zw.ac.uz.emhare.admissions.application.AdmissionOfferSummary;
import zw.ac.uz.emhare.admissions.application.AdmissionsRollingWorkflowService;
import zw.ac.uz.emhare.admissions.application.AdmissionsWorkItemService;
import zw.ac.uz.emhare.admissions.application.AdmissionsWorkItemViews.*;
import zw.ac.uz.emhare.admissions.application.DirectAdmissionOfferService;
import zw.ac.uz.emhare.admissions.application.DirectAdmissionOfferService.*;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;

/** Public rolling Admissions work-item and transition API. @author Tinashe K */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionsRollingWorkflowController {
    private final AdmissionsWorkItemService workItemService;
    private final AdmissionsRollingWorkflowService workflowService;
    private final CoreIdentityClient coreIdentityClient;
    private final DirectAdmissionOfferService offerService;

    public AdmissionsRollingWorkflowController(AdmissionsWorkItemService workItemService,
            AdmissionsRollingWorkflowService workflowService, CoreIdentityClient coreIdentityClient,
            DirectAdmissionOfferService offerService) {
        this.workItemService = workItemService;
        this.workflowService = workflowService;
        this.coreIdentityClient = coreIdentityClient;
        this.offerService = offerService;
    }

    @GetMapping("/work-items")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW') or @admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public WorkItemPage list(Authentication authentication, @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "intakeId", required = false) UUID intakeId,
            @RequestParam(value = "applicationTypeId", required = false) UUID applicationTypeId,
            @RequestParam(value = "programmeId", required = false) UUID programmeId,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size) {
        return workItemService.list(search, stage, intakeId, applicationTypeId, programmeId, outcome, page, size,
                coreIdentityClient.syncCurrentUser(authentication));
    }

    @GetMapping("/work-items/{applicationId}")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW') or @admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public WorkItemCase get(Authentication authentication, @PathVariable("applicationId") UUID applicationId) {
        return workItemService.get(applicationId, coreIdentityClient.syncCurrentUser(authentication));
    }

    @PostMapping("/applications/{applicationId}/eligibility/recalculate")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ELIGIBILITY_REVIEW')")
    public WorkItemCase recalculate(Authentication authentication, @PathVariable("applicationId") UUID applicationId) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        workflowService.recalculateEligibility(applicationId, profile.user().id());
        return workItemService.get(applicationId, profile);
    }

    @PostMapping("/applications/{applicationId}/choices/{choiceId}/eligibility-resolution")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ELIGIBILITY_REVIEW')")
    public WorkItemCase resolve(Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("choiceId") UUID choiceId, @Valid @RequestBody EligibilityResolutionRequest request) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        workflowService.resolveEligibility(applicationId, choiceId, request.outcome(), request.reason(), profile.user().id());
        return workItemService.get(applicationId, profile);
    }

    @PostMapping("/applications/{applicationId}/choices/{choiceId}/academic-recommendation")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public WorkItemCase recommend(Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("choiceId") UUID choiceId, @Valid @RequestBody AcademicRecommendationRequest request) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        workflowService.recommend(applicationId, choiceId, request.recommendation(), request.reason(), profile);
        return workItemService.get(applicationId, profile);
    }

    @PostMapping("/applications/{applicationId}/choices/{choiceId}/decision")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_DECISION_MAKE')")
    public AdmissionOfferSummary decide(Authentication authentication, @PathVariable("applicationId") UUID applicationId,
            @PathVariable("choiceId") UUID choiceId, @Valid @RequestBody AdmissionDecisionRequest request) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        return workflowService.decide(applicationId, choiceId, request.decision(), request.reason(), profile.user().id());
    }

    @PutMapping("/offers/{offerId}")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_OFFER_MANAGE')")
    public AdmissionOfferSummary updateOffer(@PathVariable("offerId") UUID offerId,
            @Valid @RequestBody UpdateOfferRequest request) {
        return offerService.update(offerId, request.offerType(), request.conditionsText(), request.acceptanceDeadline(),
                request.registrationDate(), request.orientationDate(), request.commencementDate());
    }

    @PostMapping("/offers/{offerId}/document-generation")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_OFFER_MANAGE')")
    public DocumentGenerationResult generateDocument(Authentication authentication,
            @PathVariable("offerId") UUID offerId) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        return offerService.generate(offerId, profile.user().id());
    }

    @PostMapping("/offers/{offerId}/publish-and-send")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_OFFER_MANAGE')")
    public PublicationResult publishAndSend(Authentication authentication,
            @PathVariable("offerId") UUID offerId) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        return offerService.publishAndSend(offerId, profile.user().id());
    }

    @PostMapping("/offers/{offerId}/email-retry")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_OFFER_MANAGE')")
    public PublicationResult retryEmail(Authentication authentication, @PathVariable("offerId") UUID offerId,
            @Valid @RequestBody EmailRetryRequest request) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        return offerService.retryEmail(offerId, request.reason(), profile.user().id());
    }

    @GetMapping("/applicant/offers/{offerId}/published-document")
    public PublishedDocumentAccess applicantPublishedDocument(Authentication authentication,
            @PathVariable("offerId") UUID offerId) {
        var profile = coreIdentityClient.syncCurrentUser(authentication);
        return offerService.applicantDocument(offerId, profile.user().id());
    }
}

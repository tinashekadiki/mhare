package zw.ac.uz.emhare.admissions.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.*;
import zw.ac.uz.emhare.admissions.api.model.*;
import zw.ac.uz.emhare.admissions.application.AdmissionOfferSummary;
import zw.ac.uz.emhare.admissions.application.AdmissionRequirementSetSummary;
import zw.ac.uz.emhare.admissions.application.AdmissionsSelectionOfferService;
import zw.ac.uz.emhare.admissions.application.ApplicationTypeSummary;
import zw.ac.uz.emhare.admissions.application.OfferBatchSummary;
import zw.ac.uz.emhare.admissions.application.SelectionDecisionSummary;
import zw.ac.uz.emhare.admissions.application.SelectionRoundSummary;
import zw.ac.uz.emhare.admissions.application.command.*;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;

/**
 * @author Tinashe K
 */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionsSelectionOfferController {

  private final AdmissionsSelectionOfferService workflowService;
  private final CoreIdentityClient coreIdentityClient;

  public AdmissionsSelectionOfferController(
      AdmissionsSelectionOfferService workflowService, CoreIdentityClient coreIdentityClient) {
    this.workflowService = workflowService;
    this.coreIdentityClient = coreIdentityClient;
  }

  @GetMapping("/application-types")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
  public List<ApplicationTypeSummary> applicationTypes() {
    return workflowService.listApplicationTypes();
  }

  @PostMapping("/application-types")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationTypeSummary createApplicationType(
      @Valid @RequestBody CreateApplicationTypeRequest request) {
    return workflowService.createApplicationType(
        request.code(),
        request.name(),
        request.requiresEmploymentHistory(),
        request.requiresReferees(),
        request.financeFeeStructureId(),
        request.financeFeeStructureCode(),
        request.financeFeeStructureName(),
        request.active());
  }

  @PutMapping("/application-types/{applicationTypeId}")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
  public ApplicationTypeSummary updateApplicationType(
      @PathVariable("applicationTypeId") UUID applicationTypeId,
      @Valid @RequestBody UpdateApplicationTypeRequest request) {
    return workflowService.updateApplicationType(
        applicationTypeId,
        request.name(),
        request.requiresEmploymentHistory(),
        request.requiresReferees(),
        request.financeFeeStructureId(),
        request.financeFeeStructureCode(),
        request.financeFeeStructureName(),
        request.active(),
        request.changeReason(),
        request.expectedVersion());
  }

  @GetMapping("/requirement-sets")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public List<AdmissionRequirementSetSummary> requirementSets() {
    return workflowService.listRequirementSets();
  }

  @PostMapping("/requirement-sets")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public AdmissionRequirementSetSummary createRequirementSet(
      @Valid @RequestBody CreateAdmissionRequirementSetRequest request) {
    return workflowService.createRequirementSet(
        request.programmeId(),
        request.applicationTypeId(),
        request.intakeId(),
        request.versionCode(),
        request.effectiveFrom(),
        request.effectiveTo(),
        request.minimumTotalPoints(),
        request.maleCutoffPoints(),
        request.femaleCutoffPoints(),
        request.requiresEnglish(),
        request.requiresMathematicsOrScience(),
        request.advancedRules(),
        request.advancedRulesVersion(),
        request.qualificationGroups() == null
            ? java.util.List.of()
            : request.qualificationGroups().stream()
                .map(
                    group ->
                        new AdmissionsSelectionOfferService.QualificationRequirementGroupInput(
                            group.code(),
                            group.name(),
                            group.minimumSatisfiedItems(),
                            group.sortOrder(),
                            group.items().stream()
                                .map(
                                    item ->
                                        new AdmissionsSelectionOfferService
                                            .QualificationRequirementItemInput(
                                            item.qualificationLevel(),
                                            item.minimumCount(),
                                            item.minimumTotalPoints(),
                                            item.minimumDurationMonths(),
                                            item.sortOrder()))
                                .toList()))
                .toList());
  }

  @PostMapping("/requirement-sets/{requirementSetId}/approve")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
  public AdmissionRequirementSetSummary approveRequirementSet(
      Authentication authentication, @PathVariable("requirementSetId") UUID requirementSetId) {
    return workflowService.approveRequirementSet(
        requirementSetId, currentUser(authentication).user().id());
  }

  @GetMapping("/selection-rounds")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_REVIEW_RELEASE')")
  public List<SelectionRoundSummary> selectionRounds() {
    return workflowService.listSelectionRounds();
  }

  @GetMapping("/selection-rounds/{selectionRoundId}/decisions")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public List<SelectionDecisionSummary> selectionDecisions(
      @PathVariable("selectionRoundId") UUID selectionRoundId) {
    return workflowService.listSelectionDecisions(selectionRoundId);
  }

  @GetMapping("/offer-batches")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_OFFER_MANAGE')")
  public List<OfferBatchSummary> offerBatches() {
    return workflowService.listOfferBatches();
  }

  @GetMapping("/offers")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_OFFER_MANAGE')")
  public List<AdmissionOfferSummary> offers() {
    return workflowService.listOffers();
  }

  @GetMapping("/offers/mine")
  public List<AdmissionOfferSummary> myOffers(Authentication authentication) {
    return workflowService.listApplicantOffers(currentUser(authentication).user().id());
  }

  @PostMapping("/offers/{offerId}/conditions/{conditionId}/resolve")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public AdmissionOfferSummary resolveOfferCondition(
      Authentication authentication,
      @PathVariable UUID offerId,
      @PathVariable UUID conditionId,
      @Valid @RequestBody ResolveOfferConditionRequest request) {
    return workflowService.resolveOfferCondition(
        offerId,
        conditionId,
        request.resolution(),
        request.notes(),
        currentUser(authentication).user().id());
  }

  @PostMapping("/offers/{offerId}/withdraw")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public AdmissionOfferSummary withdrawOffer(
      Authentication authentication,
      @PathVariable UUID offerId,
      @Valid @RequestBody WithdrawOfferRequest request) {
    return workflowService.withdrawOffer(
        offerId, request.reason(), currentUser(authentication).user().id());
  }

  @PostMapping("/offers/{offerId}/expire")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public AdmissionOfferSummary expireOffer(
      Authentication authentication, @PathVariable UUID offerId) {
    return workflowService.expireOffer(offerId, currentUser(authentication).user().id());
  }

  @PostMapping("/offers/{offerId}/response")
  public AdmissionOfferSummary respondToOffer(
      Authentication authentication,
      @PathVariable UUID offerId,
      @Valid @RequestBody OfferResponseRequest request) {
    return workflowService.respondToOffer(
        offerId, currentUser(authentication).user().id(), request.response(), request.notes());
  }

  private CoreCurrentUserProfile currentUser(Authentication authentication) {
    return coreIdentityClient.syncCurrentUser(authentication);
  }
}

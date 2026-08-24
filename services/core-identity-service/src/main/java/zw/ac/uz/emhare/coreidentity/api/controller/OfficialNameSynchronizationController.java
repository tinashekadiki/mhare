package zw.ac.uz.emhare.coreidentity.api.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.coreidentity.api.model.SynchronizeOfficialNameRequest;
import zw.ac.uz.emhare.coreidentity.rbac.CoreIdentityService;
import zw.ac.uz.emhare.coreidentity.rbac.OfficialNameSynchronizationService;
import zw.ac.uz.emhare.coreidentity.rbac.OfficialNameSynchronizationService.OfficialNameSynchronizationSummary;

/** Governs evidence-backed name synchronization into Core and Keycloak. @author Tinashe K */
@RestController
@RequestMapping("/api/core/users")
public class OfficialNameSynchronizationController {

  private final OfficialNameSynchronizationService synchronizationService;
  private final CoreIdentityService coreIdentityService;
  private final EmhareCurrentUserResolver currentUserResolver;

  public OfficialNameSynchronizationController(
      OfficialNameSynchronizationService synchronizationService,
      CoreIdentityService coreIdentityService,
      EmhareCurrentUserResolver currentUserResolver) {
    this.synchronizationService = synchronizationService;
    this.coreIdentityService = coreIdentityService;
    this.currentUserResolver = currentUserResolver;
  }

  @PutMapping("/{userId}/official-name")
  @PreAuthorize(
      "@coreRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW') or "
          + "@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
  public OfficialNameSynchronizationSummary synchronizeOfficialName(
      Authentication authentication,
      @PathVariable("userId") UUID userId,
      @Valid @RequestBody SynchronizeOfficialNameRequest request) {
    EmhareCurrentUser currentUser =
        currentUserResolver
            .fromAuthentication(authentication)
            .orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
    UUID actorUserId =
        coreIdentityService.syncAuthenticatedUser(currentUser, null, null, null).user().id();
    return synchronizationService.synchronize(
        request.sourceRequestId(),
        request.sourceApplicationId(),
        request.sourceDocumentId(),
        userId,
        request.firstName(),
        request.middleNames(),
        request.lastName(),
        request.approvalReason(),
        actorUserId);
  }
}

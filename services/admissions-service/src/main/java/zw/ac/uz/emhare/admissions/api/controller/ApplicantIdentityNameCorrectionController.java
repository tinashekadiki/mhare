package zw.ac.uz.emhare.admissions.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.ApplicantIdentityNameCorrectionService;
import zw.ac.uz.emhare.admissions.application.IdentityNameCorrectionSummary;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.OfficialNameSynchronizationRequest;

/** Applicant review and staff decision endpoints for identity-name mismatches. @author Tinashe K */
@RestController
@RequestMapping("/api/admissions")
public class ApplicantIdentityNameCorrectionController {

  private final ApplicantIdentityNameCorrectionService correctionService;
  private final CoreIdentityClient coreIdentityClient;

  public ApplicantIdentityNameCorrectionController(
      ApplicantIdentityNameCorrectionService correctionService,
      CoreIdentityClient coreIdentityClient) {
    this.correctionService = correctionService;
    this.coreIdentityClient = coreIdentityClient;
  }

  @PutMapping("/applications/{applicationId}/identity-name-correction/ocr-reading")
  public IdentityNameCorrectionSummary reviewOcrReading(
      Authentication authentication,
      @PathVariable("applicationId") UUID applicationId,
      @Valid @RequestBody IdentityNameReadingRequest request) {
    UUID applicantUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
    return correctionService.reviewOcrReading(
        applicationId,
        applicantUserId,
        request.documentId(),
        request.firstName(),
        request.middleNames(),
        request.lastName());
  }

  @PostMapping("/applications/{applicationId}/identity-name-correction/request")
  public IdentityNameCorrectionSummary requestOfficialNameCorrection(
      Authentication authentication,
      @PathVariable("applicationId") UUID applicationId,
      @Valid @RequestBody RequestIdentityNameCorrection request) {
    UUID applicantUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
    return correctionService.requestOfficialNameCorrection(
        applicationId,
        applicantUserId,
        request.documentId(),
        request.firstName(),
        request.middleNames(),
        request.lastName(),
        request.reason());
  }

  @PostMapping("/identity-name-corrections/{correctionId}/approve")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public IdentityNameCorrectionSummary approve(
      Authentication authentication,
      @PathVariable("correctionId") UUID correctionId,
      @Valid @RequestBody DecideIdentityNameCorrection request) {
    var staffProfile = coreIdentityClient.syncCurrentUser(authentication);
    var context = correctionService.approvalContext(correctionId);
    coreIdentityClient.synchronizeOfficialName(
        authorization(authentication),
        context.coreUserId(),
        new OfficialNameSynchronizationRequest(
            context.correctionId(),
            context.applicationId(),
            context.documentId(),
            context.approvedFirstName(),
            context.approvedMiddleNames(),
            context.approvedLastName(),
            request.reason()));
    return correctionService.completeApproval(
        correctionId, staffProfile.user().id(), request.reason());
  }

  @PostMapping("/identity-name-corrections/{correctionId}/reject")
  @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
  public IdentityNameCorrectionSummary reject(
      Authentication authentication,
      @PathVariable("correctionId") UUID correctionId,
      @Valid @RequestBody DecideIdentityNameCorrection request) {
    UUID staffUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
    return correctionService.reject(correctionId, staffUserId, request.reason());
  }

  private String authorization(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken token) {
      return "Bearer " + token.getToken().getTokenValue();
    }
    throw new IllegalStateException("JWT authentication is required.");
  }

  public record IdentityNameReadingRequest(
      @NotNull UUID documentId,
      @NotBlank @Size(max = 100) String firstName,
      @Size(max = 150) String middleNames,
      @NotBlank @Size(max = 100) String lastName) {}

  public record RequestIdentityNameCorrection(
      @NotNull UUID documentId,
      @NotBlank @Size(max = 100) String firstName,
      @Size(max = 150) String middleNames,
      @NotBlank @Size(max = 100) String lastName,
      @NotBlank @Size(min = 10, max = 1000) String reason) {}

  public record DecideIdentityNameCorrection(@NotBlank @Size(min = 10, max = 1000) String reason) {}
}

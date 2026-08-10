package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.admissions.application.AcademicReviewSummary;
import zw.ac.uz.emhare.admissions.application.AcademicReviewBatchPreview;
import zw.ac.uz.emhare.admissions.application.AdmissionsAcademicReviewService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationWorkspace;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.web.AcademicReviewRequests.*;

/** Gateway-routed academic-unit recommendation workflow. @author Tinashe K */
@RestController
@RequestMapping("/api/admissions/academic-reviews")
public class AdmissionsAcademicReviewController {
    private final AdmissionsAcademicReviewService reviewService;
    private final CoreIdentityClient coreIdentityClient;
    private final ApplicantApplicationWorkspaceService workspaceService;

    public AdmissionsAcademicReviewController(
            AdmissionsAcademicReviewService reviewService,
            CoreIdentityClient coreIdentityClient,
            ApplicantApplicationWorkspaceService workspaceService) {
        this.reviewService = reviewService;
        this.coreIdentityClient = coreIdentityClient;
        this.workspaceService = workspaceService;
    }

    @PostMapping("/selection-rounds/{selectionRoundId}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_REVIEW_RELEASE')")
    public List<AcademicReviewSummary> release(
            Authentication authentication,
            @PathVariable("selectionRoundId") UUID selectionRoundId,
            @RequestBody(required = false) ReleaseAcademicReviews request) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        ReleaseAcademicReviews filters = request == null ? new ReleaseAcademicReviews(null, null, null, null) : request;
        return reviewService.releaseEligibleChoices(selectionRoundId, filters.programmeId(),
                filters.academicUnitId(), filters.highestAcademicUnitId(), filters.dueAt(), profile.user().id());
    }

    @GetMapping("/selection-rounds/{selectionRoundId}/release-preview")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_REVIEW_RELEASE')")
    public AcademicReviewBatchPreview previewRelease(
            @PathVariable("selectionRoundId") UUID selectionRoundId) {
        return reviewService.previewReleaseBatches(selectionRoundId);
    }

    @GetMapping
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SELECTION_APPROVE')")
    public List<AcademicReviewSummary> list() { return reviewService.listAssignments(); }

    @GetMapping("/mine")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public List<AcademicReviewSummary> mine(Authentication authentication) {
        return reviewService.listMyAssignments(coreIdentityClient.syncCurrentUser(authentication));
    }

    @GetMapping("/{assignmentId}/application-workspace")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public ApplicationWorkspace applicationWorkspace(
            Authentication authentication,
            @PathVariable("assignmentId") UUID assignmentId) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        UUID applicationId = reviewService.applicationIdForScopedAcademicReviewer(assignmentId, profile);
        return workspaceService.staffWorkspace(applicationId);
    }

    @PostMapping("/{assignmentId}/claim")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public AcademicReviewSummary claim(Authentication authentication,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody ClaimAcademicReview request) {
        return reviewService.claim(assignmentId, request.expectedVersion(),
                coreIdentityClient.syncCurrentUser(authentication));
    }

    @PostMapping("/{assignmentId}/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
    public AcademicReviewSummary recommend(Authentication authentication,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody RecordAcademicRecommendation request) {
        return reviewService.recommend(assignmentId, request.recommendation(), request.rankPosition(),
                request.quotaTypeCode(), request.reason(), request.expectedVersion(),
                coreIdentityClient.syncCurrentUser(authentication));
    }

    @PostMapping("/{assignmentId}/review")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SELECTION_APPROVE')")
    public AcademicReviewSummary review(Authentication authentication,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody ReviewAcademicRecommendation request) {
        CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
        return reviewService.reviewRecommendation(assignmentId, request.action(), request.finalDecision(),
                request.reason(), profile.user().id());
    }

    @PostMapping("/{assignmentId}/waitlist-release")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SELECTION_APPROVE')")
    public AcademicReviewSummary releaseWaitlist(Authentication authentication,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody ReleaseWaitlist request) {
        return reviewService.releaseWaitlist(assignmentId, request.reason(),
                coreIdentityClient.syncCurrentUser(authentication).user().id());
    }
}

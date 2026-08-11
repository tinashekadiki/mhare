package zw.ac.uz.emhare.admissions.web;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.admissions.application.AcademicReviewSummary;
import zw.ac.uz.emhare.admissions.application.AdmissionsAcademicReviewService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationWorkspace;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;

/**
 * Read-only academic-review case-history endpoints (assignment listing and reviewer-scoped
 * workspace access). The release/claim/recommendations/review/waitlist-release write endpoints
 * that used to live here were retired per ADR-0014 (2026-08-11 admissions backend rolling-workflow
 * plan, Task 4).
 *
 * @author Tinashe K
 */
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

}

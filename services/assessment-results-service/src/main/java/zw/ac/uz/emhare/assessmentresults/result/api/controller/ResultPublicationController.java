package zw.ac.uz.emhare.assessmentresults.result.api.controller;

import zw.ac.uz.emhare.assessmentresults.result.*;
import zw.ac.uz.emhare.assessmentresults.result.api.model.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultRequests.CreateGradingScheme;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultRequests.CreateResultBatch;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultRequests.Decision;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultRequests.RequestPublishedResultAmendment;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultResponses.BatchSummary;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultResponses.CorrectionSourceSummary;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultResponses.GradingSummary;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultResponses.PublishedResultAmendmentSummary;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultResponses.PublishedResultPage;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/results")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin')")
public class ResultPublicationController {

    private final ResultPublicationService resultPublicationService;
    private final EmhareCurrentUserResolver currentUserResolver;

    public ResultPublicationController(
            ResultPublicationService resultPublicationService,
            EmhareCurrentUserResolver currentUserResolver) {
        this.resultPublicationService = resultPublicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/grading-schemes")
    public List<GradingSummary> grading() {
        return resultPublicationService.grading();
    }

    @PostMapping("/grading-schemes")
    public GradingSummary createGrading(@Valid @RequestBody CreateGradingScheme request) {
        return resultPublicationService.createGrading(request);
    }

    @PostMapping("/grading-schemes/{id}/approve")
    public GradingSummary approveGrading(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody Decision request) {
        return resultPublicationService.approveGrading(id, request, actor(authentication));
    }

    @GetMapping("/batches")
    public List<BatchSummary> batches() {
        return resultPublicationService.batches();
    }

    @PostMapping("/batches")
    public BatchSummary createBatch(
            Authentication authentication,
            @Valid @RequestBody CreateResultBatch request) {
        return resultPublicationService.createBatch(request, actor(authentication));
    }

    @PostMapping("/batches/{id}/{action:submit|moderate|approve|publish}")
    public BatchSummary moveBatch(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable String action,
            @Valid @RequestBody Decision request) {
        return resultPublicationService.moveBatch(id, action, request, actor(authentication));
    }

    @GetMapping("/published-results")
    public PublishedResultPage publishedResults(
            @RequestParam(defaultValue = "") String studentNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return resultPublicationService.publishedResults(studentNumber, page, size);
    }

    @GetMapping("/published-results/{id}/correction-sources")
    public List<CorrectionSourceSummary> correctionSources(@PathVariable UUID id) {
        return resultPublicationService.correctionSources(id);
    }

    @GetMapping("/published-result-amendments")
    public List<PublishedResultAmendmentSummary> amendments() {
        return resultPublicationService.amendments();
    }

    @PostMapping("/published-result-amendments")
    public PublishedResultAmendmentSummary requestAmendment(
            Authentication authentication,
            @Valid @RequestBody RequestPublishedResultAmendment request) {
        return resultPublicationService.requestAmendment(request, actor(authentication));
    }

    @PostMapping("/published-result-amendments/{id}/{action:review|approve|apply|reject}")
    public PublishedResultAmendmentSummary moveAmendment(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable String action,
            @Valid @RequestBody Decision request) {
        return resultPublicationService.moveAmendment(id, action, request, actor(authentication));
    }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}

package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentCommands.*;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentViews.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/assessment-results")
public class AssessmentOperationsController {
    private static final String ACADEMIC_OPERATIONS="hasAnyAuthority('ROLE_system-admin', 'ROLE_academic-admin')";
    private final GovernedAssessmentService service; private final EmhareCurrentUserResolver currentUserResolver;
    public AssessmentOperationsController(GovernedAssessmentService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}

    @GetMapping("/offerings") @PreAuthorize(ACADEMIC_OPERATIONS)
    public List<OfferingSummary> offerings(){return service.listOfferings();}
    @GetMapping("/roster-sources") @PreAuthorize(ACADEMIC_OPERATIONS)
    public List<RosterSourceSummary> rosterSources(){return service.rosterSources();}
    @PostMapping("/offerings") @PreAuthorize(ACADEMIC_OPERATIONS)
    public ResponseEntity<OfferingSummary> createOffering(@Valid @RequestBody CreateOffering request){OfferingSummary created=service.createOffering(request);return ResponseEntity.created(URI.create("/api/assessment-results/offerings/"+created.id())).body(created);}
    @PostMapping("/offerings/{offeringId}/schemes") @PreAuthorize(ACADEMIC_OPERATIONS)
    public ResponseEntity<SchemeSummary> createScheme(@PathVariable UUID offeringId,@Valid @RequestBody CreateScheme request){SchemeSummary created=service.createScheme(offeringId,request);return ResponseEntity.created(URI.create("/api/assessment-results/schemes/"+created.id())).body(created);}
    @PostMapping("/schemes/{schemeId}/approve") @PreAuthorize(ACADEMIC_OPERATIONS)
    public SchemeSummary approveScheme(Authentication authentication,@PathVariable UUID schemeId,@Valid @RequestBody Decision request){return service.approveScheme(schemeId,request,actor(authentication));}
    @GetMapping("/components/{componentId}/roster") @PreAuthorize(ACADEMIC_OPERATIONS)
    public List<RosterMarkSummary> componentRoster(@PathVariable UUID componentId){return service.componentRoster(componentId);}
    @PostMapping("/components/{componentId}/marks") @PreAuthorize(ACADEMIC_OPERATIONS)
    public List<MarkSummary> captureMarks(Authentication authentication,@PathVariable UUID componentId,@Valid @RequestBody CaptureMarkBatch request){EmhareCurrentUser user=user(authentication);return service.captureMarks(componentId,request,user.auditUserId(),user.hasRealmRole("system-admin"));}
    @PostMapping("/marks/{markId}/submit") @PreAuthorize(ACADEMIC_OPERATIONS)
    public MarkSummary submitMark(Authentication authentication,@PathVariable UUID markId,@RequestParam long expectedVersion){EmhareCurrentUser user=user(authentication);return service.submitMark(markId,expectedVersion,user.auditUserId(),user.hasRealmRole("system-admin"));}
    @PostMapping("/marks/{markId}/amendments") @PreAuthorize(ACADEMIC_OPERATIONS)
    public AmendmentSummary requestAmendment(Authentication authentication,@PathVariable UUID markId,@Valid @RequestBody RequestAmendment request){EmhareCurrentUser user=user(authentication);return service.requestAmendment(markId,request,user.auditUserId(),user.hasRealmRole("system-admin"));}
    @GetMapping("/amendments") @PreAuthorize(ACADEMIC_OPERATIONS)
    public List<AmendmentSummary> amendments(){return service.amendmentQueue();}
    @PostMapping("/amendments/{amendmentId}/approve") @PreAuthorize(ACADEMIC_OPERATIONS)
    public AmendmentSummary approveAmendment(Authentication authentication,@PathVariable UUID amendmentId,@Valid @RequestBody Decision request){return service.approveAmendment(amendmentId,request,actor(authentication));}
    @PostMapping("/amendments/{amendmentId}/reject") @PreAuthorize(ACADEMIC_OPERATIONS)
    public AmendmentSummary rejectAmendment(Authentication authentication,@PathVariable UUID amendmentId,@Valid @RequestBody Decision request){return service.rejectAmendment(amendmentId,request,actor(authentication));}
    @PostMapping("/offerings/{offeringId}/calculations") @PreAuthorize(ACADEMIC_OPERATIONS)
    public CalculationRunSummary calculate(Authentication authentication,@PathVariable UUID offeringId){return service.calculate(offeringId,actor(authentication));}
    @GetMapping("/calculations") @PreAuthorize(ACADEMIC_OPERATIONS)
    public List<CalculationRunSummary> calculations(){return service.calculationHistory();}
    private UUID actor(Authentication authentication){return user(authentication).auditUserId();}
    private EmhareCurrentUser user(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required."));}
}

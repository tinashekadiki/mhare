package zw.ac.uz.emhare.assessmentresults.progression;

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
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.CalculateDecision;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.CreateRuleSet;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.WorkflowDecision;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.DecisionSummary;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.RosterSummary;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.RuleSetSummary;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/results/progression")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin')")
public class ProgrammeProgressionController {

    private final ProgrammeProgressionService progressionService;
    private final EmhareCurrentUserResolver currentUserResolver;

    public ProgrammeProgressionController(
            ProgrammeProgressionService progressionService,
            EmhareCurrentUserResolver currentUserResolver) {
        this.progressionService = progressionService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/rule-sets")
    public List<RuleSetSummary> ruleSets() {
        return progressionService.ruleSets();
    }

    @PostMapping("/rule-sets")
    public RuleSetSummary createRuleSet(@Valid @RequestBody CreateRuleSet request) {
        return progressionService.createRuleSet(request);
    }

    @PostMapping("/rule-sets/{id}/approve")
    public RuleSetSummary approveRuleSet(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody WorkflowDecision request) {
        return progressionService.approveRuleSet(id, request, actor(authentication));
    }

    @GetMapping("/rosters")
    public List<RosterSummary> rosters() {
        return progressionService.rosters();
    }

    @GetMapping("/decisions")
    public List<DecisionSummary> decisions() {
        return progressionService.decisions();
    }

    @PostMapping("/decisions")
    public DecisionSummary calculate(
            Authentication authentication,
            @Valid @RequestBody CalculateDecision request) {
        return progressionService.calculate(request, actor(authentication));
    }

    @PostMapping("/decisions/{id}/{action:review|approve|publish|reject}")
    public DecisionSummary moveDecision(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable String action,
            @Valid @RequestBody WorkflowDecision request) {
        return progressionService.moveDecision(id, action, request, actor(authentication));
    }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}

package zw.ac.uz.emhare.finance.catalogue.api.controller;

import zw.ac.uz.emhare.finance.catalogue.*;
import zw.ac.uz.emhare.finance.catalogue.api.model.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeCatalogueApiModels.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/finance/fee-catalogues")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_finance-officer')")
public class FinanceFeeCatalogueController {
    private final GovernedFinanceFeeCatalogueService service;private final EmhareCurrentUserResolver currentUserResolver;
    public FinanceFeeCatalogueController(GovernedFinanceFeeCatalogueService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}
    @GetMapping public CatalogueRegister register(){return service.register();}
    @PostMapping public CatalogueSummary create(Authentication authentication,@Valid @RequestBody CreateCatalogue request){return service.createCatalogue(request,actor(authentication));}
    @PostMapping("/{id}/{action:activate|retire}") public CatalogueSummary move(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody WorkflowDecision request){return service.moveCatalogue(id,action,request,actor(authentication));}
    @PostMapping("/{catalogueId}/rules") public RuleSummary createRule(Authentication authentication,@PathVariable UUID catalogueId,@Valid @RequestBody CreateRule request){return service.createRule(catalogueId,request,actor(authentication));}
    @PostMapping("/rules/{ruleId}/rate") public RuleSummary rate(@PathVariable UUID ruleId,@RequestParam long expectedVersion){return service.applyRate(ruleId,expectedVersion);}
    @PostMapping("/rules/{ruleId}/{action:approve|retire}") public RuleSummary moveRule(Authentication authentication,@PathVariable UUID ruleId,@PathVariable String action,@Valid @RequestBody WorkflowDecision request){return service.moveRule(ruleId,action,request,actor(authentication));}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}

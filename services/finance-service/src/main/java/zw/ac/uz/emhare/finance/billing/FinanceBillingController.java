package zw.ac.uz.emhare.finance.billing;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.finance.billing.FinanceBillingContracts.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/finance/billing")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_finance-officer')")
public class FinanceBillingController {
    private final GovernedFinanceBillingService service;private final EmhareCurrentUserResolver currentUserResolver;
    public FinanceBillingController(GovernedFinanceBillingService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}
    @GetMapping public BillingRegister register(){return service.register();}
    @PostMapping("/events") public BillingEventSummary create(Authentication authentication,@Valid @RequestBody CreateBillingEvent command){return service.create(command,actor(authentication));}
    @PostMapping("/events/{id}/{action:approve|reject}") public BillingEventSummary decide(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody BillingDecision command){return service.decide(id,"approve".equals(action),command,actor(authentication));}
    @PostMapping("/invoices") public InvoiceSummary post(Authentication authentication,@Valid @RequestBody PostInvoice command){return service.post(command,actor(authentication));}
    @PostMapping("/policies") public BillingPolicySummary createPolicy(Authentication authentication,@Valid @RequestBody CreateBillingPolicy command){return service.createPolicy(command,actor(authentication));}
    @PostMapping("/policies/{id}/{action:activate|retire}") public BillingPolicySummary movePolicy(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody BillingDecision command){return service.movePolicy(id,action,command,actor(authentication));}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}

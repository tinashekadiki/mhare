package zw.ac.uz.emhare.finance.collections.api.controller;

import zw.ac.uz.emhare.finance.collections.*;
import zw.ac.uz.emhare.finance.collections.api.model.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.finance.collections.api.model.FinanceCollectionsApiModels.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/finance/collections")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_finance-officer')")
public class FinanceCollectionsController {
    private final GovernedFinanceCollectionsService service;private final EmhareCurrentUserResolver currentUserResolver;
    public FinanceCollectionsController(GovernedFinanceCollectionsService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}
    @GetMapping public CollectionsRegister register(){return service.register();}
    @GetMapping("/accounts") public java.util.List<StudentAccountSummary> accounts(){return service.accounts();}
    @GetMapping("/accounts/{id}/statement") public StudentAccountStatement statement(@PathVariable UUID id){return service.statement(id);}
    @PostMapping("/exchange-rates") public ExchangeRateSummary createRate(Authentication authentication,@Valid @RequestBody CreateExchangeRate request){return service.createRate(request,actor(authentication));}
    @PostMapping("/exchange-rates/{id}/{action:approve|retire}") public ExchangeRateSummary moveRate(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody ControlledDecision request){return service.moveRate(id,action,request,actor(authentication));}
    @PostMapping("/payments") public PaymentSummary capture(Authentication authentication,@Valid @RequestBody CapturePayment request){return service.capture(request,actor(authentication));}
    @PostMapping("/payments/{id}/apply-rate") public PaymentSummary applyRate(Authentication authentication,@PathVariable UUID id,@RequestParam long expectedVersion){return service.applyRate(id,expectedVersion,actor(authentication));}
    @PostMapping("/payments/{id}/{action:reconcile|reject}") public PaymentSummary decidePayment(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody ControlledDecision request){return service.decidePayment(id,action,request,actor(authentication));}
    @PostMapping("/payments/{id}/resolve-suspense") public PaymentSummary resolveSuspense(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ResolveSuspense request){return service.resolveSuspense(id,request,actor(authentication));}
    @PostMapping("/payments/{id}/allocations") public AllocationSummary allocate(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody AllocatePayment request){return service.allocate(id,request,actor(authentication));}
    @PostMapping("/allocations/{id}/reverse") public AllocationSummary reverseAllocation(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ControlledDecision request){return service.reverseAllocation(id,request,actor(authentication));}
    @PostMapping("/payments/{id}/reverse") public PaymentSummary reversePayment(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ControlledDecision request){return service.reversePayment(id,request,actor(authentication));}
    @PostMapping("/credit-notes") public CreditNoteSummary createCreditNote(Authentication authentication,@Valid @RequestBody CreateCreditNote request){return service.createCreditNote(request,actor(authentication));}
    @PostMapping("/credit-notes/{id}/post") public CreditNoteSummary postCreditNote(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ControlledDecision request){return service.postCreditNote(id,request,actor(authentication));}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}

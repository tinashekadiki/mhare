package zw.ac.uz.emhare.finance.collections;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.finance.collections.FinanceCollectionsContracts.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/finance/collections")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_finance-officer')")
public class FinanceCollectionsController {
    private final GovernedFinanceCollectionsService service;private final EmhareCurrentUserResolver currentUserResolver;
    public FinanceCollectionsController(GovernedFinanceCollectionsService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}
    @GetMapping public CollectionsRegister register(){return service.register();}
    @GetMapping("/accounts") public java.util.List<StudentAccountSummary> accounts(){return service.accounts();}
    @GetMapping("/accounts/{id}/statement") public StudentAccountStatement statement(@PathVariable UUID id){return service.statement(id);}
    @PostMapping("/exchange-rates") public ExchangeRateSummary createRate(Authentication authentication,@Valid @RequestBody CreateExchangeRate command){return service.createRate(command,actor(authentication));}
    @PostMapping("/exchange-rates/{id}/{action:approve|retire}") public ExchangeRateSummary moveRate(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody ControlledDecision command){return service.moveRate(id,action,command,actor(authentication));}
    @PostMapping("/payments") public PaymentSummary capture(Authentication authentication,@Valid @RequestBody CapturePayment command){return service.capture(command,actor(authentication));}
    @PostMapping("/payments/{id}/apply-rate") public PaymentSummary applyRate(Authentication authentication,@PathVariable UUID id,@RequestParam long expectedVersion){return service.applyRate(id,expectedVersion,actor(authentication));}
    @PostMapping("/payments/{id}/{action:reconcile|reject}") public PaymentSummary decidePayment(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody ControlledDecision command){return service.decidePayment(id,action,command,actor(authentication));}
    @PostMapping("/payments/{id}/resolve-suspense") public PaymentSummary resolveSuspense(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ResolveSuspense command){return service.resolveSuspense(id,command,actor(authentication));}
    @PostMapping("/payments/{id}/allocations") public AllocationSummary allocate(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody AllocatePayment command){return service.allocate(id,command,actor(authentication));}
    @PostMapping("/allocations/{id}/reverse") public AllocationSummary reverseAllocation(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ControlledDecision command){return service.reverseAllocation(id,command,actor(authentication));}
    @PostMapping("/payments/{id}/reverse") public PaymentSummary reversePayment(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ControlledDecision command){return service.reversePayment(id,command,actor(authentication));}
    @PostMapping("/credit-notes") public CreditNoteSummary createCreditNote(Authentication authentication,@Valid @RequestBody CreateCreditNote command){return service.createCreditNote(command,actor(authentication));}
    @PostMapping("/credit-notes/{id}/post") public CreditNoteSummary postCreditNote(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ControlledDecision command){return service.postCreditNote(id,command,actor(authentication));}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}

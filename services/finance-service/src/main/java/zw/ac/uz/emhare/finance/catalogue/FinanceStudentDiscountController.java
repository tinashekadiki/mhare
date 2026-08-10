package zw.ac.uz.emhare.finance.catalogue;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.CreateDiscount;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.DiscountDecision;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.DiscountRegister;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.DiscountSummary;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/finance/student-discounts")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_finance-officer')")
public class FinanceStudentDiscountController {
    private final GovernedFinanceStudentDiscountService service;
    private final EmhareCurrentUserResolver currentUserResolver;
    public FinanceStudentDiscountController(GovernedFinanceStudentDiscountService service,
            EmhareCurrentUserResolver currentUserResolver) {
        this.service = service; this.currentUserResolver = currentUserResolver;
    }
    @GetMapping public DiscountRegister register() { return service.register(); }
    @PostMapping public DiscountSummary create(Authentication authentication,
            @Valid @RequestBody CreateDiscount command) { return service.create(command, actor(authentication)); }
    @PostMapping("/{discountId}/{action:activate|retire}")
    public DiscountSummary move(Authentication authentication, @PathVariable("discountId") UUID discountId,
            @PathVariable("action") String action, @Valid @RequestBody DiscountDecision command) {
        return service.move(discountId, action, command, actor(authentication));
    }
    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required.")).auditUserId();
    }
}

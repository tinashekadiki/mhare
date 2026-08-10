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
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.ApplicationFeePricing;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.CreateStructure;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.ResolveStructure;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.StructureDecision;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.StructureRegister;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.StructureSummary;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/finance/fee-structures")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_finance-officer')")
public class FinanceFeeStructureController {
    private final GovernedFinanceFeeStructureService service;
    private final EmhareCurrentUserResolver currentUserResolver;

    public FinanceFeeStructureController(GovernedFinanceFeeStructureService service,
            EmhareCurrentUserResolver currentUserResolver) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public StructureRegister register() {
        return service.register();
    }

    @GetMapping("/{structureId}/pricing")
    @PreAuthorize("isAuthenticated()")
    public ApplicationFeePricing pricing(@PathVariable UUID structureId) {
        return service.pricing(structureId);
    }

    @PostMapping
    public StructureSummary create(Authentication authentication, @Valid @RequestBody CreateStructure command) {
        return service.create(command, actor(authentication));
    }

    @PostMapping("/{structureId}/{action:activate|retire}")
    public StructureSummary move(Authentication authentication, @PathVariable("structureId") UUID structureId,
            @PathVariable("action") String action, @Valid @RequestBody StructureDecision command) {
        return service.move(structureId, action, command, actor(authentication));
    }

    @PostMapping("/resolve")
    public StructureSummary resolve(@Valid @RequestBody ResolveStructure command) {
        return service.resolve(command);
    }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}

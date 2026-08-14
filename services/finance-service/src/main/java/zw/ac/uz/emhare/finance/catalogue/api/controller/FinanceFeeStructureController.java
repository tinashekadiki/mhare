package zw.ac.uz.emhare.finance.catalogue.api.controller;

import zw.ac.uz.emhare.finance.catalogue.*;
import zw.ac.uz.emhare.finance.catalogue.api.model.*;

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
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.ApplicationFeePricing;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.CreateStructure;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.ResolveStructure;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureDecision;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureRegister;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureSummary;

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
    public StructureSummary create(Authentication authentication, @Valid @RequestBody CreateStructure request) {
        return service.create(request, actor(authentication));
    }

    @PostMapping("/{structureId}/{action:activate|retire}")
    public StructureSummary move(Authentication authentication, @PathVariable("structureId") UUID structureId,
            @PathVariable("action") String action, @Valid @RequestBody StructureDecision request) {
        return service.move(structureId, action, request, actor(authentication));
    }

    @PostMapping("/resolve")
    @PreAuthorize("isAuthenticated()")
    public StructureSummary resolve(@Valid @RequestBody ResolveStructure request) {
        return service.resolve(request);
    }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}

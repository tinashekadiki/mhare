package zw.ac.uz.emhare.accommodation.operations.api.controller;

import zw.ac.uz.emhare.accommodation.operations.*;
import zw.ac.uz.emhare.accommodation.operations.api.model.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.accommodation.operations.api.model.AccommodationOperationsApiModels.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/accommodation/operations")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_accommodation-officer')")
public class AccommodationOperationsController {
    private final AccommodationOperationsService service;
    private final EmhareCurrentUserResolver currentUserResolver;

    public AccommodationOperationsController(AccommodationOperationsService service,
            EmhareCurrentUserResolver currentUserResolver) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping public OperationsRegister register() { return service.register(); }
    @PostMapping("/rates") public RateSummary createRate(Authentication authentication, @Valid @RequestBody CreateRate request) { return service.createRate(request, actor(authentication)); }
    @PostMapping("/rates/{id}/transition") public RateSummary transitionRate(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody RateTransition request) { return service.transitionRate(id, request, actor(authentication)); }
    @PostMapping("/applications") public ApplicationSummary submitApplication(@Valid @RequestBody SubmitApplication request) { return service.submitApplication(request); }
    @PostMapping("/applications/{id}/evaluate") public ApplicationSummary evaluateApplication(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody EvaluateApplication request) { return service.evaluateApplication(id, request, actor(authentication)); }
    @PostMapping("/applications/{id}/withdraw") public ApplicationSummary withdrawApplication(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody WithdrawApplication request) { return service.withdrawApplication(id, request, actor(authentication)); }
    @PostMapping("/allocations") public AllocationSummary proposeAllocation(Authentication authentication, @Valid @RequestBody ProposeAllocation request) { return service.proposeAllocation(request, actor(authentication)); }
    @PostMapping("/allocations/{id}/approve") public AllocationSummary approveAllocation(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AllocationAction request) { return service.approveAllocation(id, request, actor(authentication)); }
    @PostMapping("/allocations/{id}/check-in") public AllocationSummary checkIn(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AllocationAction request) { return service.checkIn(id, request, actor(authentication)); }
    @PostMapping("/allocations/{id}/check-out") public AllocationSummary checkOut(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AllocationAction request) { return service.checkOut(id, request, actor(authentication)); }
    @PostMapping("/allocations/{id}/cancel") public AllocationSummary cancelAllocation(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AllocationAction request) { return service.cancelAllocation(id, request, actor(authentication)); }
    @PostMapping("/allocations/{id}/withdraw") public AllocationSummary withdrawAllocation(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AllocationAction request) { return service.withdrawAllocation(id, request, actor(authentication)); }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}

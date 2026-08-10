package zw.ac.uz.emhare.accommodation.setup;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.accommodation.setup.AccommodationSetupContracts.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/accommodation/setup")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_accommodation-officer')")
public class AccommodationSetupController {
    private final AccommodationSetupService service;
    private final EmhareCurrentUserResolver currentUserResolver;

    public AccommodationSetupController(AccommodationSetupService service,
            EmhareCurrentUserResolver currentUserResolver) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping public SetupRegister register() { return service.register(); }
    @PostMapping("/premises") public PremiseSummary createPremise(@Valid @RequestBody CreatePremise request) { return service.createPremise(request); }
    @PutMapping("/premises/{id}") public PremiseSummary updatePremise(@PathVariable UUID id, @Valid @RequestBody UpdatePremise request) { return service.updatePremise(id, request); }
    @PostMapping("/room-types") public RoomTypeSummary createRoomType(@Valid @RequestBody CreateRoomType request) { return service.createRoomType(request); }
    @PutMapping("/room-types/{id}") public RoomTypeSummary updateRoomType(@PathVariable UUID id, @Valid @RequestBody UpdateRoomType request) { return service.updateRoomType(id, request); }
    @PostMapping("/residence-halls") public ResidenceHallSummary createResidenceHall(@Valid @RequestBody CreateResidenceHall request) { return service.createResidenceHall(request); }
    @PutMapping("/residence-halls/{id}") public ResidenceHallSummary updateResidenceHall(@PathVariable UUID id, @Valid @RequestBody UpdateResidenceHall request) { return service.updateResidenceHall(id, request); }
    @PostMapping("/rooms") public RoomSummary createRoom(@Valid @RequestBody CreateRoom request) { return service.createRoom(request); }
    @PutMapping("/rooms/{id}") public RoomSummary updateRoom(@PathVariable UUID id, @Valid @RequestBody UpdateRoom request) { return service.updateRoom(id, request); }
    @PostMapping("/application-periods") public ApplicationPeriodSummary createApplicationPeriod(Authentication authentication, @Valid @RequestBody CreateApplicationPeriod request) { return service.createApplicationPeriod(request, actor(authentication)); }
    @PutMapping("/application-periods/{id}") public ApplicationPeriodSummary updateApplicationPeriod(@PathVariable UUID id, @Valid @RequestBody UpdateApplicationPeriod request) { return service.updateApplicationPeriod(id, request); }
    @PostMapping("/application-periods/{id}/transition") public ApplicationPeriodSummary transitionApplicationPeriod(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody PeriodTransition request) { return service.transitionApplicationPeriod(id, request, actor(authentication)); }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}

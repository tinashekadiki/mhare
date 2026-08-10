package zw.ac.uz.emhare.dining.operations;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.dining.operations.DiningOperationsContracts.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/dining/operations")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_dining-officer')")
public class DiningOperationsController {
    private final DiningOperationsService service; private final EmhareCurrentUserResolver users;
    public DiningOperationsController(DiningOperationsService service,EmhareCurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping public OperationsRegister register(){return service.register();}
    @PostMapping("/assignments") public AssignmentSummary prepareAssignment(Authentication a,@Valid @RequestBody PrepareAssignment c){return service.prepareAssignment(c,actor(a));}
    @PostMapping("/assignments/{id}/{action:activate|suspend|resume|end|cancel}") public AssignmentSummary assignmentAction(Authentication a,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody AssignmentAction c){return service.assignmentAction(id,action,c,actor(a));}
    @PostMapping("/dietary-requirements") public DietarySummary recordDietary(Authentication a,@Valid @RequestBody RecordDietaryRequirement c){return service.recordDietary(c,actor(a));}
    @PostMapping("/dietary-requirements/{id}/resolve") public DietarySummary resolveDietary(Authentication a,@PathVariable UUID id,@Valid @RequestBody ResolveDietaryRequirement c){return service.resolveDietary(id,c,actor(a));}
    @PostMapping("/sessions") public SessionSummary planSession(Authentication a,@Valid @RequestBody PlanMealSession c){return service.planSession(c,actor(a));}
    @PostMapping("/sessions/{id}/{action:open|close}") public SessionSummary sessionAction(Authentication a,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody SessionAction c){return service.sessionAction(id,action,c,actor(a));}
    @PostMapping("/sessions/{id}/reconcile") public SessionSummary reconcileSession(Authentication a,@PathVariable UUID id,@Valid @RequestBody ReconcileSession c){return service.reconcileSession(id,c,actor(a));}
    @PostMapping("/attendance") public AttendanceSummary captureAttendance(Authentication a,@Valid @RequestBody CaptureAttendance c){return service.captureAttendance(c,actor(a));}
    @PostMapping("/attendance/{id}/reverse") public ReversalSummary reverseAttendance(Authentication a,@PathVariable UUID id,@Valid @RequestBody ReverseAttendance c){return service.reverseAttendance(id,c,actor(a));}
    private UUID actor(Authentication a){return users.fromAuthentication(a).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
